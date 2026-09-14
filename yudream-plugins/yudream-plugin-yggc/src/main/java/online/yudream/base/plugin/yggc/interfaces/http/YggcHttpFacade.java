package online.yudream.base.plugin.yggc.interfaces.http;

import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import online.yudream.base.plugin.yggc.application.service.YggcAppService;
import online.yudream.base.plugin.yggc.application.service.YggcAppService.YggcException;
import online.yudream.base.plugin.yggc.application.service.YggcOAuthService;
import online.yudream.base.plugin.yggc.application.service.YggcOAuthService.OAuthException;
import online.yudream.base.plugin.yggc.application.service.YggcSettingsService;
import online.yudream.base.plugin.yggc.application.service.YggcUnionService;
import online.yudream.base.plugin.yggc.domain.aggregate.YggcSettings;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcCryptoService;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcUnionClient;
import online.yudream.base.plugin.yggc.infrastructure.support.FormSupport;
import online.yudream.base.plugin.yggc.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.yggc.infrastructure.support.YggcUnionHostVerifier;
import online.yudream.base.plugin.yggc.interfaces.request.AuthenticateRequest;
import online.yudream.base.plugin.yggc.interfaces.request.AuthorizeDecisionRequest;
import online.yudream.base.plugin.yggc.interfaces.request.ClientSaveRequest;
import online.yudream.base.plugin.yggc.interfaces.request.DeviceDecisionRequest;
import online.yudream.base.plugin.yggc.interfaces.request.JoinRequest;
import online.yudream.base.plugin.yggc.interfaces.request.RefreshRequest;
import online.yudream.base.plugin.yggc.interfaces.request.SignoutRequest;
import online.yudream.base.plugin.yggc.interfaces.request.TextureBindRequest;
import online.yudream.base.plugin.yggc.interfaces.request.TokenRequest;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 协议端点 HTTP 门面：所有响应注入 X-Authlib-Injector-API-Location 头；
 * OAuth authorize 走 302 重定向到前端授权确认页；token 端点支持表单与 Basic 认证。
 */
public class YggcHttpFacade {

    private static final String API_LOCATION = "/api/plugins/yggc/api/yggdrasil";
    private static final String APP_WEB_URL_SETTING = "app.web-url";
    private static final String APP_BASE_URL_SETTING = "app.base-url";
    private static final String AUTHORIZE_PAGE = "/platform/plugins/yggc/authorize";
    private static final String DEVICE_PAGE = "/platform/plugins/yggc/device";

    private final YggcAppService appService;
    private final YggcOAuthService oauthService;
    private final FrameworkServices frameworkServices;
    private final YggcSettingsService settingsService;
    private final YggcUnionClient unionClient;
    private final YggcCryptoService cryptoService;
    private final YggcUnionService unionService;
    private final YggcUnionHostVerifier unionHostVerifier;

    public YggcHttpFacade(YggcAppService appService, YggcOAuthService oauthService,
                          FrameworkServices frameworkServices, YggcSettingsService settingsService,
                          YggcUnionClient unionClient, YggcCryptoService cryptoService,
                          YggcUnionService unionService, YggcUnionHostVerifier unionHostVerifier) {
        this.appService = appService;
        this.oauthService = oauthService;
        this.frameworkServices = frameworkServices;
        this.settingsService = settingsService;
        this.unionClient = unionClient;
        this.cryptoService = cryptoService;
        this.unionService = unionService;
        this.unionHostVerifier = unionHostVerifier;
    }

    // ---- 协议元数据 ----

    public PluginHttpResponse metadata(PluginHttpRequest request) {
        return ali(request, PluginHttpResponse.rawJson(200,
                appService.metadata(apiRoot(request), textureBaseUrl(request))));
    }

    public PluginHttpResponse discovery(PluginHttpRequest request) {
        return ali(request, PluginHttpResponse.rawJson(200,
                oauthService.discovery(appService.oauthIssuer(apiRoot(request)))));
    }

    public PluginHttpResponse jwks(PluginHttpRequest request) {
        return ali(request, PluginHttpResponse.rawJson(200, oauthService.jwks()));
    }

    // ---- 传统 Yggdrasil：authserver ----

    /** ygg_disable_authserver：关闭后启动器只能走 Yggdrasil Connect（OAuth）登录。 */
    private PluginHttpResponse authserverGuard(PluginHttpRequest request) {
        if (settingsService.current().disableAuthserver()) {
            return error(request, 403, (YggcException) appService.authError("ForbiddenOperationException",
                    "Auth Server 已被禁用，请使用 Yggdrasil Connect 登录"));
        }
        return null;
    }

    public PluginHttpResponse authenticate(PluginHttpRequest request) {
        PluginHttpResponse blocked = authserverGuard(request);
        if (blocked != null) {
            return blocked;
        }
        return runJson(request, () -> appService.authenticate(
                JsonSupport.read(request.body(), AuthenticateRequest.class)));
    }

    public PluginHttpResponse refresh(PluginHttpRequest request) {
        PluginHttpResponse blocked = authserverGuard(request);
        if (blocked != null) {
            return blocked;
        }
        return runJson(request, () -> appService.refresh(
                JsonSupport.read(request.body(), RefreshRequest.class)));
    }

    public PluginHttpResponse validate(PluginHttpRequest request) {
        PluginHttpResponse blocked = authserverGuard(request);
        if (blocked != null) {
            return blocked;
        }
        return runNoContent(request, () -> appService.validate(
                JsonSupport.read(request.body(), TokenRequest.class)));
    }

    public PluginHttpResponse invalidate(PluginHttpRequest request) {
        PluginHttpResponse blocked = authserverGuard(request);
        if (blocked != null) {
            return blocked;
        }
        return runNoContent(request, () -> appService.invalidate(
                JsonSupport.read(request.body(), TokenRequest.class)));
    }

    public PluginHttpResponse signout(PluginHttpRequest request) {
        PluginHttpResponse blocked = authserverGuard(request);
        if (blocked != null) {
            return blocked;
        }
        return runNoContent(request, () -> appService.signout(
                JsonSupport.read(request.body(), SignoutRequest.class)));
    }

    // ---- 传统 Yggdrasil：sessionserver / profiles / 材质 ----

    public PluginHttpResponse join(PluginHttpRequest request) {
        JoinRequest joinRequest = JsonSupport.read(request.body(), JoinRequest.class);
        // OAuth 令牌优先：Yggdrasil Connect 启动器持有的是 OAuth 访问令牌
        if (oauthService.join(joinRequest)) {
            return ali(request, PluginHttpResponse.noContent());
        }
        return runNoContent(request, () -> appService.join(joinRequest));
    }

    public PluginHttpResponse hasJoined(PluginHttpRequest request) {
        try {
            String username = firstQuery(request, "username");
            String serverId = firstQuery(request, "serverId");
            return appService.hasJoined(username, serverId, textureBaseUrl(request))
                    .map(body -> ali(request, PluginHttpResponse.rawJson(200, body)))
                    .orElseGet(() -> ali(request, PluginHttpResponse.noContent()));
        } catch (YggcException e) {
            return error(request, 403, e);
        }
    }

    public PluginHttpResponse profile(PluginHttpRequest request) {
        return runJson(request, () -> appService.profile(
                lastPathSegment(request.path()), textureBaseUrl(request), unsigned(request)));
    }

    public PluginHttpResponse profiles(PluginHttpRequest request) {
        return runJson(request, () -> appService.profiles(JsonSupport.readStringList(request.body())));
    }

    /** 按角色名查询单个角色（GET /api/users/profiles/minecraft/{username} 及 minecraftservices 别名）。 */
    public PluginHttpResponse profileByName(PluginHttpRequest request) {
        return runJson(request, () -> appService.profileByName(
                lastPathSegment(request.path()), textureBaseUrl(request), unsigned(request)));
    }

    // ---- Restore API（ygg_restore_api 开关）----

    public PluginHttpResponse restoreStatus(PluginHttpRequest request) {
        if (!settingsService.current().restoreApi()) {
            return error(request, 403, (YggcException) appService.authError("ForbiddenOperationException",
                    "Restore API 未启用"));
        }
        return ali(request, PluginHttpResponse.rawJson(200, Map.of("status", "success")));
    }

    public PluginHttpResponse restore(PluginHttpRequest request) {
        return runJson(request, () -> appService.restore(JsonSupport.readMap(request.body())));
    }

    public PluginHttpResponse setTexture(PluginHttpRequest request) {
        return runNoContent(request, () -> appService.setTexture(
                bearerToken(request),
                pathSegment(request.path(), 3),
                pathSegment(request.path(), 4),
                JsonSupport.read(request.body(), TextureBindRequest.class)
        ));
    }

    public PluginHttpResponse clearTexture(PluginHttpRequest request) {
        return runNoContent(request, () -> appService.clearTexture(
                bearerToken(request),
                pathSegment(request.path(), 3),
                pathSegment(request.path(), 4)
        ));
    }

    // ---- Yggdrasil Connect：OAuth 端点 ----

    /**
     * 授权端点：校验通过后 302 到前端授权确认页（宿主 SPA 路由守卫负责登录）。
     * 参数不合法且 redirect_uri 可信时回跳错误；否则返回 400 JSON。
     */
    public PluginHttpResponse authorize(PluginHttpRequest request) {
        Map<String, String> params = queryParams(request);
        try {
            // 先做参数校验（客户端、redirect_uri、scope、PKCE），失败按 OAuth 规范处理
            oauthService.authorizationContext(requireUserId(request), params);
        } catch (OAuthException e) {
            String redirectUrl = oauthService.errorRedirect(params, e.error(), e.getMessage());
            if (redirectUrl != null) {
                return redirect(redirectUrl);
            }
            return ali(request, PluginHttpResponse.rawJson(400, e.body()));
        }
        StringBuilder target = new StringBuilder(origin(request))
                .append(forwardedPrefix(request))
                .append(AUTHORIZE_PAGE);
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }
        if (query.length() > 0) {
            target.append('?').append(query);
        }
        return redirect(target.toString());
    }

    public PluginHttpResponse token(PluginHttpRequest request) {
        Map<String, String> form = FormSupport.parse(request.body());
        String[] basic = basicCredentials(request);
        try {
            return ali(request, PluginHttpResponse.rawJson(200, oauthService.token(
                    form, basic[0], basic[1], appService.oauthIssuer(apiRoot(request)))));
        } catch (OAuthException e) {
            return ali(request, PluginHttpResponse.rawJson(e.status(), e.body()));
        }
    }

    public PluginHttpResponse startDevice(PluginHttpRequest request) {
        try {
            return ali(request, PluginHttpResponse.rawJson(200,
                    oauthService.startDevice(FormSupport.parse(request.body()), origin(request))));
        } catch (OAuthException e) {
            return ali(request, PluginHttpResponse.rawJson(e.status(), e.body()));
        }
    }

    public PluginHttpResponse userInfo(PluginHttpRequest request) {
        try {
            return ali(request, PluginHttpResponse.rawJson(200,
                    oauthService.userInfo(bearerToken(request))));
        } catch (OAuthException e) {
            return ali(request, PluginHttpResponse.rawJson(e.status(), e.body()));
        }
    }

    // ---- 管理端 ----

    public PluginHttpResponse status(PluginHttpRequest request) {
        Map<String, Object> body = (Map<String, Object>) appService.status(apiRoot(request), textureBaseUrl(request));
        body.put("stats", oauthService.stats());
        return ali(request, PluginHttpResponse.ok(body));
    }

    public PluginHttpResponse listClients(PluginHttpRequest request) {
        return PluginHttpResponse.ok(oauthService.listClients(
                firstQuery(request, "keyword"),
                intQuery(request, "page", 1),
                intQuery(request, "size", 10)));
    }

    public PluginHttpResponse createClient(PluginHttpRequest request) {
        ClientSaveRequest save = JsonSupport.read(request.body(), ClientSaveRequest.class);
        return PluginHttpResponse.ok(oauthService.createClient(
                save.name(), save.redirectUris(),
                save.publicClient() != null && save.publicClient()));
    }

    public PluginHttpResponse updateClient(PluginHttpRequest request) {
        ClientSaveRequest save = JsonSupport.read(request.body(), ClientSaveRequest.class);
        return PluginHttpResponse.ok(oauthService.updateClient(
                lastPathSegment(request.path()), save.name(), save.redirectUris(),
                save.publicClient(), save.enabled()));
    }

    public PluginHttpResponse deleteClient(PluginHttpRequest request) {
        oauthService.deleteClient(lastPathSegment(request.path()));
        return PluginHttpResponse.ok(Map.of("deleted", true));
    }

    public PluginHttpResponse resetClientSecret(PluginHttpRequest request) {
        return PluginHttpResponse.ok(oauthService.resetClientSecret(lastPathSegment(request.path())));
    }

    public PluginHttpResponse listTokens(PluginHttpRequest request) {
        return PluginHttpResponse.ok(oauthService.listTokens(
                intQuery(request, "page", 1),
                intQuery(request, "size", 10)));
    }

    public PluginHttpResponse revokeToken(PluginHttpRequest request) {
        oauthService.revokeToken(lastPathSegment(request.path()));
        return PluginHttpResponse.ok(Map.of("revoked", true));
    }

    // ---- 管理端：插件配置（对应原 yggdrasil-connect 的 Option 配置）----

    public PluginHttpResponse getConfig(PluginHttpRequest request) {
        Map<String, Object> body = new LinkedHashMap<>(settingsService.current().toDocument());
        Map<String, Object> keyPairs = new LinkedHashMap<>();
        keyPairs.put("texture", cryptoService.keyPairInfo("texture"));
        keyPairs.put("token", cryptoService.keyPairInfo("token"));
        keyPairs.put("union-oauth2", cryptoService.keyPairInfo("union-oauth2"));
        body.put("keyPairs", keyPairs);
        body.put("union", unionService.unionLocalState());
        return PluginHttpResponse.ok(body);
    }

    public PluginHttpResponse updateConfig(PluginHttpRequest request) {
        try {
            YggcSettings saved = settingsService.save(
                    YggcSettings.from(JsonSupport.readMap(request.body())));
            return PluginHttpResponse.ok(saved.toDocument());
        } catch (IllegalArgumentException e) {
            return PluginHttpResponse.json(400, Map.of("message", e.getMessage()));
        }
    }

    public PluginHttpResponse resetConfig(PluginHttpRequest request) {
        return PluginHttpResponse.ok(settingsService.resetToDefaults().toDocument());
    }

    public PluginHttpResponse regenerateKeyPair(PluginHttpRequest request) {
        try {
            String usage = lastPathSegment(request.path());
            if ("texture".equals(usage) && unionService.unionPrivateKeySynced()) {
                return PluginHttpResponse.json(400, Map.of("message",
                        "材质签名密钥当前由 Union 主服务器分发，请使用「从上游同步签名私钥」更新；本地重新生成会破坏跨站签名兼容"));
            }
            return PluginHttpResponse.ok(cryptoService.regenerateKeyPair(usage));
        } catch (IllegalArgumentException e) {
            return PluginHttpResponse.json(400, Map.of("message", e.getMessage()));
        }
    }

    /** Union 主服务器自助诊断：验证 union_api_root / union_member_key 是否配置正确。 */
    public PluginHttpResponse diagnoseUnion(PluginHttpRequest request) {
        YggcSettings settings = settingsService.current();
        return PluginHttpResponse.ok(unionClient.diagnose(settings.unionApiRoot(), settings.unionMemberKey()));
    }

    // ---- 用户端：授权确认 / 设备确认 / 个人授权 ----

    public PluginHttpResponse authorizeContext(PluginHttpRequest request) {
        return userJson(request, userId -> oauthService.authorizationContext(userId, queryParams(request)));
    }

    public PluginHttpResponse authorizeDecision(PluginHttpRequest request) {
        AuthorizeDecisionRequest decision = JsonSupport.read(request.body(), AuthorizeDecisionRequest.class);
        return userJson(request, userId -> oauthService.decide(userId, queryParams(request),
                decision.approve() == null || decision.approve(), decision.profileId()));
    }

    public PluginHttpResponse deviceContext(PluginHttpRequest request) {
        return userJson(request, userId -> oauthService.deviceContextFor(userId,
                firstQuery(request, "user_code")));
    }

    public PluginHttpResponse deviceDecision(PluginHttpRequest request) {
        DeviceDecisionRequest decision = JsonSupport.read(request.body(), DeviceDecisionRequest.class);
        return userJson(request, userId -> oauthService.deviceDecision(userId,
                decision.userCode() == null ? firstQuery(request, "user_code") : decision.userCode(),
                decision.approve() == null || decision.approve(), decision.profileId()));
    }

    public PluginHttpResponse myGrants(PluginHttpRequest request) {
        return userJson(request, oauthService::myGrants);
    }

    public PluginHttpResponse revokeMyToken(PluginHttpRequest request) {
        return userJson(request, userId -> {
            oauthService.revokeMyToken(userId, lastPathSegment(request.path()));
            return Map.of("revoked", true);
        });
    }

    public PluginHttpResponse revokeMyClient(PluginHttpRequest request) {
        return userJson(request, userId -> {
            oauthService.revokeMyClient(userId, lastPathSegment(request.path()));
            return Map.of("revoked", true);
        });
    }

    // ---- 管理端：Union 联邦 ----

    /** Union 状态总览：本地数据版本 + 上游公告。 */
    public PluginHttpResponse unionStatus(PluginHttpRequest request) {
        return PluginHttpResponse.ok(unionService.unionStatus());
    }

    /** 从 Union 主服务器拉取签名私钥（用户信息签名密钥由主服务器生成并分发）。 */
    public PluginHttpResponse syncUnionPrivateKey(PluginHttpRequest request) {
        return unionAction(unionService::syncPrivateKey);
    }

    public PluginHttpResponse syncUnionServerList(PluginHttpRequest request) {
        return unionAction(unionService::syncServerList);
    }

    public PluginHttpResponse syncUnionProfiles(PluginHttpRequest request) {
        return unionAction(unionService::triggerSync);
    }

    private PluginHttpResponse unionAction(java.util.function.Supplier<Object> action) {
        try {
            return PluginHttpResponse.ok(action.get());
        } catch (IllegalArgumentException e) {
            return PluginHttpResponse.json(400, Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return PluginHttpResponse.json(500, Map.of("message", e.getMessage()));
        }
    }

    // ---- 管理端：MUA 黑名单代理 ----

    /** 上游黑名单接口要求 X-Union-Member-Key，未配置时直接给出可操作的错误提示。 */
    private PluginHttpResponse blacklistGuard() {
        if (!unionService.memberKeyConfigured()) {
            // 必须用 rawJson：包一层信封后宿主会把 message 塞进 data，前端只能读到「操作成功」，看不到这句提示。
            return PluginHttpResponse.rawJson(400, Map.of(
                    "message", "未配置 MUA Member Key，无法代理黑名单接口；请先在「插件配置」中填写并保存 MUA Member Key"));
        }
        return null;
    }

    public PluginHttpResponse blacklistQuery(PluginHttpRequest request) {
        PluginHttpResponse guard = blacklistGuard();
        if (guard != null) {
            return guard;
        }
        String query = rawQuery(request);
        YggcUnionClient.UnionResult result = unionService.blacklistQuery(query);
        return proxyJson(result);
    }

    public PluginHttpResponse blacklistCreate(PluginHttpRequest request) {
        PluginHttpResponse guard = blacklistGuard();
        if (guard != null) {
            return guard;
        }
        YggcUnionClient.UnionResult result = unionService.blacklistCreate(JsonSupport.readMap(request.body()));
        return proxyJson(result);
    }

    public PluginHttpResponse blacklistInvalidate(PluginHttpRequest request) {
        PluginHttpResponse guard = blacklistGuard();
        if (guard != null) {
            return guard;
        }
        YggcUnionClient.UnionResult result = unionService.blacklistInvalidate(lastPathSegment(request.path()));
        return proxyJson(result);
    }

    public PluginHttpResponse blacklistDelete(PluginHttpRequest request) {
        PluginHttpResponse guard = blacklistGuard();
        if (guard != null) {
            return guard;
        }
        YggcUnionClient.UnionResult result = unionService.blacklistDelete(lastPathSegment(request.path()));
        return proxyJson(result);
    }

    /**
     * 把上游结果透传给前端。
     *
     * <p>成功时必须走 {@code ok}（包一层宿主信封）：宿主前端只认 {@code code == 200}，直接把上游的
     * Laravel 分页 JSON 原样返回（rawJson）会被当成失败并弹「请求失败」。
     * <p>失败时反过来用 {@code rawJson}：4xx/5xx 走 axios 的错误拦截器，它只读顶层 {@code message}，
     * 包一层信封会把真实原因盖成「操作成功」。
     */
    private PluginHttpResponse proxyJson(YggcUnionClient.UnionResult result) {
        if (result.ok()) {
            Object body = result.body() == null || result.body().isBlank()
                    ? Map.of("status", 200) : parseOrRaw(result.body());
            return PluginHttpResponse.ok(body);
        }
        return PluginHttpResponse.rawJson(upstreamStatus(result), Map.of("message", upstreamMessage(result)));
    }

    private static int upstreamStatus(YggcUnionClient.UnionResult result) {
        return result.status() == 0 ? 502 : result.status();
    }

    /** 上游失败原因 → 中文提示；无法识别时保留原始文案，便于排查。 */
    private static String upstreamMessage(YggcUnionClient.UnionResult result) {
        if (result.status() == 0) {
            // 客户端已经给出「无法连接 Union 主服务器：…」这类说明，这里原样使用，不再二次加前缀。
            String reason = result.body() == null ? "" : result.body().trim();
            return reason.isEmpty() ? "无法连接 MUA 主服务器：网络不可达" : reason;
        }
        Map<String, Object> json = result.json();
        String message = json == null ? "" : String.valueOf(json.getOrDefault("message", "")).trim();
        if (isMemberKeyFailure(result.status(), message)) {
            return "MUA Member Key 无效或已过期，请在「插件配置」中更新后重试"
                    + (message.isEmpty() ? "" : "（上游：" + message + "）");
        }
        if (result.status() == 422) {
            String invalid = validationMessage(json);
            return invalid.isEmpty() ? "主服务器校验未通过：" + fallbackMessage(result, message)
                    : "主服务器校验未通过：" + invalid;
        }
        if (result.status() == 404) {
            return "主服务器上没有这条黑名单记录（可能已被删除或已失效）";
        }
        return "MUA 主服务器返回 HTTP " + result.status() + "：" + fallbackMessage(result, message);
    }

    private static boolean isMemberKeyFailure(int status, String message) {
        String text = message.toLowerCase(Locale.ROOT);
        return status == 401 || status == 403
                || text.contains("key_invalid") || text.contains("key_missing")
                || text.contains("member_key") || text.contains("memberkey");
    }

    /** Laravel 422 的 {@code errors} 字段 → 一句话提示（每个字段取第一条）。 */
    private static String validationMessage(Map<String, Object> json) {
        Object errors = json == null ? null : json.get("errors");
        if (!(errors instanceof Map<?, ?> map) || map.isEmpty()) {
            return "";
        }
        List<String> messages = new ArrayList<>();
        map.forEach((field, value) -> {
            String text = "";
            if (value instanceof List<?> rows && !rows.isEmpty()) {
                text = String.valueOf(rows.get(0));
            } else if (value != null) {
                text = String.valueOf(value);
            }
            text = text.trim();
            if (!text.isEmpty() && !messages.contains(text)) {
                messages.add(text);
            }
        });
        return String.join("；", messages);
    }

    /** 上游文案缺失或只是翻译键时，回落到原始响应片段。 */
    private static String fallbackMessage(YggcUnionClient.UnionResult result, String message) {
        if (!message.isEmpty() && !message.contains("::")) {
            return message;
        }
        String body = result.body() == null ? "" : result.body().trim().replaceAll("\\s+", " ");
        if (body.length() > 200) {
            body = body.substring(0, 200) + "…";
        }
        return body.isEmpty() ? "无响应内容" : body;
    }

    private Object parseOrRaw(String body) {
        try {
            return JsonSupport.readMap(body);
        } catch (Exception e) {
            return Map.of("raw", body);
        }
    }

    /** 原样透传查询串（黑名单分页参数等）。 */
    private String rawQuery(PluginHttpRequest request) {
        Map<String, List<String>> query = request.query();
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : query.entrySet()) {
            for (String value : entry.getValue() == null ? List.<String>of() : entry.getValue()) {
                if (builder.length() > 0) {
                    builder.append('&');
                }
                builder.append(urlEncode(entry.getKey())).append('=').append(urlEncode(value));
            }
        }
        return builder.toString();
    }

    // ---- 用户端：Union 跨站角色 ----

    public PluginHttpResponse unionOverview(PluginHttpRequest request) {
        return userJson(request, unionService::overview);
    }

    public PluginHttpResponse unionBind(PluginHttpRequest request) {
        Map<String, Object> body = JsonSupport.readMap(request.body());
        return userJson(request, userId -> unionService.bindProfile(text(body, "uuid")));
    }

    public PluginHttpResponse unionBindTo(PluginHttpRequest request) {
        Map<String, Object> body = JsonSupport.readMap(request.body());
        return userJson(request, userId -> unionService.bindToProfile(text(body, "uuid"), text(body, "token")));
    }

    public PluginHttpResponse unionUnbind(PluginHttpRequest request) {
        Map<String, Object> body = JsonSupport.readMap(request.body());
        return userJson(request, userId -> unionService.unbindProfile(text(body, "uuid")));
    }

    public PluginHttpResponse unionRemapUuid(PluginHttpRequest request) {
        Map<String, Object> body = JsonSupport.readMap(request.body());
        return userJson(request, userId -> unionService.requestRemapUuid(text(body, "me"), text(body, "target")));
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    // ---- 成员回调（Union 主服务器 → 本站，X-Message-Signature 主机验证）----

    /** 主机验证失败统一 403；验证通过后执行动作。 */
    private PluginHttpResponse memberAction(PluginHttpRequest request, MemberAction action) {
        YggcSettings settings = settingsService.current();
        try {
            unionHostVerifier.verify(settings.unionApiRoot(), request.headers(), request.body());
        } catch (IllegalArgumentException e) {
            return PluginHttpResponse.rawJson(403, Map.of("error", "ForbiddenOperationException",
                    "errorMessage", e.getMessage()));
        }
        try {
            return PluginHttpResponse.rawJson(200, action.run(request));
        } catch (IllegalArgumentException e) {
            return PluginHttpResponse.rawJson(400, Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return PluginHttpResponse.rawJson(500, Map.of("message", e.getMessage()));
        }
    }

    /** GET /union/member：本站数据版本与可用特性（无主机验证，公开公告）。 */
    public PluginHttpResponse unionMemberHello(PluginHttpRequest request) {
        return PluginHttpResponse.rawJson(200, unionService.memberHello());
    }

    public PluginHttpResponse unionMemberUpdateList(PluginHttpRequest request) {
        return memberAction(request, ignored -> unionService.syncServerList());
    }

    public PluginHttpResponse unionMemberUpdatePrivateKey(PluginHttpRequest request) {
        return memberAction(request, ignored -> unionService.syncPrivateKey());
    }

    public PluginHttpResponse unionMemberUpdateBackendKey(PluginHttpRequest request) {
        return memberAction(request, ignored -> {
            unionService.memberUpdateBackendKey(text(JsonSupport.readMap(request.body()), "key"));
            return Map.of("status", "success");
        });
    }

    public PluginHttpResponse unionMemberSync(PluginHttpRequest request) {
        return memberAction(request, ignored -> unionService.triggerSync());
    }

    public PluginHttpResponse unionMemberRemapUuid(PluginHttpRequest request) {
        return memberAction(request, ignored ->
                unionService.memberRemapUuid(JsonSupport.readMap(request.body())));
    }

    /** GET /union/member/queryemail?username=x：Union 黑名单核验（204 = 无此角色）。 */
    public PluginHttpResponse unionMemberQueryEmail(PluginHttpRequest request) {
        YggcSettings settings = settingsService.current();
        try {
            unionHostVerifier.verify(settings.unionApiRoot(), request.headers(), request.body());
        } catch (IllegalArgumentException e) {
            return PluginHttpResponse.rawJson(403, Map.of("error", "ForbiddenOperationException",
                    "errorMessage", e.getMessage()));
        }
        return queryEmailBody(firstQuery(request, "username"));
    }

    private PluginHttpResponse queryEmailBody(String username) {
        return unionService.memberQueryEmail(username)
                .map(email -> PluginHttpResponse.rawJson(200, Map.of("email", email)))
                .orElseGet(PluginHttpResponse::noContent);
    }

    /** POST /union/member/diagnose：回显 nonce 与时间戳。 */
    public PluginHttpResponse unionMemberDiagnose(PluginHttpRequest request) {
        return memberAction(request, ignored -> {
            Map<String, Object> echo = new LinkedHashMap<>();
            echo.put("nonce", text(JsonSupport.readMap(request.body()), "nonce"));
            echo.put("timestamp", System.currentTimeMillis() / 1000.0);
            return echo;
        });
    }

    public PluginHttpResponse unionMemberUpdatePlugin(PluginHttpRequest request) {
        return memberAction(request, ignored ->
                unionService.memberUpdatePlugin(text(JsonSupport.readMap(request.body()), "url")));
    }

    // ---- Union OAuth2（主服务器经本站登录，需用户会话）----

    /** GET /union/member/oauth2/：本站 Union OAuth2 签名公钥。 */
    public PluginHttpResponse unionOauth2PublicKey(PluginHttpRequest request) {
        if (!settingsService.current().unionEnableOauth2()) {
            return PluginHttpResponse.rawJson(403, Map.of("message", "Union OAuth2 未启用"));
        }
        return PluginHttpResponse.rawJson(200, Map.of("signaturePublicKey",
                unionService.unionOauth2SigPublicKeyPem()));
    }

    /** GET /union/member/oauth2/grant：签发 userInfoToken 并回跳 Union 主服务器。 */
    public PluginHttpResponse unionOauth2Grant(PluginHttpRequest request) {
        YggcSettings settings = settingsService.current();
        if (!settings.unionEnableOauth2()) {
            return PluginHttpResponse.rawJson(403, Map.of("error", "ForbiddenOperationException",
                    "errorMessage", "Union OAuth2 未启用"));
        }
        PluginPrincipal principal = request.principal();
        if (principal == null || principal.userId() == null) {
            return redirect(origin(request) + "/login");
        }
        var user = frameworkServices.users().findById(principal.userId());
        if (user.isEmpty()) {
            return PluginHttpResponse.rawJson(403, Map.of("message", "用户不存在"));
        }
        String userInfoToken;
        try {
            userInfoToken = unionService.buildUserInfoToken(user.get());
        } catch (IllegalArgumentException e) {
            return PluginHttpResponse.rawJson(502, Map.of("message", e.getMessage()));
        }
        StringBuilder target = new StringBuilder(settings.unionApiRoot()).append("/oauth2/continue?")
                .append("userInfoToken=").append(urlEncode(userInfoToken));
        queryParams(request).forEach((key, value) -> target.append('&').append(urlEncode(key))
                .append('=').append(urlEncode(value)));
        return redirect(target.toString());
    }

    // ---- 内部：响应构造 ----

    private PluginHttpResponse runJson(PluginHttpRequest request, JsonAction action) {
        try {
            return ali(request, PluginHttpResponse.rawJson(200, action.run()));
        } catch (YggcException e) {
            return error(request, 403, e);
        } catch (OAuthException e) {
            return ali(request, PluginHttpResponse.rawJson(e.status(), e.body()));
        } catch (RuntimeException e) {
            return error(request, 400, new YggcException("IllegalArgumentException", e.getMessage()));
        }
    }

    private PluginHttpResponse runNoContent(PluginHttpRequest request, VoidAction action) {
        try {
            action.run();
            return ali(request, PluginHttpResponse.noContent());
        } catch (YggcException e) {
            return error(request, 403, e);
        } catch (OAuthException e) {
            return ali(request, PluginHttpResponse.rawJson(e.status(), e.body()));
        } catch (RuntimeException e) {
            return error(request, 400, new YggcException("IllegalArgumentException", e.getMessage()));
        }
    }

    private PluginHttpResponse userJson(PluginHttpRequest request, UserAction action) {
        String userId;
        try {
            userId = requireUserId(request);
        } catch (UnauthorizedException e) {
            return PluginHttpResponse.json(401, Map.of("message", "未登录"));
        }
        try {
            return PluginHttpResponse.ok(action.run(userId));
        } catch (UnauthorizedException e) {
            return PluginHttpResponse.json(401, Map.of("message", "未登录"));
        } catch (OAuthException e) {
            return PluginHttpResponse.json(e.status(), e.body());
        } catch (IllegalArgumentException e) {
            return PluginHttpResponse.json(400, Map.of("message", e.getMessage()));
        }
    }

    private PluginHttpResponse error(PluginHttpRequest request, int status, YggcException exception) {
        return ali(request, PluginHttpResponse.rawJson(status, appService.errorBody(exception)));
    }

    private PluginHttpResponse redirect(String location) {
        return new PluginHttpResponse(302, Map.of("Location", location), null, "");
    }

    private PluginHttpResponse ali(PluginHttpRequest request, PluginHttpResponse response) {
        // ygg_enable_ali：关闭后不注入 X-Authlib-Injector-API-Location 响应头
        if (!settingsService.current().enableAli()) {
            return response;
        }
        Map<String, String> headers = new LinkedHashMap<>(response.headers());
        headers.put("X-Authlib-Injector-API-Location", externalApiLocation(request));
        return new PluginHttpResponse(response.status(), headers, response.contentType(), response.body(), response.wrapped());
    }

    // ---- 内部：地址解析 ----

    private String apiRoot(PluginHttpRequest request) {
        return origin(request) + externalApiLocation(request);
    }

    private String textureBaseUrl(PluginHttpRequest request) {
        return origin(request) + forwardedPrefix(request) + "/api/plugins/yudream-skin/textures";
    }

    private String origin(PluginHttpRequest request) {
        Optional<String> configuredOrigin = configuredOrigin();
        if (configuredOrigin.isPresent()) {
            return configuredOrigin.get();
        }
        String proto = firstForwardedValue(header(request, "x-forwarded-proto"));
        String host = header(request, "x-forwarded-host");
        if (host == null) {
            host = header(request, "host");
        }
        host = firstForwardedValue(host);
        if (proto == null) {
            proto = localHost(host) ? "http" : "https";
        }
        return proto + "://" + (host == null ? "localhost:8080" : host);
    }

    private Optional<String> configuredOrigin() {
        return frameworkServices.setting(APP_WEB_URL_SETTING)
                .or(() -> frameworkServices.setting(APP_BASE_URL_SETTING))
                .flatMap(this::originOf);
    }

    private Optional<String> originOf(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return Optional.empty();
            }
            String origin = uri.getScheme() + "://" + uri.getHost();
            if (uri.getPort() >= 0) {
                origin += ":" + uri.getPort();
            }
            return Optional.of(origin);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String externalApiLocation(PluginHttpRequest request) {
        return forwardedPrefix(request) + API_LOCATION;
    }

    private String forwardedPrefix(PluginHttpRequest request) {
        String value = firstForwardedValue(header(request, "x-forwarded-prefix"));
        if (value == null || !value.startsWith("/") || value.contains("..") || value.contains("\\")) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean localHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String value = host.toLowerCase(Locale.ROOT);
        int portIndex = value.indexOf(':');
        if (portIndex > -1) {
            value = value.substring(0, portIndex);
        }
        return "localhost".equals(value) || "127.0.0.1".equals(value) || "::1".equals(value);
    }

    // ---- 内部：请求解析 ----

    private String requireUserId(PluginHttpRequest request) {
        PluginPrincipal principal = request.principal();
        if (principal == null || principal.userId() == null) {
            throw new UnauthorizedException();
        }
        return String.valueOf(principal.userId());
    }

    private String[] basicCredentials(PluginHttpRequest request) {
        String authorization = header(request, "authorization");
        if (authorization == null || !authorization.toLowerCase(Locale.ROOT).startsWith("basic ")) {
            return new String[]{null, null};
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(
                    authorization.substring("Basic ".length()).trim()), StandardCharsets.UTF_8);
            int index = decoded.indexOf(':');
            return index > -1
                    ? new String[]{decoded.substring(0, index), decoded.substring(index + 1)}
                    : new String[]{decoded, null};
        } catch (IllegalArgumentException e) {
            return new String[]{null, null};
        }
    }

    private String bearerToken(PluginHttpRequest request) {
        String authorization = header(request, "authorization");
        if (authorization == null || !authorization.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            throw new OAuthException("invalid_request", "缺少 Bearer 访问令牌", 401);
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private boolean unsigned(PluginHttpRequest request) {
        return !"false".equalsIgnoreCase(firstQuery(request, "unsigned"));
    }

    private Map<String, String> queryParams(PluginHttpRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        if (request.query() == null) {
            return params;
        }
        for (Map.Entry<String, List<String>> entry : request.query().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                params.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return params;
    }

    private String firstQuery(PluginHttpRequest request, String key) {
        List<String> values = request.query() == null ? null : request.query().get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private int intQuery(PluginHttpRequest request, String key, int fallback) {
        String value = firstQuery(request, key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String header(PluginHttpRequest request, String name) {
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    private String firstForwardedValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.split(",")[0].trim();
    }

    private String lastPathSegment(String path) {
        String[] segments = trim(path).split("/");
        return segments.length == 0 ? "" : decode(segments[segments.length - 1]);
    }

    private String pathSegment(String path, int index) {
        String[] segments = trim(path).split("/");
        return index >= 0 && index < segments.length ? decode(segments[index]) : null;
    }

    private String trim(String path) {
        String value = path == null ? "" : path;
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** 未登录：交给控制器层转换为 401。 */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException() {
            super("未登录");
        }
    }

    private interface JsonAction {
        Object run();
    }

    private interface VoidAction {
        void run();
    }

    private interface UserAction {
        Object run(String userId);
    }

    private interface MemberAction {
        Object run(PluginHttpRequest request);
    }
}
