package online.yudream.base.plugin.eduroam.bootstrap;

import online.yudream.base.plugin.eduroam.application.cmd.EduroamLoginCmd;
import online.yudream.base.plugin.eduroam.application.dto.EduroamLoginResultDTO;
import online.yudream.base.plugin.eduroam.application.service.EduroamAppService;
import online.yudream.base.plugin.eduroam.application.service.EduroamLoginProvider;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import online.yudream.base.plugin.eduroam.infrastructure.FakeAccountRepository;
import online.yudream.base.plugin.eduroam.infrastructure.FakeAttemptRepository;
import online.yudream.base.plugin.eduroam.infrastructure.FakeLocalUserPort;
import online.yudream.base.plugin.eduroam.infrastructure.FakeProbe;
import online.yudream.base.plugin.eduroam.infrastructure.FakeSettingsRepository;
import online.yudream.base.plugin.eduroam.infrastructure.FakeTicketRepository;
import online.yudream.base.plugin.eduroam.infrastructure.support.AttemptRateLimiter;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginAuthorizeRequest;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginDescriptor;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginExchangeRequest;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部登录提供方测试：宿主只依赖 descriptor / enabled / authorizationUrl / exchange 这四个入口。
 */
class EduroamLoginProviderTest {

    private static final String STATE = "host-state-123";

    private FakeSettingsRepository settingsRepository;
    private EduroamAppService app;
    private EduroamLoginProvider provider;

    @BeforeEach
    void setUp() {
        settingsRepository = new FakeSettingsRepository();
        settingsRepository.save(new EduroamSettings(true, "example.edu.cn", "mail.example.edu.cn",
                EduroamSettings.DEFAULT_ENDPOINT, 5, 20, 10, ""));
        app = new EduroamAppService(new FakeAccountRepository(), new FakeAttemptRepository(),
                new FakeTicketRepository(), settingsRepository,
                new FakeProbe().expectPassword("s3cret"), new FakeLocalUserPort(), new AttemptRateLimiter());
        app.useClock(() -> 1_700_000_000_000L);
        provider = new EduroamLoginProvider(app);
    }

    @Test
    void descriptorMatchesPluginCodeAndAdvertisesSingleType() {
        PluginExternalLoginDescriptor descriptor = provider.descriptor();
        assertEquals("eduroam", descriptor.providerCode());
        assertEquals("Eduroam 认证", descriptor.displayName());
        assertEquals(List.of("eduroam"), descriptor.supportedTypes());
        assertTrue(provider.enabled());
    }

    @Test
    void enabledFollowsChannelSwitch() {
        app.saveSettings(new online.yudream.base.plugin.eduroam.application.cmd.EduroamSettingsSaveCmd(
                false, null, null, null, null, null, null, null));
        assertFalse(provider.enabled());
    }

    @Test
    void authorizationUrlSendsBrowserToCredentialPageWithState() {
        String url = provider.authorizationUrl(new PluginExternalLoginAuthorizeRequest("eduroam", STATE));
        assertTrue(url.startsWith("/eduroam?"), url);
        assertTrue(url.contains("state=" + STATE), url);
        assertTrue(url.contains("provider=eduroam"), url);
        assertTrue(url.contains("type=eduroam"), url);

        assertEquals("登录请求缺少 state",
                assertThrows(IllegalArgumentException.class,
                        () -> provider.authorizationUrl(new PluginExternalLoginAuthorizeRequest("eduroam", " ")))
                        .getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> provider.authorizationUrl(new PluginExternalLoginAuthorizeRequest("cas", STATE)));
    }

    @Test
    void exchangeTurnsTicketIntoExternalIdentity() {
        EduroamLoginResultDTO login = app.authenticate(
                new EduroamLoginCmd("2023123456", "s3cret", STATE), "10.0.0.8");
        assertTrue(login.success());

        PluginExternalLoginIdentity identity = provider.exchange(
                new PluginExternalLoginExchangeRequest("eduroam", login.ticket(), STATE));

        assertEquals("2023123456@example.edu.cn", identity.socialUid());
        assertEquals("2023123456", identity.nickname());

        // 票据已核销，重复回调必须失败（fail-closed）
        assertEquals("登录票据无效或已过期，请返回登录页重试",
                assertThrows(IllegalArgumentException.class, () -> provider.exchange(
                        new PluginExternalLoginExchangeRequest("eduroam", login.ticket(), STATE))).getMessage());
    }

    @Test
    void exchangeRejectsMissingTicketAndWrongState() {
        assertEquals("登录回调缺少票据",
                assertThrows(IllegalArgumentException.class,
                        () -> provider.exchange(new PluginExternalLoginExchangeRequest("eduroam", " ", STATE)))
                        .getMessage());

        EduroamLoginResultDTO login = app.authenticate(
                new EduroamLoginCmd("2023123456", "s3cret", STATE), "10.0.0.8");
        assertThrows(IllegalArgumentException.class, () -> provider.exchange(
                new PluginExternalLoginExchangeRequest("eduroam", login.ticket(), "another-state")));
    }
}
