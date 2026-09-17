package online.yudream.base.plugin.yggc.application.service;

import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.skin.api.PluginSkinProfile;
import online.yudream.base.plugin.yggc.domain.aggregate.DeviceCode;
import online.yudream.base.plugin.yggc.domain.aggregate.OAuthClient;
import online.yudream.base.plugin.yggc.domain.aggregate.OAuthCode;
import online.yudream.base.plugin.yggc.domain.aggregate.OAuthRefreshToken;
import online.yudream.base.plugin.yggc.domain.aggregate.OAuthToken;
import online.yudream.base.plugin.yggc.domain.aggregate.ServerJoin;
import online.yudream.base.plugin.yggc.domain.aggregate.YggcSettings;
import online.yudream.base.plugin.yggc.infrastructure.repository.YggcRepository;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcCryptoService;
import online.yudream.base.plugin.yggc.interfaces.request.JoinRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Yggdrasil Connect 协议（Janus 能力）服务端：OAuth 2.0 授权码 + PKCE、
 * 设备授权（RFC 8628）、刷新令牌旋转、RS256 ID Token、UserInfo 与发现文档。
 */
public class YggcOAuthService {

    public static final String SCOPE_OPENID = "openid";
    public static final String SCOPE_PROFILE = "profile";
    public static final String SCOPE_OFFLINE = "offline_access";
    public static final String SCOPE_SELECT = "Yggdrasil.PlayerProfiles.Select";
    public static final String SCOPE_READ = "Yggdrasil.PlayerProfiles.Read";
    public static final String SCOPE_JOIN = "Yggdrasil.Server.Join";

    public static final List<String> SUPPORTED_SCOPES = List.of(
            SCOPE_OPENID, SCOPE_PROFILE, SCOPE_OFFLINE, SCOPE_SELECT, SCOPE_READ, SCOPE_JOIN);

    private static final long CODE_TTL = Duration.ofMinutes(5).toMillis();
    private static final long ID_TOKEN_TTL = Duration.ofMinutes(10).toMillis();
    private static final long JOIN_TTL = Duration.ofMinutes(5).toMillis();
    private static final int DEVICE_INTERVAL = 5;
    private static final Logger LOG = Logger.getLogger(YggcOAuthService.class.getName());

    private static final Map<String, String> SCOPE_LABELS = Map.of(
            SCOPE_OPENID, "获取你的基础身份信息",
            SCOPE_PROFILE, "获取你的详细资料（昵称、邮箱）",
            SCOPE_OFFLINE, "离线访问（颁发刷新令牌）",
            SCOPE_SELECT, "获取你选择的游戏角色",
            SCOPE_READ, "获取你的全部角色列表",
            SCOPE_JOIN, "使用该角色加入 Minecraft 多人服务器");

    private final PluginContext context;
    private final YggcRepository repository;
    private final YggcCryptoService cryptoService;
    private final YggcAppService appService;
    private final YggcSettingsService settingsService;

    public YggcOAuthService(PluginContext context, YggcRepository repository,
                            YggcCryptoService cryptoService, YggcAppService appService,
                            YggcSettingsService settingsService) {
        this.context = context;
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.appService = appService;
        this.settingsService = settingsService;
    }

    // ---- 发现文档 ----

    public Map<String, Object> discovery(String apiRoot) {
        String issuer = YggcAppService.stripTrailingSlash(apiRoot);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("issuer", issuer);
        body.put("authorization_endpoint", issuer + "/oauth/authorize");
        body.put("token_endpoint", issuer + "/oauth/token");
        body.put("device_authorization_endpoint", issuer + "/oauth/device");
        body.put("userinfo_endpoint", issuer + "/userinfo");
        body.put("jwks_uri", issuer + "/.well-known/jwks.json");
        body.put("response_types_supported", List.of("code"));
        body.put("grant_types_supported", List.of("authorization_code", "refresh_token",
                "urn:ietf:params:oauth:grant-type:device_code"));
        body.put("subject_types_supported", List.of("public"));
        body.put("id_token_signing_alg_values_supported", List.of("RS256"));
        body.put("scopes_supported", SUPPORTED_SCOPES);
        body.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post", "none"));
        body.put("code_challenge_methods_supported", List.of("S256", "plain"));
        body.put("claims_supported", List.of("sub", "aud", "iss", "selectedProfile", "availableProfiles",
                "name", "preferred_username", "nickname", "email"));
        // 共享客户端：没有内置 client_id 的 Yggdrasil Connect 启动器（如 PCL-CE）直接读取该字段登录。
        // 未配置时不输出，保持发现文档与旧版本完全一致。
        String sharedClientId = settings().sharedClientId();
        if (YggcAppService.hasText(sharedClientId)) {
            body.put("shared_client_id", sharedClientId);
        }
        return body;
    }

    public Map<String, Object> jwks() {
        return cryptoService.jwks();
    }

    // ---- 授权请求 ----

    /**
     * 授权请求上下文（授权确认页数据），同时完成全部参数校验。
     */
    public Map<String, Object> authorizationContext(String userId, Map<String, String> params) {
        OAuthClient client = requireClient(params.get("client_id"));
        String redirectUri = requireRegisteredRedirectUri(client, params.get("redirect_uri"));
        List<String> scopes = parseScopes(params.get("scope"));
        validateScopes(scopes);
        validatePkce(params);
        PluginUserProfile user = requireUser(userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client", clientView(client));
        body.put("scopes", scopes.stream().map(this::scopeView).toList());
        body.put("profiles", profileViews(userId));
        body.put("requireProfileSelection", scopes.contains(SCOPE_SELECT));
        body.put("user", Map.of("id", String.valueOf(user.id()),
                "nickname", user.nickname() == null ? user.username() : user.nickname()));
        return body;
    }

    /**
     * 用户在授权确认页作出决定后，生成授权码并构造回跳地址。
     */
    public Map<String, Object> decide(String userId, Map<String, String> params, boolean approve, String profileId) {
        OAuthClient client = requireClient(params.get("client_id"));
        String redirectUri = requireRegisteredRedirectUri(client, params.get("redirect_uri"));
        List<String> scopes = parseScopes(params.get("scope"));
        validateScopes(scopes);
        validatePkce(params);
        if (!approve) {
            return Map.of("redirectUrl", errorRedirectUrl(redirectUri, params.get("state"), "access_denied", "用户拒绝授权"));
        }
        String boundProfileId = null;
        if (scopes.contains(SCOPE_SELECT)) {
            PluginSkinProfile selected = appService.findOwnedProfile(profilesForUser(userId), profileId)
                    .orElseThrow(() -> new OAuthException("invalid_request", "请选择自己的角色", 400));
            boundProfileId = selected.uuid();
        }
        String code = "yggc_ac_" + cryptoService.randomToken(43);
        repository.saveCode(new OAuthCode(code, client.id(), userId, boundProfileId, scopes, redirectUri,
                params.get("code_challenge"), params.get("code_challenge_method"), params.get("nonce"),
                System.currentTimeMillis() + CODE_TTL));
        StringBuilder url = new StringBuilder(redirectUri);
        url.append(redirectUri.contains("?") ? '&' : '?');
        url.append("code=").append(code);
        if (YggcAppService.hasText(params.get("state"))) {
            url.append("&state=").append(params.get("state"));
        }
        return Map.of("redirectUrl", url.toString());
    }

    /**
     * 授权请求出错且 redirect_uri 可信时，构造回跳错误地址。
     */
    public String errorRedirect(Map<String, String> params, String error, String description) {
        if (!YggcAppService.hasText(params.get("client_id")) || !YggcAppService.hasText(params.get("redirect_uri"))) {
            return null;
        }
        Optional<OAuthClient> client = repository.findClient(params.get("client_id").trim());
        if (client.isEmpty() || !client.get().redirectUris().contains(params.get("redirect_uri"))) {
            return null;
        }
        return errorRedirectUrl(params.get("redirect_uri"), params.get("state"), error, description);
    }

    // ---- 令牌端点 ----

    public Map<String, Object> token(Map<String, String> form, String basicUser, String basicPassword, String issuer) {
        Map<String, String> body = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        body.putAll(form);
        String grantType = body.get("grant_type");
        String clientId = YggcAppService.hasText(basicUser) ? basicUser : body.get("client_id");
        String clientSecret = YggcAppService.hasText(basicPassword) ? basicPassword : body.get("client_secret");
        if (!YggcAppService.hasText(grantType)) {
            throw new OAuthException("invalid_request", "缺少 grant_type", 400);
        }
        return switch (grantType) {
            case "authorization_code" -> tokenByCode(body, clientId, clientSecret, issuer);
            case "refresh_token" -> tokenByRefresh(body, clientId, clientSecret, issuer);
            case "urn:ietf:params:oauth:grant-type:device_code" -> tokenByDevice(body, clientId, clientSecret, issuer);
            default -> throw new OAuthException("unsupported_grant_type", "不支持的授权模式", 400);
        };
    }

    private Map<String, Object> tokenByCode(Map<String, String> body, String clientId, String clientSecret, String issuer) {
        OAuthClient client = requireClient(clientId);
        String codeValue = body.get("code");
        OAuthCode code = repository.findCode(codeValue == null ? "" : codeValue)
                .orElseThrow(() -> new OAuthException("invalid_grant", "授权码无效或已使用", 400));
        if (code.expiresAt() == null || code.expiresAt() <= System.currentTimeMillis()) {
            repository.deleteCode(code.code());
            throw new OAuthException("invalid_grant", "授权码已过期", 400);
        }
        if (!code.clientId().equals(client.id())) {
            throw new OAuthException("invalid_grant", "授权码与客户端不匹配", 400);
        }
        String redirectUri = body.get("redirect_uri");
        if (!code.redirectUri().equals(redirectUri)) {
            throw new OAuthException("invalid_grant", "redirect_uri 与授权请求不一致", 400);
        }
        boolean pkce = YggcAppService.hasText(code.codeChallenge());
        if (pkce) {
            String verifier = body.get("code_verifier");
            if (!YggcAppService.hasText(verifier) || !verifyPkce(verifier, code.codeChallenge())) {
                throw new OAuthException("invalid_grant", "PKCE 校验失败", 400);
            }
        } else {
            requireClientSecret(client, clientSecret);
        }
        repository.deleteCode(code.code());
        return issueTokens(client, code.userId(), code.profileId(), code.scopes(), code.nonce(), issuer);
    }

    private Map<String, Object> tokenByRefresh(Map<String, String> body, String clientId, String clientSecret, String issuer) {
        OAuthClient client = requireClient(clientId);
        requireClientSecret(client, clientSecret);
        String tokenValue = body.get("refresh_token");
        OAuthRefreshToken refresh = repository.findRefreshToken(tokenValue == null ? "" : tokenValue)
                .orElseThrow(() -> new OAuthException("invalid_grant", "刷新令牌无效或已使用", 400));
        if (refresh.expiresAt() == null || refresh.expiresAt() <= System.currentTimeMillis()) {
            repository.deleteRefreshToken(refresh.token());
            throw new OAuthException("invalid_grant", "刷新令牌已过期", 400);
        }
        if (!refresh.clientId().equals(client.id())) {
            throw new OAuthException("invalid_grant", "刷新令牌与客户端不匹配", 400);
        }
        // 刷新令牌一次性：旋转并吊销旧访问令牌
        repository.deleteRefreshToken(refresh.token());
        repository.deleteToken(refresh.accessToken());
        return issueTokens(client, refresh.userId(), refresh.profileId(), refresh.scopes(), null, issuer);
    }

    private Map<String, Object> tokenByDevice(Map<String, String> body, String clientId, String clientSecret, String issuer) {
        OAuthClient client = requireClient(clientId);
        if (!client.publicClient()) {
            requireClientSecret(client, clientSecret);
        }
        String deviceCodeValue = body.get("device_code");
        DeviceCode device = repository.findDeviceCode(deviceCodeValue == null ? "" : deviceCodeValue)
                .orElseThrow(() -> new OAuthException("expired_token", "设备码无效或已过期", 400));
        if (!device.clientId().equals(client.id())) {
            throw new OAuthException("invalid_grant", "设备码与客户端不匹配", 400);
        }
        long now = System.currentTimeMillis();
        if (device.expiresAt() == null || device.expiresAt() <= now) {
            repository.deleteDeviceCode(device.deviceCode());
            throw new OAuthException("expired_token", "设备码已过期", 400);
        }
        if (DeviceCode.DENIED.equals(device.status())) {
            repository.deleteDeviceCode(device.deviceCode());
            throw new OAuthException("access_denied", "用户拒绝了授权", 400);
        }
        if (DeviceCode.PENDING.equals(device.status())) {
            long intervalMs = device.intervalSeconds() * 1000L;
            if (device.lastPolledAt() != null && now - device.lastPolledAt() < intervalMs) {
                throw new OAuthException("slow_down", "轮询过快", 400);
            }
            repository.saveDeviceCode(new DeviceCode(device.deviceCode(), device.userCode(), device.clientId(),
                    device.scopes(), device.status(), device.userId(), device.profileId(), device.expiresAt(),
                    device.intervalSeconds(), now));
            throw new OAuthException("authorization_pending", "等待用户授权", 400);
        }
        List<String> scopes = parseScopes(device.scopes());
        repository.deleteDeviceCode(device.deviceCode());
        return issueTokens(client, device.userId(), device.profileId(), scopes, null, issuer);
    }

    private Map<String, Object> issueTokens(OAuthClient client, String userId, String profileId,
                                            List<String> scopes, String nonce, String issuer) {
        long now = System.currentTimeMillis();
        long accessTtl = settings().oauthAccessTtl() * 1000L;
        PluginUserProfile user = requireUser(userId);
        PluginSkinProfile boundProfile = null;
        if (YggcAppService.hasText(profileId)) {
            boundProfile = appService.findOwnedProfile(profilesForUser(userId), profileId).orElse(null);
        }
        String accessToken = "yggc_at_" + cryptoService.randomToken(43);
        OAuthToken token = new OAuthToken(accessToken, client.id(), userId,
                user.nickname() == null ? user.username() : user.nickname(),
                boundProfile == null ? profileId : boundProfile.uuid(),
                boundProfile == null ? null : boundProfile.name(),
                scopes, now, now + accessTtl);
        repository.saveToken(token);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", accessToken);
        response.put("token_type", "Bearer");
        // expires_in 必须是 JSON number（RFC 6749/8628）；宿主序列化会把 long 转成字符串，强转 int 规避。
        response.put("expires_in", (int) (accessTtl / 1000));
        response.put("scope", String.join(" ", scopes));
        if (scopes.contains(SCOPE_OFFLINE)) {
            String refreshToken = "yggc_rt_" + cryptoService.randomToken(43);
            repository.saveRefreshToken(new OAuthRefreshToken(refreshToken, client.id(), userId, profileId,
                    accessToken, scopes, now, now + settings().oauthRefreshTtl() * 1000L));
            response.put("refresh_token", refreshToken);
        }
        response.put("id_token", idToken(issuer, client, token, user, boundProfile, scopes, nonce, now));
        return response;
    }

    private String idToken(String issuer, OAuthClient client, OAuthToken token, PluginUserProfile user,
                           PluginSkinProfile boundProfile, List<String> scopes, String nonce, long now) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", YggcAppService.stripTrailingSlash(issuer));
        claims.put("sub", token.userId());
        claims.put("aud", client.id());
        claims.put("iat", now / 1000);
        claims.put("exp", (now + ID_TOKEN_TTL) / 1000);
        if (YggcAppService.hasText(nonce)) {
            claims.put("nonce", nonce);
        }
        if (boundProfile != null) {
            claims.put("selectedProfile", Map.of("id", boundProfile.uuid(), "name", boundProfile.name()));
        }
        if (scopes.contains(SCOPE_READ)) {
            claims.put("availableProfiles", profileViews(token.userId()));
        }
        if (scopes.contains(SCOPE_PROFILE)) {
            claims.put("name", user.nickname() == null ? user.username() : user.nickname());
            claims.put("preferred_username", user.username());
            if (user.nickname() != null) {
                claims.put("nickname", user.nickname());
            }
            if (YggcAppService.hasText(user.email())) {
                claims.put("email", user.email());
            }
        }
        return cryptoService.signIdToken(claims);
    }

    // ---- UserInfo ----

    public Map<String, Object> userInfo(String bearerToken) {
        OAuthToken token = repository.findToken(bearerToken)
                .orElseThrow(() -> new OAuthException("invalid_token", "访问令牌无效", 401));
        if (token.expired(System.currentTimeMillis())) {
            repository.deleteToken(token.token());
            throw new OAuthException("invalid_token", "访问令牌已过期", 401);
        }
        PluginUserProfile user = requireUser(token.userId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sub", token.userId());
        body.put("aud", token.clientId());
        if (YggcAppService.hasText(token.profileId())) {
            body.put("selectedProfile", Map.of("id", token.profileId(),
                    "name", token.profileName() == null ? "" : token.profileName()));
        }
        if (token.hasScope(SCOPE_READ)) {
            body.put("availableProfiles", profileViews(token.userId()));
        }
        if (token.hasScope(SCOPE_PROFILE)) {
            body.put("name", user.nickname() == null ? user.username() : user.nickname());
            body.put("preferred_username", user.username());
            if (user.nickname() != null) {
                body.put("nickname", user.nickname());
            }
            if (YggcAppService.hasText(user.email())) {
                body.put("email", user.email());
            }
        }
        return body;
    }

    // ---- 设备授权 ----

    public Map<String, Object> startDevice(Map<String, String> form, String webOrigin) {
        OAuthClient client = requireClient(form.get("client_id"));
        List<String> scopes = parseScopes(form.get("scope"));
        validateScopes(scopes);
        long now = System.currentTimeMillis();
        String deviceCode = "yggc_dc_" + cryptoService.randomToken(43);
        String userCode = cryptoService.randomUserCode();
        long deviceTtl = settings().oauthDeviceTtl() * 1000L;
        repository.saveDeviceCode(new DeviceCode(deviceCode, userCode, client.id(), String.join(" ", scopes),
                DeviceCode.PENDING, null, null, now + deviceTtl, DEVICE_INTERVAL, null));
        String origin = YggcAppService.stripTrailingSlash(webOrigin);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("device_code", deviceCode);
        body.put("user_code", userCode);
        body.put("verification_uri", origin + "/platform/plugins/yggc/device");
        body.put("verification_uri_complete", origin + "/platform/plugins/yggc/device?user_code=" + userCode);
        // 同上：int 保证 JSON number 输出，long 会被宿主序列化成字符串导致 SJMCL 等严格客户端 ParseError。
        body.put("expires_in", (int) (deviceTtl / 1000));
        body.put("interval", DEVICE_INTERVAL);
        return body;
    }

    public Map<String, Object> deviceContextFor(String userId, String userCode) {
        DeviceCode device = repository.findDeviceCodeByUserCode(userCode)
                .orElseThrow(() -> new OAuthException("invalid_request", "设备码无效", 404));
        if (device.expiresAt() != null && device.expiresAt() <= System.currentTimeMillis()) {
            repository.deleteDeviceCode(device.deviceCode());
            throw new OAuthException("invalid_request", "设备码已过期，请回到应用重新发起", 410);
        }
        OAuthClient client = requireClient(device.clientId());
        List<String> scopes = parseScopes(device.scopes());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client", clientView(client));
        body.put("scopes", scopes.stream().map(this::scopeView).toList());
        body.put("profiles", profileViews(userId));
        body.put("requireProfileSelection", scopes.contains(SCOPE_SELECT));
        body.put("status", device.status());
        return body;
    }

    public Map<String, Object> deviceDecision(String userId, String userCode, boolean approve, String profileId) {
        DeviceCode device = repository.findDeviceCodeByUserCode(userCode)
                .orElseThrow(() -> new OAuthException("invalid_request", "设备码无效", 404));
        if (device.expiresAt() != null && device.expiresAt() <= System.currentTimeMillis()) {
            repository.deleteDeviceCode(device.deviceCode());
            throw new OAuthException("invalid_request", "设备码已过期", 410);
        }
        if (!DeviceCode.PENDING.equals(device.status())) {
            return Map.of("status", device.status());
        }
        List<String> scopes = parseScopes(device.scopes());
        String boundProfileId = null;
        if (approve && scopes.contains(SCOPE_SELECT)) {
            PluginSkinProfile selected = appService.findOwnedProfile(profilesForUser(userId), profileId)
                    .orElseThrow(() -> new OAuthException("invalid_request", "请选择自己的角色", 400));
            boundProfileId = selected.uuid();
        }
        repository.saveDeviceCode(new DeviceCode(device.deviceCode(), device.userCode(), device.clientId(),
                device.scopes(), approve ? DeviceCode.APPROVED : DeviceCode.DENIED, userId, boundProfileId,
                device.expiresAt(), device.intervalSeconds(), device.lastPolledAt()));
        return Map.of("status", approve ? DeviceCode.APPROVED : DeviceCode.DENIED);
    }

    // ---- 会话服务器：OAuth 令牌进服 ----

    /**
     * 使用 OAuth 访问令牌加入服务器。返回 false 表示该令牌不是 OAuth 令牌（交给传统会话流程）。
     */
    public boolean join(JoinRequest request) {
        OAuthToken token = repository.findToken(request.accessToken()).orElse(null);
        if (token == null) {
            return false;
        }
        if (token.expired(System.currentTimeMillis())) {
            repository.deleteToken(token.token());
            throw new YggcAppService.YggcException("ForbiddenOperationException", "访问令牌已过期");
        }
        if (!token.hasScope(SCOPE_JOIN)) {
            throw new YggcAppService.YggcException("ForbiddenOperationException",
                    "访问令牌未获得加入服务器的授权（缺少 Yggdrasil.Server.Join）");
        }
        if (!YggcAppService.hasText(token.profileId())) {
            throw new YggcAppService.YggcException("ForbiddenOperationException",
                    "访问令牌未绑定角色（缺少 Yggdrasil.PlayerProfiles.Select）");
        }
        if (!token.profileId().equals(YggcAppService.normalizeUuid(request.selectedProfile()))) {
            throw new YggcAppService.YggcException("ForbiddenOperationException", "访问令牌与角色不匹配");
        }
        PluginSkinProfile profile = appService.findOwnedProfile(profilesForUser(token.userId()), token.profileId())
                .orElseThrow(() -> new YggcAppService.YggcException("ForbiddenOperationException", "角色不存在"));
        repository.saveJoin(new ServerJoin(
                request.serverId() + ":" + profile.uuid(),
                request.serverId(),
                profile.uuid(),
                profile.name(),
                token.token(),
                System.currentTimeMillis() + JOIN_TTL
        ));
        LOG.info("[yggc-oauth] join 成功（OAuth 令牌）：角色 " + profile.name() + "（" + profile.uuid()
                + "）加入服务器 serverId=" + request.serverId());
        return true;
    }

    // ---- 客户端管理（管理员） ----

    public Map<String, Object> listClients(String keyword, int page, int size) {
        List<Map<String, Object>> records = repository.findClients(keyword, page, size).stream()
                .map(this::clientView)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("records", records);
        body.put("total", repository.clientCount());
        return body;
    }

    public Map<String, Object> createClient(String name, List<String> redirectUris, boolean publicClient) {
        if (!YggcAppService.hasText(name)) {
            throw new IllegalArgumentException("应用名称不能为空");
        }
        String clientId = "yggc_" + cryptoService.randomToken(16);
        String secret = cryptoService.randomToken(32);
        OAuthClient client = new OAuthClient(clientId, name.trim(),
                publicClient ? null : cryptoService.sha256Hex(secret),
                sanitizeRedirectUris(redirectUris), publicClient, true, System.currentTimeMillis());
        repository.saveClient(client);
        Map<String, Object> body = clientView(client);
        if (!publicClient) {
            body.put("secret", secret);
        }
        return body;
    }

    public Map<String, Object> updateClient(String clientId, String name, List<String> redirectUris,
                                            Boolean publicClient, Boolean enabled) {
        OAuthClient existing = repository.findClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("应用不存在"));
        boolean nextPublic = publicClient == null ? existing.publicClient() : publicClient;
        boolean nextEnabled = enabled == null ? existing.enabled() : enabled;
        if (_isSharedClient(existing.id()) && (!nextPublic || !nextEnabled)) {
            throw new IllegalArgumentException("该应用正在作为共享客户端（发现文档 shared_client_id）使用，"
                    + "不能改为机密客户端或禁用；请先在插件配置里解除共享客户端绑定");
        }
        OAuthClient updated = new OAuthClient(existing.id(),
                name == null || name.isBlank() ? existing.name() : name.trim(),
                nextPublic ? null : existing.secretHash(),
                redirectUris == null ? existing.redirectUris() : sanitizeRedirectUris(redirectUris),
                nextPublic,
                nextEnabled,
                existing.createdAt());
        repository.saveClient(updated);
        return clientView(updated);
    }

    public void deleteClient(String clientId) {
        OAuthClient existing = repository.findClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("应用不存在"));
        if (_isSharedClient(existing.id())) {
            throw new IllegalArgumentException("该应用正在作为共享客户端（发现文档 shared_client_id）使用，"
                    + "删除会让所有依赖共享客户端的启动器无法登录；请先在插件配置里解除共享客户端绑定");
        }
        repository.findTokensByClient(clientId).forEach(token -> repository.deleteToken(token.token()));
        repository.findRefreshTokensByClient(clientId).forEach(token -> repository.deleteRefreshToken(token.token()));
        repository.deleteClient(clientId);
    }

    public Map<String, Object> resetClientSecret(String clientId) {
        OAuthClient existing = repository.findClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("应用不存在"));
        if (_isSharedClient(existing.id())) {
            throw new IllegalArgumentException("该应用正在作为共享客户端使用，转为机密客户端会让启动器无法登录；"
                    + "请先在插件配置里解除共享客户端绑定");
        }
        String secret = cryptoService.randomToken(32);
        repository.saveClient(new OAuthClient(existing.id(), existing.name(), cryptoService.sha256Hex(secret),
                existing.redirectUris(), false, existing.enabled(), existing.createdAt()));
        Map<String, Object> body = clientView(existing);
        body.put("secret", secret);
        return body;
    }

    /**
     * 可作为共享客户端的应用：启用中的公共客户端。
     * 设备流不需要 redirect_uri，因此回调地址留空即可；空回调同时避免共享 id 被用于授权码流。
     */
    public List<Map<String, Object>> eligibleSharedClients() {
        return _allClients().stream()
                .filter(OAuthClient::enabled)
                .filter(OAuthClient::publicClient)
                .map(client -> {
                    Map<String, Object> view = clientView(client);
                    view.put("shared", _isSharedClient(client.id()));
                    return view;
                })
                .toList();
    }

    private List<OAuthClient> _allClients() {
        List<OAuthClient> clients = new ArrayList<>();
        int page = 1;
        while (true) {
            List<OAuthClient> batch = repository.findClients(null, page, 200);
            if (batch.isEmpty()) {
                return clients;
            }
            clients.addAll(batch);
            if (batch.size() < 200) {
                return clients;
            }
            page++;
        }
    }

    private boolean _isSharedClient(String clientId) {
        return YggcAppService.hasText(clientId) && clientId.equals(settings().sharedClientId());
    }

    // ---- 令牌管理（管理员） ----

    public Map<String, Object> listTokens(int page, int size) {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> records = repository.findTokens(page, size).stream()
                .filter(token -> !token.expired(now))
                .map(this::tokenView)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("records", records);
        body.put("total", repository.tokenCount());
        return body;
    }

    public void revokeToken(String token) {
        OAuthToken existing = repository.findToken(token)
                .orElseThrow(() -> new IllegalArgumentException("令牌不存在"));
        repository.deleteToken(token);
        repository.findRefreshTokensByAccessToken(token)
                .forEach(refresh -> repository.deleteRefreshToken(refresh.token()));
    }

    // ---- 个人授权（用户） ----

    public List<Map<String, Object>> myGrants(String userId) {
        long now = System.currentTimeMillis();
        Map<String, List<OAuthToken>> byClient = new LinkedHashMap<>();
        repository.findTokensByUser(userId).stream()
                .filter(token -> !token.expired(now))
                .forEach(token -> byClient.computeIfAbsent(token.clientId(), key -> new ArrayList<>()).add(token));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<OAuthToken>> entry : byClient.entrySet()) {
            OAuthClient client = repository.findClient(entry.getKey()).orElse(null);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("clientId", entry.getKey());
            item.put("clientName", client == null ? "（应用已删除）" : client.name());
            item.put("clientEnabled", client != null && client.enabled());
            item.put("tokens", entry.getValue().stream().map(this::tokenView).toList());
            result.add(item);
        }
        return result;
    }

    public void revokeMyToken(String userId, String token) {
        OAuthToken existing = repository.findToken(token)
                .orElseThrow(() -> new IllegalArgumentException("令牌不存在"));
        if (!userId.equals(existing.userId())) {
            throw new IllegalArgumentException("只能操作自己的令牌");
        }
        repository.deleteToken(token);
        repository.findRefreshTokensByAccessToken(token)
                .forEach(refresh -> repository.deleteRefreshToken(refresh.token()));
    }

    public void revokeMyClient(String userId, String clientId) {
        repository.findTokensByUser(userId).stream()
                .filter(token -> clientId.equals(token.clientId()))
                .forEach(token -> repository.deleteToken(token.token()));
        repository.findRefreshTokensByUser(userId).stream()
                .filter(refresh -> clientId.equals(refresh.clientId()))
                .forEach(refresh -> repository.deleteRefreshToken(refresh.token()));
    }

    // ---- 统计 ----

    public Map<String, Object> stats() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clients", repository.clientCount());
        body.put("tokens", repository.tokenCount());
        body.put("sessions", repository.sessionCount());
        return body;
    }

    // ---- 内部 ----

    private YggcSettings settings() {
        return settingsService.current();
    }

    /** 发现文档对外声明的共享客户端；未配置时为空串。 */
    private String sharedClientId() {
        return settings().sharedClientId();
    }

    private OAuthClient requireClient(String clientId) {
        if (!YggcAppService.hasText(clientId)) {
            throw new OAuthException("invalid_client", "缺少 client_id", 401);
        }
        OAuthClient client = repository.findClient(clientId.trim())
                .orElseThrow(() -> new OAuthException("invalid_client", "客户端不存在", 401));
        if (!client.enabled()) {
            throw new OAuthException("invalid_client", "客户端已被禁用", 401);
        }
        return client;
    }

    private String requireRegisteredRedirectUri(OAuthClient client, String redirectUri) {
        if (!YggcAppService.hasText(redirectUri) || !client.redirectUris().contains(redirectUri)) {
            throw new OAuthException("invalid_request", "redirect_uri 未注册", 400);
        }
        return redirectUri;
    }

    private void validatePkce(Map<String, String> params) {
        String challenge = params.get("code_challenge");
        String method = params.get("code_challenge_method");
        if (!YggcAppService.hasText(challenge)) {
            return;
        }
        if (YggcAppService.hasText(method) && !"S256".equals(method)) {
            throw new OAuthException("invalid_request", "仅支持 S256 的 code_challenge_method", 400);
        }
        if (challenge.length() < 43 || challenge.length() > 128) {
            throw new OAuthException("invalid_request", "code_challenge 长度非法", 400);
        }
    }

    private void requireClientSecret(OAuthClient client, String clientSecret) {
        if (client.publicClient()) {
            return;
        }
        if (!YggcAppService.hasText(clientSecret)
                || !cryptoService.sha256Hex(clientSecret).equals(client.secretHash())) {
            throw new OAuthException("invalid_client", "client_secret 校验失败", 401);
        }
    }

    private PluginUserProfile requireUser(String userId) {
        if (!YggcAppService.hasText(userId)) {
            throw new OAuthException("invalid_request", "缺少用户身份", 400);
        }
        return context.framework().users().findById(Long.parseLong(userId.trim()))
                .orElseThrow(() -> new OAuthException("invalid_request", "用户不存在", 400));
    }

    private List<PluginSkinProfile> profilesForUser(String userId) {
        return appService.profilesForUser(userId);
    }

    private List<Map<String, Object>> profileViews(String userId) {
        return profilesForUser(userId).stream()
                .map(profile -> Map.<String, Object>of("id", profile.uuid(), "name", profile.name()))
                .toList();
    }

    private List<String> parseScopes(String scope) {
        if (!YggcAppService.hasText(scope)) {
            return List.of();
        }
        return List.of(scope.trim().split("\\s+"));
    }

    private void validateScopes(List<String> scopes) {
        if (!scopes.contains(SCOPE_OPENID)) {
            throw new OAuthException("invalid_scope", "必须包含 openid", 400);
        }
        for (String scope : scopes) {
            if (!SUPPORTED_SCOPES.contains(scope)) {
                throw new OAuthException("invalid_scope", "不支持的 scope：" + scope, 400);
            }
        }
        if (scopes.contains(SCOPE_SELECT) && scopes.contains(SCOPE_READ)) {
            throw new OAuthException("invalid_scope",
                    "不能同时申请 Yggdrasil.PlayerProfiles.Select 与 Yggdrasil.PlayerProfiles.Read", 400);
        }
        if (scopes.contains(SCOPE_JOIN) && !scopes.contains(SCOPE_SELECT)) {
            throw new OAuthException("invalid_scope",
                    "申请 Yggdrasil.Server.Join 时必须同时申请 Yggdrasil.PlayerProfiles.Select", 400);
        }
    }

    private boolean verifyPkce(String verifier, String challenge) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String computed = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            return computed.equals(challenge);
        } catch (Exception e) {
            return false;
        }
    }

    private String errorRedirectUrl(String redirectUri, String state, String error, String description) {
        StringBuilder url = new StringBuilder(redirectUri);
        url.append(redirectUri.contains("?") ? '&' : '?');
        url.append("error=").append(error);
        if (YggcAppService.hasText(description)) {
            url.append("&error_description=").append(description);
        }
        if (YggcAppService.hasText(state)) {
            url.append("&state=").append(state);
        }
        return url.toString();
    }

    private List<String> sanitizeRedirectUris(List<String> redirectUris) {
        if (redirectUris == null) {
            return List.of();
        }
        return redirectUris.stream()
                .filter(YggcAppService::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private Map<String, Object> clientView(OAuthClient client) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", client.id());
        view.put("name", client.name());
        view.put("redirectUris", client.redirectUris());
        view.put("publicClient", client.publicClient());
        view.put("enabled", client.enabled());
        view.put("createdAt", client.createdAt());
        return view;
    }

    private Map<String, Object> scopeView(String scope) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", scope);
        view.put("description", SCOPE_LABELS.getOrDefault(scope, ""));
        return view;
    }

    private Map<String, Object> tokenView(OAuthToken token) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("token", token.token());
        view.put("clientId", token.clientId());
        view.put("userId", token.userId());
        view.put("nickname", token.nickname());
        view.put("profileId", token.profileId());
        view.put("profileName", token.profileName());
        view.put("scopes", token.scopes());
        view.put("issuedAt", token.issuedAt());
        view.put("expiresAt", token.expiresAt());
        return view;
    }

    public static class OAuthException extends RuntimeException {
        private final String error;
        private final int status;

        public OAuthException(String error, String description, int status) {
            super(description);
            this.error = error;
            this.status = status;
        }

        public String error() {
            return error;
        }

        public int status() {
            return status;
        }

        public Map<String, Object> body() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", error);
            body.put("error_description", getMessage());
            return body;
        }
    }
}
