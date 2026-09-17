package online.yudream.base.plugin.tarusso.domain.aggregate;

import online.yudream.base.plugin.tarusso.bootstrap.TaruSsoPlugin;
import online.yudream.base.plugin.tarusso.domain.enumerate.SsoProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoSettingsTest {

    @Test
    void casReadyWhenCallbackPresent() {
        SsoSettings settings = new SsoSettings(
                true, SsoProtocol.CAS, "塔大", "i-ri:school-line",
                "https://auth.taru.edu.cn/", "/authserver/login", "/cas/p3/serviceValidate",
                SsoSettings.DEFAULT_OIDC_ISSUER, SsoSettings.DEFAULT_OIDC_AUTHORIZE_PATH, SsoSettings.DEFAULT_OIDC_TOKEN_PATH,
                SsoSettings.DEFAULT_OIDC_USERINFO_PATH, SsoSettings.DEFAULT_OIDC_JWKS_PATH, SsoSettings.DEFAULT_OIDC_REGISTER_PATH,
                "", false, SsoSettings.DEFAULT_SCOPES, "https://site.example/api/external-login/callback",
                SsoSettings.STATE_MODE_QUERY
        );
        assertTrue(settings.ready());
        assertTrue(settings.loginEnabled());
        assertEquals("https://auth.taru.edu.cn", settings.casBaseUrl());
        assertEquals("https://site.example/api/external-login/callback?state=abc", settings.casServiceUrl("abc"));
    }

    @Test
    void oidcNotReadyWithoutSecret() {
        SsoSettings settings = new SsoSettings(
                true, SsoProtocol.OIDC, SsoSettings.DEFAULT_DISPLAY_NAME, SsoSettings.DEFAULT_ICON,
                SsoSettings.DEFAULT_CAS_BASE_URL, SsoSettings.DEFAULT_LOGIN_PATH, SsoSettings.DEFAULT_VALIDATE_PATH,
                SsoSettings.DEFAULT_OIDC_ISSUER, SsoSettings.DEFAULT_OIDC_AUTHORIZE_PATH, SsoSettings.DEFAULT_OIDC_TOKEN_PATH,
                SsoSettings.DEFAULT_OIDC_USERINFO_PATH, SsoSettings.DEFAULT_OIDC_JWKS_PATH, SsoSettings.DEFAULT_OIDC_REGISTER_PATH,
                "client", false, SsoSettings.DEFAULT_SCOPES, "https://site.example/callback",
                SsoSettings.STATE_MODE_QUERY
        );
        assertFalse(settings.ready());
        assertTrue(settings.withClientSecretConfigured(true).ready());
    }

    @Test
    void appendsStateWhenCallbackAlreadyHasQuery() {
        SsoSettings settings = SsoSettings.defaults();
        SsoSettings withCallback = new SsoSettings(
                false, SsoProtocol.CAS, settings.displayName(), settings.icon(), settings.casBaseUrl(),
                settings.loginPath(), settings.validatePath(), settings.oidcIssuer(), settings.oidcAuthorizePath(),
                settings.oidcTokenPath(), settings.oidcUserinfoPath(), settings.oidcJwksPath(), settings.oidcRegisterPath(),
                "", false, settings.scopes(), "https://site.example/callback?from=login",
                SsoSettings.STATE_MODE_QUERY
        );
        assertEquals("https://site.example/callback?from=login&state=s1", withCallback.casServiceUrl("s1"));
        assertFalse(withCallback.relayStateMode());
    }

    @Test
    void stateModeDefaultsToQueryAndUnknownValueFallsBack() {
        assertEquals(SsoSettings.STATE_MODE_QUERY, SsoSettings.defaults().casStateMode());
        assertFalse(SsoSettings.defaults().relayStateMode());
        assertFalse(SsoSettings.defaults().withCasStateMode("nonsense").relayStateMode());
        assertTrue(SsoSettings.defaults().withCasStateMode("RELAY").relayStateMode());
    }

    @Test
    void relayServiceIsFixedAndCarriesNoQueryString() {
        SsoSettings settings = casSettings("https://hall.mc.taru.xj.cn/api/external-login/callback")
                .withCasStateMode(SsoSettings.STATE_MODE_RELAY);

        assertTrue(settings.relayReady());
        assertEquals("https://hall.mc.taru.xj.cn", settings.siteBaseUrl());
        assertEquals("https://hall.mc.taru.xj.cn" + SsoSettings.RELAY_PATH, settings.relayServiceUrl());
        // 兜底的关键：跳给 IdP 的 service 里不能出现任何查询串。
        assertFalse(settings.relayServiceUrl().contains("?"));
        assertFalse(settings.relayServiceUrl().contains("state"));
    }

    @Test
    void relayForwardUrlCarriesHostStateAndTicket() {
        SsoSettings settings = casSettings("https://hall.mc.taru.xj.cn/api/external-login/callback")
                .withCasStateMode(SsoSettings.STATE_MODE_RELAY);

        String forward = settings.relayForwardUrl("cas", "cas", "xQWwDAwBBzS", "ST-1234-abc");

        assertEquals("https://hall.mc.taru.xj.cn/api/external-login/callback"
                + "?provider=cas&type=cas&state=xQWwDAwBBzS&ticket=ST-1234-abc", forward);
    }

    @Test
    void relayForwardUrlFromProviderSpecificCallbackAlsoWorks() {
        // 管理员把回调填成供应商专用端点时，兜底中转仍应转发到宿主的通用回调。
        SsoSettings settings = casSettings("https://site.example/api/external-login/cas/cas/callback")
                .withCasStateMode(SsoSettings.STATE_MODE_RELAY);

        assertEquals("https://site.example/api/external-login/callback?provider=cas&type=cas&state=s1&ticket=ST-1",
                settings.relayForwardUrl("cas", "cas", "s1", "ST-1"));
    }

    @Test
    void relayNotReadyWhenCallbackCannotBeRewritten() {
        SsoSettings settings = casSettings("https://site.example/login/callback")
                .withCasStateMode(SsoSettings.STATE_MODE_RELAY);

        assertFalse(settings.relayReady());
        assertEquals("", settings.relayServiceUrl());
        assertEquals("", settings.relayForwardUrl("cas", "cas", "s1", "ST-1"));
    }

    @Test
    void withCasStateModeKeepsEveryOtherField() {
        SsoSettings original = casSettings("https://site.example/api/external-login/callback");
        SsoSettings relay = original.withCasStateMode(SsoSettings.STATE_MODE_RELAY);

        assertEquals(original.enabled(), relay.enabled());
        assertEquals(original.protocol(), relay.protocol());
        assertEquals(original.displayName(), relay.displayName());
        assertEquals(original.casBaseUrl(), relay.casBaseUrl());
        assertEquals(original.loginPath(), relay.loginPath());
        assertEquals(original.validatePath(), relay.validatePath());
        assertEquals(original.scopes(), relay.scopes());
        assertEquals(original.callbackUrl(), relay.callbackUrl());
        assertEquals(SsoSettings.STATE_MODE_RELAY, relay.casStateMode());
    }

    @Test
    void relayPathMatchesPluginCode() {
        // 中转端点必须挂在插件自己的前缀下，否则浏览器拿到的地址 404。
        assertTrue(SsoSettings.RELAY_PATH.startsWith("/api/plugins/" + TaruSsoPlugin.CODE + "/"),
                "RELAY_PATH 必须位于插件 code 前缀下：" + SsoSettings.RELAY_PATH);
    }

    private static SsoSettings casSettings(String callbackUrl) {
        SsoSettings defaults = SsoSettings.defaults();
        return new SsoSettings(
                true, SsoProtocol.CAS, defaults.displayName(), defaults.icon(), defaults.casBaseUrl(),
                defaults.loginPath(), defaults.validatePath(), defaults.oidcIssuer(), defaults.oidcAuthorizePath(),
                defaults.oidcTokenPath(), defaults.oidcUserinfoPath(), defaults.oidcJwksPath(), defaults.oidcRegisterPath(),
                "", false, defaults.scopes(), callbackUrl, SsoSettings.STATE_MODE_QUERY
        );
    }
}
