package online.yudream.base.plugin.yggc.application.service;

import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.skin.api.PluginSkinProfile;
import online.yudream.base.plugin.skin.api.PluginSkinService;
import online.yudream.base.plugin.skin.api.PluginSkinTexture;
import online.yudream.base.plugin.yggc.domain.aggregate.AuthSession;
import online.yudream.base.plugin.yggc.domain.aggregate.ServerJoin;
import online.yudream.base.plugin.yggc.domain.aggregate.YggcSettings;
import online.yudream.base.plugin.yggc.infrastructure.repository.YggcRepository;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcCryptoService;
import online.yudream.base.plugin.yggc.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.yggc.interfaces.request.AuthenticateRequest;
import online.yudream.base.plugin.yggc.interfaces.request.JoinRequest;
import online.yudream.base.plugin.yggc.interfaces.request.RefreshRequest;
import online.yudream.base.plugin.yggc.interfaces.request.SignoutRequest;
import online.yudream.base.plugin.yggc.interfaces.request.TextureBindRequest;
import online.yudream.base.plugin.yggc.interfaces.request.TokenRequest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 传统 Yggdrasil API（authserver / sessionserver / profiles / 材质）。
 * 访问令牌与会话服务器同时被 YggcOAuthService 颁发的 OAuth 访问令牌识别。
 * 各项限制（令牌有效期 / 数量上限 / 频率限制 / 批量查询上限 / 皮肤白名单）由后台配置驱动。
 */
public class YggcAppService {

    private static final String SKIN_PLUGIN_CODE = "yudream-skin";
    private static final long JOIN_TTL = Duration.ofMinutes(5).toMillis();
    private static final int THROTTLE_ENTRY_LIMIT = 20000;
    private static final Logger LOG = Logger.getLogger(YggcAppService.class.getName());

    private final PluginContext context;
    private final YggcRepository repository;
    private final YggcCryptoService cryptoService;
    private final YggcSettingsService settingsService;
    private final YggcProfileSyncTrigger profileSyncTrigger;
    private final Map<String, Long> lastCalls = new ConcurrentHashMap<>();

    public YggcAppService(PluginContext context, YggcRepository repository, YggcCryptoService cryptoService,
                          YggcSettingsService settingsService, YggcProfileSyncTrigger profileSyncTrigger) {
        this.context = context;
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.settingsService = settingsService;
        this.profileSyncTrigger = profileSyncTrigger;
    }

    public YggcSettings settings() {
        return settingsService.current();
    }

    public Object metadata(String apiRoot, String textureBaseUrl) {
        YggcSettings settings = settingsService.current();
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("serverName", serverName());
        meta.put("implementationName", "YuDream Union Yggdrasil Connect Plugin");
        meta.put("implementationVersion", "1.0.0");
        meta.put("links", links(apiRoot));
        // 未禁用 Auth Server 时启动器才可以使用用户名密码的传统登录方式
        meta.put("feature.non_email_login", !settings.disableAuthserver());
        meta.put("feature.legacy_skin_api", false);
        meta.put("feature.no_mojang_namespace", false);
        meta.put("feature.enable_mojang_anti_features", false);
        meta.put("feature.enable_profile_key", false);
        meta.put("feature.username_check", false);
        // Yggdrasil Connect 协议发现入口：优先后台配置的 OpenID 提供者标识符
        meta.put("feature.openid_configuration_url", oauthIssuer(apiRoot) + "/.well-known/openid-configuration");
        body.put("meta", meta);
        body.put("skinDomains", skinDomains(textureBaseUrl, settings));
        body.put("signaturePublickey", cryptoService.texturePublicKeyPem());
        return body;
    }

    public Object status(String apiRoot, String textureBaseUrl) {
        YggcSettings settings = settingsService.current();
        String normalizedApiRoot = stripTrailingSlash(apiRoot);
        String normalizedTextureBaseUrl = stripTrailingSlash(textureBaseUrl);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("apiRoot", normalizedApiRoot);
        body.put("textureBaseUrl", normalizedTextureBaseUrl);
        body.put("oauthIssuer", oauthIssuer(apiRoot));
        body.put("accountSource", "system-user");
        body.put("skinPluginEnabled", skinServiceAvailable());
        body.put("metadata", metadata(normalizedApiRoot, normalizedTextureBaseUrl));
        body.put("features", features(settings));
        body.put("endpoints", List.of(
                "GET /",
                "POST /authserver/authenticate",
                "POST /authserver/refresh",
                "POST /authserver/validate",
                "POST /authserver/invalidate",
                "POST /authserver/signout",
                "POST /sessionserver/session/minecraft/join",
                "GET /sessionserver/session/minecraft/hasJoined",
                "GET /sessionserver/session/minecraft/profile/{uuid}",
                "POST /api/profiles/minecraft",
                "GET /api/users/profiles/minecraft/{username}",
                "PUT /api/user/profile/{uuid}/{textureType}",
                "DELETE /api/user/profile/{uuid}/{textureType}",
                "GET|POST /restore",
                "GET /.well-known/openid-configuration",
                "GET /.well-known/jwks.json",
                "GET /oauth/authorize",
                "POST /oauth/token",
                "POST /oauth/device",
                "GET /userinfo"
        ));
        return body;
    }

    private Map<String, Object> features(YggcSettings settings) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("ali", settings.enableAli());
        features.put("authServerEnabled", !settings.disableAuthserver());
        features.put("restoreApi", settings.restoreApi());
        features.put("oauth", true);
        features.put("rateLimit", settings.rateLimit());
        features.put("tokensLimit", settings.tokensLimit());
        features.put("searchProfileMax", settings.searchProfileMax());
        return features;
    }

    public Object authenticate(AuthenticateRequest request) {
        YggcSettings settings = settingsService.current();
        throttle("authenticate:" + normalizeKey(request.username()), settings.rateLimit());
        PluginUserProfile user = authenticateSystemUser(request.username(), request.password());
        List<PluginSkinProfile> profiles = profilesForUser(String.valueOf(user.id()));
        if (profiles.isEmpty()) {
            LOG.warning("[yggc] authenticate 失败：账号 " + request.username() + " 没有可用角色（请先在皮肤站创建角色）");
            throw authError("ForbiddenOperationException", "当前账号没有可用角色");
        }
        LOG.info("[yggc] authenticate 成功：用户 " + request.username() + "（userId=" + user.id() + "），角色 "
                + profiles.size() + " 个，已选 " + profiles.get(0).name());
        String clientToken = hasText(request.clientToken()) ? request.clientToken() : UUID.randomUUID().toString();
        PluginSkinProfile selected = profiles.get(0);
        AuthSession session = createSession(clientToken, user, selected, settings);
        pruneSessions(String.valueOf(user.id()), settings.tokensLimit());
        // 玩家刚用启动器登录：顺手把这位玩家名下的新角色补推到 Union 主服务器（异步，不影响登录耗时）。
        profileSyncTrigger.profilesInUse(String.valueOf(user.id()));
        return tokenResponse(session, profiles, selected, Boolean.TRUE.equals(request.requestUser()));
    }

    public Object refresh(RefreshRequest request) {
        YggcSettings settings = settingsService.current();
        AuthSession oldSession = validSession(request.accessToken(), request.clientToken());
        // 超过「令牌完全失效时间」后不可再刷新，必须重新登录
        if (oldSession.issuedAt() != null
                && oldSession.issuedAt() + settings.tokenRefreshExpire() * 1000L <= now()) {
            repository.deleteSession(oldSession.accessToken());
            throw authError("ForbiddenOperationException", "访问令牌已完全失效，请重新登录");
        }
        List<PluginSkinProfile> profiles = profilesForUser(oldSession.userId());
        String selectedId = request.selectedProfile() == null ? oldSession.selectedProfileId() : request.selectedProfile().id();
        PluginSkinProfile selected = findOwnedProfile(profiles, selectedId)
                .orElseThrow(() -> authError("ForbiddenOperationException", "角色不存在"));
        repository.deleteSession(oldSession.accessToken());
        AuthSession newSession = new AuthSession(randomToken(), oldSession.clientToken(), oldSession.userId(),
                selected.name(), selected.uuid(), now(), now() + settings.tokenExpire() * 1000L);
        repository.saveSession(newSession);
        return tokenResponse(newSession, profiles, selected, Boolean.TRUE.equals(request.requestUser()));
    }

    public void validate(TokenRequest request) {
        validSession(request.accessToken(), request.clientToken());
    }

    public void invalidate(TokenRequest request) {
        repository.findSession(request.accessToken()).ifPresent(session -> repository.deleteSession(session.accessToken()));
    }

    public void signout(SignoutRequest request) {
        YggcSettings settings = settingsService.current();
        throttle("signout:" + normalizeKey(request.username()), settings.rateLimit());
        PluginUserProfile user = authenticateSystemUser(request.username(), request.password());
        repository.findSessionsByUser(String.valueOf(user.id())).forEach(session -> repository.deleteSession(session.accessToken()));
    }

    /**
     * 启动器免密会话兑换（对齐 authlib-injector 的 launcher/exchange）：调用方已持有站点
     * 登录会话，此处按 userId 取角色并向 ygg 签发会话，免去密码重放。
     */
    public List<PluginSkinProfile> profilesOf(String userId) {
        return profilesForUser(userId);
    }

    /** 选择要登录的角色；角色名不存在时回落首个可用角色（与 authlib-injector 行为一致）。 */
    public PluginSkinProfile selectProfile(List<PluginSkinProfile> profiles, String requestedProfileName) {
        if (!hasText(requestedProfileName)) {
            return profiles.get(0);
        }
        String wanted = requestedProfileName.trim();
        return profiles.stream()
                .filter(profile -> wanted.equalsIgnoreCase(profile.name()) || wanted.equalsIgnoreCase(profile.uuid()))
                .findFirst()
                .orElse(profiles.get(0));
    }

    public AuthSession issueSession(String userId, String clientToken, PluginSkinProfile selected) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (selected == null) {
            throw new IllegalArgumentException("该账号没有可用角色（请先在皮肤站创建角色）");
        }
        YggcSettings settings = settingsService.current();
        String owner = userId.trim();
        String sessionClientToken = hasText(clientToken) ? clientToken.trim() : UUID.randomUUID().toString();
        AuthSession session = new AuthSession(randomToken(), sessionClientToken, owner,
                selected.name(), selected.uuid(), now(), now() + settings.tokenExpire() * 1000L);
        AuthSession saved = repository.saveSession(session);
        pruneSessions(owner, settings.tokensLimit());
        profileSyncTrigger.profilesInUse(owner);
        LOG.info("[yggc] 免密签发会话：userId=" + owner + "，角色 " + selected.name()
                + "（" + selected.uuid() + "）");
        return saved;
    }


    public void join(JoinRequest request) {
        AuthSession session = validSession(request.accessToken(), null);
        if (!session.selectedProfileId().equals(normalizeUuid(request.selectedProfile()))) {
            LOG.warning("[yggc] join 失败：访问令牌绑定角色 " + session.selectedProfileId()
                    + " 与请求角色 " + request.selectedProfile() + " 不匹配（serverId=" + request.serverId() + "）");
            throw authError("ForbiddenOperationException", "访问令牌与角色不匹配");
        }
        PluginSkinProfile profile = findOwnedProfile(session.userId(), session.selectedProfileId())
                .orElseThrow(() -> authError("ForbiddenOperationException", "角色不存在"));
        repository.saveJoin(new ServerJoin(
                request.serverId() + ":" + profile.uuid(),
                request.serverId(),
                profile.uuid(),
                profile.name(),
                session.accessToken(),
                now() + JOIN_TTL
        ));
        LOG.info("[yggc] join 成功：角色 " + profile.name() + "（" + profile.uuid() + "）加入服务器 serverId="
                + request.serverId() + "，有效期 " + JOIN_TTL / 1000 + " 秒");
        // 进入服务器同样是一次「角色已被使用」，用于兜住跳过 authserver 的场景（如 OAuth 登录）。
        profileSyncTrigger.profilesInUse(session.userId());
    }

    public Optional<Object> hasJoined(String username, String serverId, String textureBaseUrl) {
        if (!hasText(username) || !hasText(serverId)) {
            LOG.warning("[yggc] hasJoined 缺少参数：username=" + username + ", serverId=" + serverId);
            return Optional.empty();
        }
        List<ServerJoin> joins = repository.findJoinsByServer(serverId);
        Optional<ServerJoin> matched = joins.stream()
                .filter(join -> join.expiresAt() != null && join.expiresAt() > now())
                .filter(join -> username.equalsIgnoreCase(join.username()))
                .findFirst();
        if (matched.isEmpty()) {
            LOG.warning("[yggc] hasJoined 未命中：username=" + username + ", serverId=" + serverId
                    + "（当前 serverId 记录 " + joins.size() + " 条"
                    + (joins.isEmpty() ? "，启动器未调用 join 或 join 已过期"
                    : "，用户名/过期时间不匹配：" + joins.stream()
                    .map(j -> j.username() + "@" + (j.expiresAt() == null ? "?" :
                    (j.expiresAt() > now() ? "有效" : "已过期")))
                    .toList())
                    + "）");
            return Optional.empty();
        }
        Optional<PluginSkinProfile> profile = skinService().findProfileByUuid(matched.get().profileId());
        if (profile.isEmpty()) {
            LOG.warning("[yggc] hasJoined 找到 join 记录但角色不存在：uuid=" + matched.get().profileId());
            return Optional.empty();
        }
        LOG.info("[yggc] hasJoined 命中：username=" + username + ", serverId=" + serverId
                + " → 角色 " + profile.get().name() + "（" + profile.get().uuid() + "）");
        return profile.map(p -> profileResponse(p, textureBaseUrl, false));
    }

    public Object profile(String uuid, String textureBaseUrl, boolean unsigned) {
        PluginSkinProfile profile = findProfileWithRemap(uuid)
                .orElseThrow(() -> authError("ForbiddenOperationException", "角色不存在"));
        return profileResponse(profile, textureBaseUrl, unsigned);
    }

    /** 按 UUID 查询角色；未命中时回退到 Union 下发的 UUID 重映射表（旧 → 新）。 */
    private Optional<PluginSkinProfile> findProfileWithRemap(String uuid) {
        Optional<PluginSkinProfile> found = skinService().findProfileByUuid(uuid);
        if (found.isPresent() || uuid == null) {
            return found;
        }
        String normalized = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : repository.uuidMappings().entrySet()) {
            if (entry.getKey().replace("-", "").toLowerCase(Locale.ROOT).equals(normalized)) {
                return skinService().findProfileByUuid(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public Object profiles(List<String> names) {
        YggcSettings settings = settingsService.current();
        List<String> requested = names == null ? List.of() : names;
        if (requested.size() > settings.searchProfileMax()) {
            throw authError("ForbiddenOperationException",
                    "一次最多查询 " + settings.searchProfileMax() + " 个角色");
        }
        return skinService().findProfilesByNames(requested).stream()
                .map(this::profileSummary)
                .toList();
    }

    /** 按角色名查询单个角色（multiplayer / minecraftservices 兼容接口）。 */
    public Object profileByName(String name, String textureBaseUrl, boolean unsigned) {
        if (!hasText(name)) {
            throw authError("IllegalArgumentException", "角色名不能为空");
        }
        return skinService().findProfilesByNames(List.of(name.trim())).stream()
                .findFirst()
                .map(profile -> profileResponse(profile, textureBaseUrl, unsigned))
                .orElseThrow(() -> authError("ForbiddenOperationException", "角色不存在"));
    }

    /**
     * Restore API：把客户端送来的角色资料中的 properties 重新签名后返回。
     * 供修改版 MultiLogin 等第三方后端复用本站密钥，需在后台显式启用。
     */
    public Object restore(Map<String, Object> profile) {
        if (!settingsService.current().restoreApi()) {
            throw authError("ForbiddenOperationException", "Restore API 未启用");
        }
        if (profile == null || !(profile.get("properties") instanceof List<?> properties)) {
            throw authError("IllegalArgumentException", "请求体中缺少 properties 字段");
        }
        List<Map<String, Object>> resigned = new ArrayList<>();
        for (Object item : properties) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> property = new LinkedHashMap<>();
            raw.forEach((key, value) -> property.put(String.valueOf(key), value));
            if (!hasText(property.get("value") == null ? null : String.valueOf(property.get("value")))) {
                continue;
            }
            property.put("signature", cryptoService.signTexture(String.valueOf(property.get("value"))));
            resigned.add(property);
        }
        Map<String, Object> body = new LinkedHashMap<>(profile);
        body.put("properties", resigned);
        return body;
    }

    public void setTexture(String accessToken, String uuid, String textureType, TextureBindRequest request) {
        AuthSession session = validSession(accessToken, null);
        findOwnedProfile(session.userId(), uuid)
                .orElseThrow(() -> authError("ForbiddenOperationException", "只能操作自己的角色"));
        skinService().setProfileTexture(uuid, textureType, request.hash());
    }

    public void clearTexture(String accessToken, String uuid, String textureType) {
        AuthSession session = validSession(accessToken, null);
        findOwnedProfile(session.userId(), uuid)
                .orElseThrow(() -> authError("ForbiddenOperationException", "只能操作自己的角色"));
        skinService().clearProfileTexture(uuid, textureType);
    }

    public RuntimeException authError(String error, String message) {
        return new YggcException(error, message);
    }

    public Object errorBody(YggcException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", exception.error());
        body.put("errorMessage", exception.getMessage());
        return body;
    }

    // ---- 内部 ----

    private boolean skinServiceAvailable() {
        return context.service(SKIN_PLUGIN_CODE, PluginSkinService.class).isPresent();
    }

    private Map<String, String> links(String apiRoot) {
        String homepage = originOf(apiRoot).orElseGet(() -> stripTrailingSlash(apiRoot));
        Map<String, String> links = new LinkedHashMap<>();
        links.put("homepage", homepage);
        links.put("register", homepage + "/login");
        return links;
    }

    private String serverName() {
        // 认证服务器名称配置项优先；未配置时回退站点名（app.name / app.site-name）
        String configured = settingsService.current().serverName();
        if (hasText(configured)) {
            return configured;
        }
        return context.framework().setting("app.name")
                .or(() -> context.framework().setting("app.site-name"))
                .filter(YggcAppService::hasText)
                .orElse("YuDream Yggdrasil Connect");
    }

    /** OAuth / OIDC 的 issuer：后台配置的 OpenID 提供者标识符优先，未配置时使用本站 API 地址。 */
    public String oauthIssuer(String apiRoot) {
        String configured = settingsService.current().oauthIssuerFallback();
        return configured.isBlank() ? stripTrailingSlash(apiRoot) : configured;
    }

    /**
     * 皮肤白名单域名：额外白名单（ygg_skin_domain）+ 材质地址主机，本机地址附带 localhost 常用值。
     */
    private List<String> skinDomains(String textureBaseUrl, YggcSettings settings) {
        Set<String> domains = new LinkedHashSet<>(settings.extraSkinDomains());
        Optional<String> textureHost = originHost(textureBaseUrl);
        textureHost.ifPresent(domains::add);
        if (textureHost.isEmpty() || textureHost.filter(YggcAppService::isLocalHost).isPresent()) {
            domains.add(".localhost");
            domains.add("localhost");
            domains.add("127.0.0.1");
        }
        return List.copyOf(domains);
    }

    private static boolean isLocalHost(String host) {
        String value = host.toLowerCase(Locale.ROOT);
        return value.startsWith("localhost") || value.startsWith("127.0.0.1") || "::1".equals(value);
    }

    /** 登录 / 登出频率限制（ygg_rate_limit，单位毫秒，0 表示不限制）。 */
    private void throttle(String key, long intervalMillis) {
        if (intervalMillis <= 0 || key == null || key.isBlank()) {
            return;
        }
        long current = now();
        Long previous = lastCalls.put(key, current);
        if (previous != null && current - previous < intervalMillis) {
            long retryAfter = intervalMillis - (current - previous);
            throw authError("ForbiddenOperationException",
                    "操作过于频繁，请 " + Math.max(1, retryAfter / 1000) + " 秒后再试");
        }
        if (lastCalls.size() > THROTTLE_ENTRY_LIMIT) {
            lastCalls.entrySet().removeIf(entry -> current - entry.getValue() > Math.max(intervalMillis, 60000L));
        }
    }

    /** 令牌数量限制（ygg_tokens_limit）：仅保留最新的 N 个会话。 */
    private void pruneSessions(String userId, int limit) {
        List<AuthSession> sessions = repository.findSessionsByUser(userId).stream()
                .sorted((left, right) -> Long.compare(
                        right.issuedAt() == null ? 0L : right.issuedAt(),
                        left.issuedAt() == null ? 0L : left.issuedAt()))
                .toList();
        for (int index = limit; index < sessions.size(); index++) {
            repository.deleteSession(sessions.get(index).accessToken());
        }
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Optional<String> originOf(String url) {
        return Optional.ofNullable(UrlSupports.originOf(url));
    }

    private Optional<String> originHost(String url) {
        try {
            java.net.URI uri = java.net.URI.create(stripTrailingSlash(url));
            return hasText(uri.getHost()) ? Optional.of(uri.getHost()) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private AuthSession createSession(String clientToken, PluginUserProfile user, PluginSkinProfile selected,
                                      YggcSettings settings) {
        AuthSession session = new AuthSession(randomToken(), clientToken, String.valueOf(user.id()),
                selected == null ? user.username() : selected.name(),
                selected == null ? null : selected.uuid(), now(), now() + settings.tokenExpire() * 1000L);
        return repository.saveSession(session);
    }

    private Object tokenResponse(AuthSession session, List<PluginSkinProfile> profiles, PluginSkinProfile selected, boolean requestUser) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", session.accessToken());
        body.put("clientToken", session.clientToken());
        body.put("availableProfiles", profiles.stream().map(this::profileSummary).toList());
        if (selected != null) {
            body.put("selectedProfile", profileSummary(selected));
        }
        if (requestUser) {
            body.put("user", Map.of("id", session.userId(), "properties", List.of()));
        }
        return body;
    }

    Map<String, Object> profileSummary(PluginSkinProfile profile) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", profile.uuid());
        summary.put("name", profile.name());
        return summary;
    }

    Object profileResponse(PluginSkinProfile profile, String textureBaseUrl, boolean unsigned) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", profile.uuid());
        body.put("name", profile.name());
        body.put("properties", List.of(
                textureProperty(profile, textureBaseUrl, unsigned),
                uploadableTexturesProperty(unsigned)
        ));
        return body;
    }

    private Map<String, Object> textureProperty(PluginSkinProfile profile, String textureBaseUrl, boolean unsigned) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("name", "textures");
        String value = Base64.getEncoder().encodeToString(texturePayload(profile, textureBaseUrl, !unsigned).getBytes(StandardCharsets.UTF_8));
        property.put("value", value);
        if (!unsigned) {
            property.put("signature", cryptoService.signTexture(value));
        }
        return property;
    }

    private Map<String, Object> uploadableTexturesProperty(boolean unsigned) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("name", "uploadableTextures");
        String value = "skin,cape";
        property.put("value", value);
        if (!unsigned) {
            property.put("signature", cryptoService.signTexture(value));
        }
        return property;
    }

    private String texturePayload(PluginSkinProfile profile, String textureBaseUrl, boolean signatureRequired) {
        Map<String, Object> textures = new LinkedHashMap<>();
        if (profile.skin() != null) {
            textures.put("SKIN", textureNode(profile.skin(), textureBaseUrl));
        }
        if (profile.cape() != null) {
            textures.put("CAPE", textureNode(profile.cape(), textureBaseUrl));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", now());
        payload.put("profileId", profile.uuid());
        payload.put("profileName", profile.name());
        payload.put("isPublic", true);
        if (signatureRequired) {
            payload.put("signatureRequired", true);
        }
        payload.put("textures", textures);
        return JsonSupport.write(payload);
    }

    private Map<String, Object> textureNode(PluginSkinTexture texture, String textureBaseUrl) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("url", textureBaseUrl + "/" + texture.hash());
        if ("skin".equalsIgnoreCase(texture.type()) && "slim".equalsIgnoreCase(texture.model())) {
            node.put("metadata", Map.of("model", "slim"));
        }
        return node;
    }

    AuthSession validSession(String accessToken, String clientToken) {
        Optional<AuthSession> found = repository.findSession(accessToken);
        if (found.isEmpty()) {
            LOG.warning("[yggc] 访问令牌无效：" + maskToken(accessToken));
            throw authError("ForbiddenOperationException", "访问令牌无效");
        }
        AuthSession session = found.get();
        if (clientToken != null && !clientToken.equals(session.clientToken())) {
            LOG.warning("[yggc] 客户端令牌不匹配：" + maskToken(accessToken));
            throw authError("ForbiddenOperationException", "客户端令牌不匹配");
        }
        if (session.expiresAt() == null || session.expiresAt() <= now()) {
            LOG.warning("[yggc] 访问令牌已过期：" + maskToken(accessToken));
            repository.deleteSession(session.accessToken());
            throw authError("ForbiddenOperationException", "访问令牌已过期");
        }
        return session;
    }

    /** 日志中只展示令牌前 8 位，避免完整凭据泄露。 */
    static String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "(空)";
        }
        return token.substring(0, 8) + "...";
    }

    PluginSkinService skinService() {
        return context.service(SKIN_PLUGIN_CODE, PluginSkinService.class)
                .orElseThrow(() -> authError("ForbiddenOperationException", "yudream-skin 插件未启用"));
    }

    private PluginUserProfile authenticateSystemUser(String usernameOrEmail, String password) {
        return context.framework().users().authenticate(usernameOrEmail, password)
                .orElseThrow(() -> authError("ForbiddenOperationException", "用户名或密码错误"));
    }

    List<PluginSkinProfile> profilesForUser(String userId) {
        if (!hasText(userId)) {
            return List.of();
        }
        return skinService().findProfilesByOwner(userId.trim());
    }

    Optional<PluginSkinProfile> findOwnedProfile(List<PluginSkinProfile> profiles, String uuid) {
        String normalized = normalizeUuid(uuid);
        if (normalized == null) {
            return Optional.empty();
        }
        return profiles.stream()
                .filter(profile -> normalized.equals(normalizeUuid(profile.uuid())))
                .findFirst();
    }

    private Optional<PluginSkinProfile> findOwnedProfile(String userId, String uuid) {
        return findOwnedProfile(profilesForUser(userId), uuid);
    }

    String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    static long now() {
        return System.currentTimeMillis();
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static String normalizeUuid(String uuid) {
        return uuid == null ? null : uuid.replace("-", "").toLowerCase(Locale.ROOT);
    }

    public static class YggcException extends RuntimeException {
        private final String error;

        public YggcException(String error, String message) {
            super(message);
            this.error = error;
        }

        public String error() {
            return error;
        }
    }
}
