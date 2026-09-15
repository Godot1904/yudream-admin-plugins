package online.yudream.base.plugin.eduroam.application.service;

import online.yudream.base.plugin.eduroam.application.cmd.EduroamLoginCmd;
import online.yudream.base.plugin.eduroam.application.cmd.EduroamSettingsSaveCmd;
import online.yudream.base.plugin.eduroam.application.dto.EduroamAccountDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamAttemptDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamLoginResultDTO;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamLoginTicket;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import online.yudream.base.plugin.eduroam.domain.enumerate.EduroamFailureReason;
import online.yudream.base.plugin.eduroam.infrastructure.FakeAccountRepository;
import online.yudream.base.plugin.eduroam.infrastructure.FakeAttemptRepository;
import online.yudream.base.plugin.eduroam.infrastructure.FakeLocalUserPort;
import online.yudream.base.plugin.eduroam.infrastructure.FakeProbe;
import online.yudream.base.plugin.eduroam.infrastructure.FakeSettingsRepository;
import online.yudream.base.plugin.eduroam.infrastructure.FakeTicketRepository;
import online.yudream.base.plugin.eduroam.infrastructure.support.AttemptRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三方登录主流程测试。
 *
 * <p>重点覆盖最容易出错的地方：票据一次性且与 state 绑定、封禁账号连上游都不探测、
 * 失败才限流、密码不落库，以及管理端封禁/解封生命周期。
 */
class EduroamAppServiceTest {

    private static final String IP = "10.0.0.8";
    private static final String STATE = "host-issued-state";

    private FakeAccountRepository accounts;
    private FakeAttemptRepository attempts;
    private FakeTicketRepository tickets;
    private FakeSettingsRepository settingsRepository;
    private FakeLocalUserPort localUsers;
    private FakeProbe probe;
    private EduroamAppService app;
    private long now = 1_700_000_000_000L;

    @BeforeEach
    void setUp() {
        accounts = new FakeAccountRepository();
        attempts = new FakeAttemptRepository();
        tickets = new FakeTicketRepository();
        settingsRepository = new FakeSettingsRepository();
        localUsers = new FakeLocalUserPort();
        probe = new FakeProbe().expectPassword("s3cret");
        app = new EduroamAppService(accounts, attempts, tickets, settingsRepository, probe, localUsers,
                new AttemptRateLimiter());
        app.useClock(() -> now);
        settingsRepository.save(new EduroamSettings(true, "example.edu.cn", "mail.example.edu.cn",
                EduroamSettings.DEFAULT_ENDPOINT, 5, 20, 3, ""));
    }

    private EduroamLoginResultDTO login(String password) {
        return app.authenticate(new EduroamLoginCmd("2023123456", password, STATE), IP);
    }

    @Test
    void successfulLoginReturnsOneTimeTicketAndRecordsAccount() {
        EduroamLoginResultDTO result = login("s3cret");

        assertTrue(result.success());
        assertEquals("2023123456@mail.example.edu.cn", result.email());
        assertEquals("2023123456@example.edu.cn", result.identity());
        assertEquals("2023123456", result.account());
        assertFalse(result.ticket().isBlank());
        assertEquals(now + EduroamAppService.TICKET_TTL_MILLIS, result.expiresAt());
        // 上游收到的是补全认证域后的账号
        assertEquals(List.of("2023123456@example.edu.cn"), probe.identities());

        EduroamAccountDTO account = app.listAccounts(null, null, 1, 10).get(0);
        assertEquals("2023123456@mail.example.edu.cn", account.email());
        assertEquals("正常", account.statusLabel());
        assertEquals(1, account.loginCount());
        assertEquals(IP, account.lastLoginIp());
        assertEquals(now, account.lastLoginAt());
    }

    @Test
    void ticketIsSingleUseAndBoundToStateAndType() {
        EduroamLoginResultDTO result = login("s3cret");
        String ticketId = result.ticket();

        EduroamLoginTicket consumed = app.consumeTicket(ticketId, STATE, EduroamLoginProvider.PROVIDER_TYPE);
        assertEquals("2023123456@example.edu.cn", consumed.identity());
        assertEquals("2023123456@mail.example.edu.cn", consumed.email());

        // 同一个票据不能再用第二次
        assertEquals("登录票据无效或已过期，请返回登录页重试",
                assertThrows(IllegalArgumentException.class,
                        () -> app.consumeTicket(ticketId, STATE, EduroamLoginProvider.PROVIDER_TYPE)).getMessage());
    }

    @Test
    void ticketRejectsMismatchedStateAndTypeAndExpiry() {
        String first = login("s3cret").ticket();
        assertEquals("登录状态不匹配，请返回登录页重试",
                assertThrows(IllegalArgumentException.class,
                        () -> app.consumeTicket(first, "other-state", EduroamLoginProvider.PROVIDER_TYPE))
                        .getMessage());

        String second = login("s3cret").ticket();
        assertEquals("登录方式不匹配，请返回登录页重试",
                assertThrows(IllegalArgumentException.class,
                        () -> app.consumeTicket(second, STATE, "cas")).getMessage());

        String third = login("s3cret").ticket();
        now += EduroamAppService.TICKET_TTL_MILLIS + 1L;
        assertEquals("登录票据已过期，请返回登录页重试",
                assertThrows(IllegalArgumentException.class,
                        () -> app.consumeTicket(third, STATE, EduroamLoginProvider.PROVIDER_TYPE)).getMessage());
    }

    @Test
    void repeatedLoginUpdatesStatsInsteadOfDuplicating() {
        login("s3cret");
        now += 60_000L;
        login("s3cret");

        assertEquals(1, accounts.count());
        EduroamAccountDTO account = app.accountDetail("2023123456@mail.example.edu.cn");
        assertEquals(2, account.loginCount());
        assertEquals(now, account.lastLoginAt());
    }

    @Test
    void failureReturnsReasonAndIsAuditedWithoutTicketOrAccount() {
        probe.failWith(EduroamFailureReason.TIMEOUT);
        EduroamLoginResultDTO result = login("wrong");

        assertFalse(result.success());
        assertEquals(EduroamFailureReason.TIMEOUT.code(), result.reasonCode());
        assertEquals(EduroamFailureReason.TIMEOUT.message(), result.message());
        assertTrue(result.ticket().isBlank());
        assertEquals(0, accounts.count());
        assertEquals(0, tickets.count());

        List<EduroamAttemptDTO> audit = app.listAttempts(null, null, 1, 10);
        assertEquals(1, audit.size());
        assertFalse(audit.get(0).success());
        assertEquals(EduroamFailureReason.TIMEOUT.code(), audit.get(0).reasonCode());
    }

    @Test
    void blockedAccountIsRefusedWithoutProbingUpstream() {
        login("s3cret");
        app.blockAccount("2023123456@mail.example.edu.cn", "9001", "非本校成员");

        int probesBefore = probe.identities().size();
        EduroamLoginResultDTO result = login("s3cret");

        assertFalse(result.success());
        assertEquals("BLOCKED", result.reasonCode());
        assertEquals(probesBefore, probe.identities().size(), "被禁止的账号不应再探测上游");
        assertTrue(result.ticket().isBlank());

        EduroamAccountDTO blocked = app.accountDetail("2023123456@mail.example.edu.cn");
        assertEquals("已禁止", blocked.statusLabel());
        assertEquals("9001", blocked.blockedByUserId());
        assertEquals("非本校成员", blocked.blockReason());
        assertEquals(now, blocked.blockedAt());

        // 解封后可以正常登录
        app.unblockAccount("2023123456@mail.example.edu.cn", "9001");
        assertTrue(app.accountDetail("2023123456@mail.example.edu.cn").statusLabel().equals("正常"));
        assertTrue(login("s3cret").success());

        assertEquals("该账号当前未被禁止",
                assertThrows(IllegalArgumentException.class,
                        () -> app.unblockAccount("2023123456@mail.example.edu.cn", "9001")).getMessage());
        app.blockAccount("2023123456@mail.example.edu.cn", "9001", "");
        assertEquals("该账号已经是禁止登录状态",
                assertThrows(IllegalArgumentException.class,
                        () -> app.blockAccount("2023123456@mail.example.edu.cn", "9001", "")).getMessage());
    }

    /** 只有失败才计入限流：输错两次后仍应能用正确密码登录。 */
    @Test
    void rateLimitCountsOnlyFailuresAndResetsAfterSuccess() {
        assertFalse(login("wrong").success());
        assertFalse(login("wrong").success());
        assertTrue(login("s3cret").success(), "失败两次后正确密码必须还能登录");

        for (int index = 0; index < 3; index++) {
            assertFalse(login("wrong").success());
        }
        assertEquals("失败次数过多（每小时最多 3 次），请稍后再试",
                assertThrows(IllegalArgumentException.class, () -> login("s3cret")).getMessage());
    }

    @Test
    void invalidAccountIsRejectedAndAuditedWithoutProbing() {
        assertEquals("Eduroam 账号不能包含空格",
                assertThrows(IllegalArgumentException.class,
                        () -> app.authenticate(new EduroamLoginCmd("ali ce", "s3cret", STATE), IP)).getMessage());
        assertEquals(0, probe.identities().size());

        List<EduroamAttemptDTO> audit = app.listAttempts(null, null, 1, 10);
        assertEquals(1, audit.size());
        assertEquals("INVALID_ACCOUNT", audit.get(0).reasonCode());
        assertEquals("账号格式不正确", audit.get(0).reasonLabel());
    }

    /**
     * 拿不到客户端 IP 时按账号限流：一个人的失败不能把全站登录一起挡掉。
     */
    @Test
    void withoutClientIpTheRateLimitFallsBackToPerAccount() {
        for (int index = 0; index < 3; index++) {
            assertFalse(app.authenticate(new EduroamLoginCmd("userA", "wrong", STATE), "").success());
        }
        assertEquals("失败次数过多（每小时最多 3 次），请稍后再试",
                assertThrows(IllegalArgumentException.class,
                        () -> app.authenticate(new EduroamLoginCmd("userA", "s3cret", STATE), "")).getMessage());
        // 另一个账号不受 userA 的失败影响
        assertTrue(app.authenticate(new EduroamLoginCmd("userB", "s3cret", STATE), "").success());
    }

    @Test
    void loginRequiresStateAndExistingChannel() {
        assertEquals("登录请求缺少 state，请从登录页重新进入",
                assertThrows(IllegalArgumentException.class,
                        () -> app.authenticate(new EduroamLoginCmd("2023123456", "s3cret", ""), IP)).getMessage());

        app.saveSettings(new EduroamSettingsSaveCmd(false, null, null, null, null, null, null, null));
        assertEquals("Eduroam 登录当前未开放，请联系管理员",
                assertThrows(IllegalArgumentException.class,
                        () -> app.authenticate(new EduroamLoginCmd("2023123456", "s3cret", STATE), IP)).getMessage());
    }

    @Test
    void passwordsNeverReachAuditRecords() {
        login("s3cret");
        login("wrong-password-value");

        for (EduroamAttemptDTO attempt : app.listAttempts(null, null, 1, 20)) {
            assertFalse(attempt.detail().contains("s3cret"));
            assertFalse(attempt.detail().contains("wrong-password-value"));
            assertFalse(attempt.message().contains("s3cret"));
        }
        for (EduroamAccountDTO account : app.listAccounts(null, null, 1, 10)) {
            assertFalse(account.email().contains("s3cret"));
        }
    }

    @Test
    void adminQueriesFilterAndPage() {
        for (int index = 0; index < 12; index++) {
            now += 1_000L;
            app.authenticate(new EduroamLoginCmd("user" + index, "s3cret", STATE), IP);
        }
        assertEquals(12, app.countAccounts(null, null));
        assertEquals(10, app.listAccounts(null, null, 1, 10).size());
        assertEquals(2, app.listAccounts(null, null, 2, 10).size());
        assertEquals(12, app.listAccounts(null, null, 1, 1000).size(), "size 超上限时被收敛到 100");
        assertEquals(1, app.listAccounts(null, "user7", 1, 10).size());

        app.blockAccount("user3@mail.example.edu.cn", "9001", "");
        assertEquals(1, app.countAccounts("BLOCKED", null));
        assertEquals(11, app.countAccounts("ACTIVE", null));

        assertEquals(12, app.countAttempts(null, null));
        assertEquals(12, app.countAttempts(null, true));
        assertEquals(0, app.countAttempts(null, false));
        assertEquals(1, app.countAttempts("user7", null));
    }

    @Test
    void deletingAccountRemovesRecordAndRejectsUnknownId() {
        login("s3cret");
        app.deleteAccount("2023123456@mail.example.edu.cn");
        assertEquals(0, accounts.count());
        assertEquals("账号记录不存在",
                assertThrows(IllegalArgumentException.class, () -> app.deleteAccount("missing")).getMessage());
    }

    /**
     * 「本站邮箱域」的实际用途：把学号映射成本站邮箱，再据此在台账里标出该邮箱是否已有本地账号。
     */
    @Test
    void ledgerShowsWhetherTheMappedEmailAlreadyHasALocalAccount() {
        login("s3cret");
        EduroamAccountDTO unregistered = app.accountDetail("2023123456@mail.example.edu.cn");
        assertEquals("2023123456@mail.example.edu.cn", unregistered.email());
        assertEquals("", unregistered.localUserId(), "站内还没注册时留空");

        localUsers.register("2023123456@mail.example.edu.cn", "357806992028471296", "2023123456", "小明");
        EduroamAccountDTO registered = app.accountDetail("2023123456@mail.example.edu.cn");
        assertEquals("357806992028471296", registered.localUserId());
        assertEquals("2023123456", registered.localUsername());
        assertEquals("小明", registered.localNickname());

        // 列表与详情口径一致
        EduroamAccountDTO listed = app.listAccounts(null, null, 1, 10).get(0);
        assertEquals(registered.localUserId(), listed.localUserId());
        assertEquals(registered.localNickname(), listed.localNickname());
    }

    /** 本地账号查询只影响展示：查不到、或查询本身抛错，都不该影响登录与封禁判定。 */
    @Test
    void localAccountLookupNeverAffectsLoginOutcome() {
        localUsers.register("other@mail.example.edu.cn", "1", "other", "别人");
        assertTrue(login("s3cret").success());
        app.blockAccount("2023123456@mail.example.edu.cn", "9001", "测试");
        assertEquals("BLOCKED", login("s3cret").reasonCode());
    }

    @Test
    void settingsAreCachedAndSaveKeepsUnspecifiedFields() {
        assertEquals("example.edu.cn", app.settings().eduDomain());
        app.saveSettings(new EduroamSettingsSaveCmd(null, "@Other.EDU.CN", null, "not-a-url", 0, 999, 0, null));

        EduroamSettings saved = app.settings();
        assertEquals("other.edu.cn", saved.eduDomain());
        assertTrue(saved.enabled(), "未指定 enabled 时保持原值");
        assertEquals("mail.example.edu.cn", saved.storeDomain(), "未指定的字段保持原值");
        assertEquals(EduroamSettings.DEFAULT_ENDPOINT, saved.verifyEndpoint(), "非法地址回落到默认值");
        assertEquals(5, saved.connectTimeoutSeconds(), "非正数回落默认值");
        assertEquals(120, saved.requestTimeoutSeconds(), "过大的请求超时被收敛");
        assertEquals(10, saved.maxAttemptsPerHour(), "非正数回落默认值");
    }

    @Test
    void expiredTicketsArePurgedOnNextLogin() {
        login("s3cret");
        assertEquals(1, tickets.count());
        now += EduroamAppService.TICKET_TTL_MILLIS + 1L;
        login("s3cret");
        assertEquals(1, tickets.count(), "过期票据应在下次登录时被清理，只剩刚签发的那张");
    }

    @Test
    void seedDefaultsWritesDocumentOnlyOnce() {
        FakeSettingsRepository empty = new FakeSettingsRepository();
        EduroamAppService fresh = new EduroamAppService(accounts, attempts, tickets, empty, probe, localUsers,
                new AttemptRateLimiter());
        assertTrue(empty.find().isEmpty());
        fresh.seedDefaults();
        assertTrue(empty.find().isPresent());
        fresh.seedDefaults();
        assertTrue(empty.find().isPresent());
    }
}
