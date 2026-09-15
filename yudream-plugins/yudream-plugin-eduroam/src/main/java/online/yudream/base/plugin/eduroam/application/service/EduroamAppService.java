package online.yudream.base.plugin.eduroam.application.service;

import online.yudream.base.plugin.eduroam.application.assembler.EduroamAppAssembler;
import online.yudream.base.plugin.eduroam.application.cmd.EduroamLoginCmd;
import online.yudream.base.plugin.eduroam.application.cmd.EduroamSettingsSaveCmd;
import online.yudream.base.plugin.eduroam.application.dto.EduroamAccountDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamAttemptDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamLoginResultDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamPublicConfigDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamSettingsDTO;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamAccount;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamAttempt;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamLoginTicket;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamAccountRepository;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamAttemptRepository;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamLoginTicketRepository;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamSettingsRepository;
import online.yudream.base.plugin.eduroam.domain.valobj.EduroamIdentity;
import online.yudream.base.plugin.eduroam.domain.valobj.EduroamProbeOutcome;
import online.yudream.base.plugin.eduroam.infrastructure.support.AttemptRateLimiter;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Eduroam 第三方登录的用例编排。
 *
 * <p>两次交互构成一次外部登录：凭据页调用 {@link #authenticate} 校验校园网账号密码并拿到一次性票据，
 * 浏览器随后回宿主回调端点，宿主用 {@link #consumeTicket} 把票据换成外部身份并签发会话。
 *
 * <p>四条边界写死在这里：
 * <ul>
 *   <li><b>不存密码</b>：密码只在 {@link EduroamProbePort#probe} 期间存在；</li>
 *   <li><b>票据一次性</b>：核销即删除，且必须与宿主签发的 state 对上，重放无效；</li>
 *   <li><b>禁止即拒绝</b>：被管理员禁止的账号连上游都不探测，避免拿封禁账号继续试探；</li>
 *   <li><b>失败才限流</b>：按客户端 IP 统计失败次数，正常用户输错一两次不会被挡。</li>
 * </ul>
 */
public class EduroamAppService {

    /** 失败限流窗口：一小时。 */
    public static final long RATE_WINDOW_MILLIS = 60L * 60L * 1000L;
    /** 登录交接票据有效期：5 分钟，够浏览器完成一次跳转。 */
    public static final long TICKET_TTL_MILLIS = 5L * 60L * 1000L;

    private static final int MAX_PAGE_SIZE = 100;
    private static final String REASON_SUCCESS = "SUCCESS";
    private static final String REASON_RATE_LIMITED = "RATE_LIMITED";
    private static final String REASON_INVALID_ACCOUNT = "INVALID_ACCOUNT";
    private static final String REASON_BLOCKED = "BLOCKED";

    private final EduroamAccountRepository accounts;
    private final EduroamAttemptRepository attempts;
    private final EduroamLoginTicketRepository tickets;
    private final EduroamSettingsRepository settingsRepository;
    private final EduroamProbePort probe;
    private final EduroamLocalUserPort localUsers;
    private final AttemptRateLimiter rateLimiter;
    private final EduroamAppAssembler assembler = new EduroamAppAssembler();
    /** 宿主会高频调用 enabled()/descriptor()，配置必须走内存缓存。 */
    private final AtomicReference<EduroamSettings> settingsCache = new AtomicReference<>();
    private final SecureRandom random = new SecureRandom();
    private LongSupplier clock = System::currentTimeMillis;

    public EduroamAppService(EduroamAccountRepository accounts,
                             EduroamAttemptRepository attempts,
                             EduroamLoginTicketRepository tickets,
                             EduroamSettingsRepository settingsRepository,
                             EduroamProbePort probe,
                             EduroamLocalUserPort localUsers,
                             AttemptRateLimiter rateLimiter) {
        this.accounts = accounts;
        this.attempts = attempts;
        this.tickets = tickets;
        this.settingsRepository = settingsRepository;
        this.probe = probe;
        this.localUsers = localUsers;
        this.rateLimiter = rateLimiter;
    }

    /** 测试用：替换时钟，让限流、票据有效期与时间戳可预期。 */
    public void useClock(LongSupplier clock) {
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    // ------------------------------------------------------------------ 配置

    /** 首次启用把默认配置落库，管理端一进页面就有可编辑的值。 */
    public void seedDefaults() {
        if (settingsRepository.find().isEmpty()) {
            settingsCache.set(settingsRepository.save(EduroamSettings.defaults()));
        }
    }

    /** 供登录提供方高频读取：先缓存，未命中才读库。 */
    public EduroamSettings settings() {
        EduroamSettings cached = settingsCache.get();
        if (cached != null) {
            return cached;
        }
        EduroamSettings loaded = settingsRepository.get();
        settingsCache.set(loaded);
        return loaded;
    }

    public EduroamSettingsDTO settingsView() {
        return assembler.toSettingsDTO(settings());
    }

    public EduroamSettingsDTO saveSettings(EduroamSettingsSaveCmd cmd) {
        EduroamSettings current = settings();
        EduroamSettingsSaveCmd safe = cmd == null
                ? new EduroamSettingsSaveCmd(null, null, null, null, null, null, null, null)
                : cmd;
        EduroamSettings next = new EduroamSettings(
                safe.enabled() == null ? current.enabled() : safe.enabled(),
                safe.eduDomain() == null ? current.eduDomain() : safe.eduDomain(),
                safe.storeDomain() == null ? current.storeDomain() : safe.storeDomain(),
                safe.verifyEndpoint() == null ? current.verifyEndpoint() : safe.verifyEndpoint(),
                safe.connectTimeoutSeconds() == null ? current.connectTimeoutSeconds() : safe.connectTimeoutSeconds(),
                safe.requestTimeoutSeconds() == null ? current.requestTimeoutSeconds() : safe.requestTimeoutSeconds(),
                safe.maxAttemptsPerHour() == null ? current.maxAttemptsPerHour() : safe.maxAttemptsPerHour(),
                safe.tutorialMarkdown() == null ? current.tutorialMarkdown() : safe.tutorialMarkdown()
        );
        if (next.enabled() && next.verifyEndpoint().isBlank()) {
            throw new IllegalArgumentException("开启登录通道前请先填写 Eduroam 认证服务地址");
        }
        return assembler.toSettingsDTO(settingsCache.updateAndGet(ignored -> settingsRepository.save(next)));
    }

    public EduroamPublicConfigDTO publicConfig() {
        return assembler.toPublicConfigDTO(settings());
    }

    // ------------------------------------------------------------------ 外部登录

    /**
     * 校验校园网凭据并签发登录交接票据。
     *
     * @param clientIp 调用方 IP，仅用于限流与审计，不参与业务判定
     */
    public EduroamLoginResultDTO authenticate(EduroamLoginCmd cmd, String clientIp) {
        EduroamSettings settings = settings();
        String account = cmd == null || cmd.account() == null ? "" : cmd.account().trim();
        String password = cmd == null || cmd.password() == null ? "" : cmd.password();
        if (!settings.enabled()) {
            throw new IllegalArgumentException("Eduroam 登录当前未开放，请联系管理员");
        }
        long now = clock.getAsLong();
        String rateKey = rateKey(clientIp, account);
        if (rateLimiter.blocked(rateKey, settings.maxAttemptsPerHour(), RATE_WINDOW_MILLIS, now)) {
            recordAttempt("", "", account, "", false, REASON_RATE_LIMITED, "失败次数过多，已被限流", "", clientIp, 0L, now);
            throw new IllegalArgumentException(
                    "失败次数过多（每小时最多 " + settings.maxAttemptsPerHour() + " 次），请稍后再试");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("请填写 Eduroam 密码");
        }

        EduroamIdentity identity;
        try {
            identity = EduroamIdentity.resolve(account, settings);
        } catch (IllegalArgumentException failure) {
            recordAttempt("", "", account, "", false, REASON_INVALID_ACCOUNT, failure.getMessage(), "", clientIp, 0L, now);
            throw failure;
        }

        // 被禁止的账号不再探测上游：封禁是管理员的决定，不能被「再输一次正确密码」试探。
        EduroamAccount existing = accounts.findByEmail(identity.email()).orElse(null);
        if (existing != null && !existing.active()) {
            recordAttempt(identity.email(), identity.identity(), identity.account(), identity.domain(), false,
                    REASON_BLOCKED, "该账号已被管理员禁止登录", existing.blockReason(), clientIp, 0L, now);
            return assembler.toFailureDTO(identity.email(), identity.identity(), identity.account(), REASON_BLOCKED,
                    "该 Eduroam 账号已被管理员禁止登录，请联系管理员");
        }

        EduroamProbeOutcome outcome = probe.probe(identity.identity(), password, settings);
        recordAttempt(identity.email(), identity.identity(), identity.account(), identity.domain(),
                outcome.success(), outcome.reasonCode(), outcome.success() ? "" : outcome.message(),
                outcome.detail(), clientIp, outcome.latencyMs(), now);
        if (!outcome.success()) {
            rateLimiter.record(rateKey, now);
            return assembler.toFailureDTO(identity.email(), identity.identity(), identity.account(),
                    outcome.reasonCode(), outcome.message());
        }

        rateLimiter.reset(rateKey);
        EduroamAccount saved = existing == null
                ? EduroamAccount.firstLogin(identity.email(), identity.identity(), identity.account(),
                        identity.domain(), clientIp, now)
                : existing.recordLogin(identity.identity(), identity.account(), clientIp, now);
        accounts.save(saved);

        String state = cmd == null ? "" : (cmd.state() == null ? "" : cmd.state().trim());
        if (state.isEmpty()) {
            throw new IllegalArgumentException("登录请求缺少 state，请从登录页重新进入");
        }
        tickets.purgeExpired(now);
        EduroamLoginTicket ticket = tickets.save(new EduroamLoginTicket(
                newTicketId(), saved.email(), saved.identity(), saved.account(), state,
                EduroamLoginProvider.PROVIDER_TYPE, now + TICKET_TTL_MILLIS, now));
        return assembler.toLoginResultDTO(ticket);
    }

    /**
     * 宿主回调时用票据换取外部身份。票据一次性、与 state 绑定。
     */
    public EduroamLoginTicket consumeTicket(String ticketId, String state, String platformType) {
        if (ticketId == null || ticketId.isBlank()) {
            throw new IllegalArgumentException("登录回调缺少票据");
        }
        long now = clock.getAsLong();
        EduroamLoginTicket ticket = tickets.consume(ticketId.trim())
                .orElseThrow(() -> new IllegalArgumentException("登录票据无效或已过期，请返回登录页重试"));
        if (ticket.expired(now)) {
            throw new IllegalArgumentException("登录票据已过期，请返回登录页重试");
        }
        if (!ticket.matchesState(state)) {
            throw new IllegalArgumentException("登录状态不匹配，请返回登录页重试");
        }
        if (!ticket.matchesType(platformType)) {
            throw new IllegalArgumentException("登录方式不匹配，请返回登录页重试");
        }
        return ticket;
    }

    // ------------------------------------------------------------------ 管理端

    public List<EduroamAccountDTO> listAccounts(String status, String keyword, int page, int size) {
        List<EduroamAccount> all = filterAccounts(status, keyword);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        // 只为当前这一页查本地账号：翻页大小有上限，不会把整表拿去逐个查用户。
        return all.subList(from, to).stream().map(this::toAccountDTO).toList();
    }

    public long countAccounts(String status, String keyword) {
        return filterAccounts(status, keyword).size();
    }

    public EduroamAccountDTO accountDetail(String id) {
        return toAccountDTO(requireAccount(id));
    }

    public EduroamAccountDTO blockAccount(String id, String operatorUserId, String reason) {
        EduroamAccount account = requireAccount(id);
        String note = reason == null || reason.isBlank() ? "管理员禁止登录" : reason.trim();
        return toAccountDTO(accounts.save(account.block(operatorUserId, note, clock.getAsLong())));
    }

    public EduroamAccountDTO unblockAccount(String id, String operatorUserId) {
        EduroamAccount account = requireAccount(id);
        return toAccountDTO(accounts.save(account.unblock(operatorUserId, clock.getAsLong())));
    }

    public void deleteAccount(String id) {
        EduroamAccount account = requireAccount(id);
        accounts.delete(account.id());
    }

    public List<EduroamAttemptDTO> listAttempts(String keyword, Boolean success, int page, int size) {
        List<EduroamAttempt> all = filterAttempts(keyword, success);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return all.subList(from, to).stream().map(assembler::toAttemptDTO).toList();
    }

    public long countAttempts(String keyword, Boolean success) {
        return filterAttempts(keyword, success).size();
    }

    // ------------------------------------------------------------------ 内部

    private List<EduroamAccount> filterAccounts(String status, String keyword) {
        String statusFilter = blankToNull(status);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return accounts.listAll().stream()
                .filter(item -> statusFilter == null || statusFilter.equals(item.status().name()))
                .filter(item -> kw.isEmpty()
                        || contains(item.email(), kw)
                        || contains(item.identity(), kw)
                        || contains(item.account(), kw)
                        || contains(item.domain(), kw))
                .sorted(Comparator.comparingLong(EduroamAccount::lastLoginAt).reversed())
                .toList();
    }

    private List<EduroamAttempt> filterAttempts(String keyword, Boolean success) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return attempts.listAll().stream()
                .filter(item -> success == null || item.success() == success)
                .filter(item -> kw.isEmpty()
                        || contains(item.email(), kw)
                        || contains(item.identity(), kw)
                        || contains(item.account(), kw)
                        || contains(item.clientIp(), kw))
                .sorted(Comparator.comparingLong(EduroamAttempt::createdAt).reversed())
                .toList();
    }

    private EduroamAccount requireAccount(String id) {
        String key = id == null ? "" : id.trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("账号记录 ID 不能为空");
        }
        return accounts.findById(key)
                .or(() -> accounts.findByEmail(key))
                .orElseThrow(() -> new IllegalArgumentException("账号记录不存在"));
    }

    /** 台账行：附上该「本站邮箱」在站内对应的账号（没有就留空），管理员据此核对要不要引导注册。 */
    private EduroamAccountDTO toAccountDTO(EduroamAccount account) {
        EduroamLocalUserPort.LocalUser localUser = localUsers.findByEmail(account.email()).orElse(null);
        return assembler.toAccountDTO(account, localUser);
    }

    private void recordAttempt(String email, String identity, String account, String domain, boolean success,
                               String reasonCode, String message, String detail, String clientIp,
                               long latencyMs, long now) {
        attempts.save(EduroamAttempt.of(email, identity, account, domain, success, reasonCode, message, detail,
                clientIp, latencyMs, now));
    }

    private String newTicketId() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 限流键：优先按客户端 IP（反代会带 x-forwarded-for / x-real-ip）。
     *
     * <p>拿不到 IP 时按<b>账号</b>限流，而不是所有请求共用一个键——共用一个键会在没有反代头的部署里
     * 让一个人的失败把全站登录一起挡掉。按账号限流放弃的是「同 IP 换账号继续试」的拦截，
     * 这个取舍比误伤全站安全得多。
     */
    private static String rateKey(String clientIp, String account) {
        String ip = clientIp == null ? "" : clientIp.trim();
        if (!ip.isEmpty()) {
            return "ip:" + ip;
        }
        String name = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
        return name.isEmpty() ? "ip:unknown" : "account:" + name;
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    /** 供测试断言成功原因码。 */
    static String successReason() {
        return REASON_SUCCESS;
    }
}
