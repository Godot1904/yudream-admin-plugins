package online.yudream.base.plugin.yggc.application.service;

import online.yudream.base.plugin.skin.api.PluginSkinProfile;
import online.yudream.base.plugin.skin.api.PluginSkinService;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.yggc.domain.aggregate.YggcSettings;
import online.yudream.base.plugin.yggc.domain.valobj.KeyMaterial;
import online.yudream.base.plugin.yggc.infrastructure.repository.YggcRepository;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcCryptoService;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcUnionClient;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcUnionClient.UnionResult;
import online.yudream.base.plugin.yggc.infrastructure.support.JsonSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Union 联邦协议应用服务：成员站与 Union 主服务器的全部交互。
 * - 上游数据获取：签名私钥（由主服务器分发）、皮肤站列表、跨站角色数据、黑名单、安全等级；
 * - 成员回调（由 UnionService 承接业务，HTTP 层负责主机签名验证）；
 * - 角色推送（POST /profile*、POST /sync）见 {@link YggcProfileSyncService}。
 */
public class YggcUnionService {

    private static final String SKIN_PLUGIN_CODE = "yudream-skin";

    private final PluginContext context;
    private final YggcRepository repository;
    private final YggcSettingsService settingsService;
    private final YggcCryptoService cryptoService;
    private final YggcUnionClient unionClient;

    public YggcUnionService(PluginContext context, YggcRepository repository,
                            YggcSettingsService settingsService, YggcCryptoService cryptoService,
                            YggcUnionClient unionClient) {
        this.context = context;
        this.repository = repository;
        this.settingsService = settingsService;
        this.cryptoService = cryptoService;
        this.unionClient = unionClient;
    }

    private YggcSettings settings() {
        return settingsService.current();
    }

    private String apiRoot() {
        return settings().unionApiRoot();
    }

    private String memberKey() {
        return settings().unionMemberKey();
    }

    private UnionResult requireOk(UnionResult result, String action) {
        if (!result.ok()) {
            throw new IllegalArgumentException(action + " 失败：" + YggcUnionClient.failureMessage(result));
        }
        return result;
    }

    private static String snippet(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300) + "...";
    }

    // ---- 管理端：上游同步 ----

    /** 从上游拉取签名私钥（用户信息签名密钥由 Union 主服务器生成并分发）。 */
    public Map<String, Object> syncPrivateKey() {
        YggcSettings settings = settings();
        if (settings.unionMemberKey().isBlank()) {
            throw new IllegalArgumentException("未配置 Union Member Key，无法从上游获取签名私钥");
        }
        UnionResult result = requireOk(unionClient.get(apiRoot(), "/privatekey", memberKey()), "获取签名私钥");
        String privateKeyPem = result.text("privateKey");
        String version = result.text("privateKeyVersion");
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new IllegalArgumentException("上游响应中缺少 privateKey 字段");
        }
        KeyMaterial material = cryptoService.importUnionPrivateKey(privateKeyPem);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("version", version);
        state.put("syncedAt", System.currentTimeMillis());
        state.put("source", "union");
        repository.saveUnionState("privatekey", state);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("privateKeyVersion", version);
        body.put("syncedAt", state.get("syncedAt"));
        body.put("publicKey", cryptoService.texturePublicKeyPem());
        body.put("message", "已从 Union 主服务器同步签名私钥（版本 " + version + "）");
        return body;
    }

    /** 从上游拉取皮肤站列表。 */
    public Map<String, Object> syncServerList() {
        UnionResult result = requireOk(unionClient.get(apiRoot(), "/serverlist", memberKey()), "获取皮肤站列表");
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("servers", result.json() == null ? List.of() : result.json().get("servers"));
        state.put("version", result.text("version"));
        state.put("syncedAt", System.currentTimeMillis());
        repository.saveUnionState("serverlist", state);
        return state;
    }

    /** 全量角色同步已迁至 {@link YggcProfileSyncService}（同时维护推送快照）。 */

    /** 上游公告信息（GET /），含 union_host_signature_public_key 与各数据版本。 */
    public Map<String, Object> helloUpstream() {
        UnionResult result = unionClient.get(apiRoot(), "", null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reachable", result.ok());
        body.put("status", result.status());
        body.put("latencyMs", result.latencyMs());
        body.put("response", result.json());
        return body;
    }

    /** Union 状态总览（设置页展示）。 */
    public Map<String, Object> unionStatus() {
        YggcSettings settings = settings();
        Map<String, Object> body = new LinkedHashMap<>(unionLocalState());
        body.put("apiRoot", settings.unionApiRoot());
        body.put("memberKeyConfigured", !settings.unionMemberKey().isBlank());
        body.put("upstream", helloUpstream());
        return body;
    }

    /** 本地联邦状态（不触发上游请求）：私钥来源 / 版本、服务器列表版本 / 数量。 */
    public Map<String, Object> unionLocalState() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> privateKey = repository.unionPrivateKey()
                .map(document -> new LinkedHashMap<>(document))
                .orElseGet(LinkedHashMap::new);
        body.put("privateKey", privateKey);
        body.put("privateKeySynced", !privateKey.isEmpty());
        Map<String, Object> serverList = repository.serverList()
                .map(document -> new LinkedHashMap<>(document))
                .orElseGet(LinkedHashMap::new);
        Object servers = serverList.get("servers");
        body.put("serverList", serverList);
        body.put("serverCount", servers instanceof List<?> list ? list.size() : 0);
        return body;
    }

    /** 是否已导入 Union 主服务器分发的签名私钥。 */
    public boolean unionPrivateKeySynced() {
        return repository.unionPrivateKey().isPresent();
    }

    // ---- 用户端：跨站角色 ----

    /** 当前用户角色的跨站数据：各站同名占用（unmapped）+ 已绑定站点（detail）。 */
    public Map<String, Object> overview(String userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        List<PluginSkinProfile> profiles = profilesOf(userId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (PluginSkinProfile profile : profiles) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("uuid", profile.uuid());
            item.put("name", profile.name());
            UnionResult duplicates = unionClient.get(apiRoot(),
                    "/profile/unmapped/byname/" + YggcUnionClient.encode(profile.name()), memberKey());
            item.put("duplicateNames", duplicates.ok() && duplicates.json() != null
                    ? duplicates.json() : List.of());
            UnionResult detail = unionClient.get(apiRoot(),
                    "/profile/detail/" + YggcUnionClient.encode(profile.uuid()), memberKey());
            item.put("detail", detail.ok() && detail.json() != null ? detail.json() : Map.of());
            items.add(item);
        }
        body.put("profiles", items);
        body.put("serverList", repository.serverList().map(state -> state.get("servers")).orElse(List.of()));
        body.put("securityLevel", securityLevelQuietly());
        return body;
    }

    public Map<String, Object> bindProfile(String uuid) {
        UnionResult result = requireOk(unionClient.post(apiRoot(), "/profile/bind",
                Map.of("uuid", uuid), memberKey()), "发起跨站绑定");
        String token = result.text("token");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("response", result.json());
        return body;
    }

    public Map<String, Object> bindToProfile(String uuid, String token) {
        UnionResult result = requireOk(unionClient.post(apiRoot(), "/profile/bindto",
                Map.of("uuid", uuid, "token", token), memberKey()), "确认跨站绑定");
        return result.json() != null ? result.json() : Map.of("status", result.status());
    }

    public Map<String, Object> unbindProfile(String uuid) {
        UnionResult result = requireOk(unionClient.post(apiRoot(), "/profile/unbind",
                Map.of("uuid", uuid), memberKey()), "解除跨站绑定");
        return result.json() != null ? result.json() : Map.of("status", result.status());
    }

    public Map<String, Object> requestRemapUuid(String me, String target) {
        UnionResult result = requireOk(unionClient.post(apiRoot(), "/profile/remapuuid",
                Map.of("me", me, "target", target), memberKey()), "请求 UUID 重映射");
        return result.json() != null ? result.json() : Map.of("status", result.status());
    }

    // ---- 安全等级 ----

    /** 查询 Union 主服务器对本站后端的安全评级。 */
    public Map<String, Object> securityLevel() {
        YggcSettings settings = settings();
        if (settings.unionMemberKey().isBlank()) {
            throw new IllegalArgumentException("未配置 Union Member Key");
        }
        UnionResult code = requireOk(unionClient.post(apiRoot(), "/code",
                Map.of("token", memberKey()), memberKey()), "获取安全等级查询码");
        String backendCode = code.text("code");
        if (backendCode == null || backendCode.isBlank()) {
            throw new IllegalArgumentException("上游响应中缺少 code 字段");
        }
        UnionResult level = requireOk(unionClient.get(apiRoot(),
                "/backend/" + YggcUnionClient.encode(backendCode) + "/security/level", memberKey()),
                "查询安全等级");
        return level.json() != null ? level.json() : Map.of("raw", snippet(level.body()));
    }

    private Map<String, Object> securityLevelQuietly() {
        try {
            return securityLevel();
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    // ---- Union OAuth2（主服务器经本站登录）----

    /** Union 主服务器的 OAuth2 公钥（用于加密 userInfoToken）。 */
    public String oauth2BackendPublicKey() {
        UnionResult result = requireOk(unionClient.get(apiRoot(), "/oauth2/backend", null), "获取 Union OAuth2 公钥");
        String publicKey = result.text("publicKey");
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalArgumentException("上游响应中缺少 publicKey 字段");
        }
        return publicKey;
    }

    public String unionOauth2SigPublicKeyPem() {
        return cryptoService.unionOauth2SigPublicKeyPem();
    }

    /** 构造加密后的 userInfoToken（Union OAuth2 grant 流程）。 */
    public String buildUserInfoToken(PluginUserProfile user) {
        YggcSettings settings = settings();
        String masterPublicKey = oauth2BackendPublicKey();
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("uid", user.id());
        userInfo.put("nickname", user.nickname() == null ? user.username() : user.nickname());
        userInfo.put("email", user.email());
        userInfo.put("expires_at", System.currentTimeMillis() / 1000 + 600);
        String encodedUserInfo = java.util.Base64.getEncoder()
                .encodeToString(JsonSupport.write(userInfo).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String mac = cryptoService.hmacSha256Hex(encodedUserInfo, settings.unionMemberKey());
        String signature = cryptoService.signSha256Rsa(cryptoService.unionOauth2SigKeyPair(),
                encodedUserInfo + "." + mac);
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("userInfo", encodedUserInfo);
        token.put("mac", mac);
        token.put("signature", signature);
        return cryptoService.rsaEncryptWithPem(masterPublicKey, JsonSupport.write(token));
    }

    // ---- 成员回调（Union 主服务器 → 本站）----

    /** GET /union/member：向主服务器声明本站数据版本与可用特性。 */
    public Map<String, Object> memberHello() {
        YggcSettings settings = settings();
        List<String> features = new ArrayList<>();
        features.add("unionBlacklist");
        if (settings.unionEnableOauth2()) {
            features.add("unionOAuth2");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("yggdrasilApiVersion", "1.0.0");
        body.put("serverListVersion", repository.serverList().map(state -> state.get("version")).orElse(null));
        body.put("privateKeyVersion", repository.unionPrivateKey().map(state -> state.get("version")).orElse(null));
        body.put("enabledFeatures", features);
        return body;
    }

    /** GET /union/member/queryemail?username=x：供 Union 黑名单核验的邮箱查询。 */
    public Optional<String> memberQueryEmail(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return skinService().findProfileByName(username.trim())
                .flatMap(profile -> {
                    try {
                        return Optional.ofNullable(Long.parseLong(profile.ownerId()));
                    } catch (NumberFormatException e) {
                        return Optional.<Long>empty();
                    }
                })
                .flatMap(id -> context.framework().users().findById(id))
                .map(PluginUserProfile::email)
                .filter(email -> email != null && !email.isBlank());
    }

    /** POST /union/member/remapuuid：主服务器下发 UUID 重映射表（旧 → 新）。 */
    public Map<String, Object> memberRemapUuid(Map<String, Object> remapped) {
        Object raw = remapped == null ? null : remapped.get("remapped_uuid");
        Map<String, String> mappings = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((key, value) -> mappings.put(String.valueOf(key), String.valueOf(value)));
        }
        Map<String, String> existing = repository.uuidMappings();
        existing.putAll(mappings);
        repository.saveUuidMappings(existing);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applied", mappings.size());
        body.put("total", existing.size());
        return body;
    }

    /** UUID 重映射查询（旧 UUID → 新 UUID），未命中返回原值。 */
    public String resolveUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        String normalized = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : repository.uuidMappings().entrySet()) {
            if (entry.getKey().replace("-", "").toLowerCase(Locale.ROOT).equals(normalized)) {
                return entry.getValue();
            }
        }
        return uuid;
    }

    /** POST /union/member/updatebackendkey：主服务器轮换成员密钥。 */
    public void memberUpdateBackendKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("缺少 key 字段");
        }
        settingsService.save(settings().withUnionMemberKey(key.trim()));
    }

    /** POST /union/member/updateplugin：YuDream 插件不支持热更新，返回说明。 */
    public Map<String, Object> memberUpdatePlugin(String url) {
        if (!settings().unionEnableUpdate()) {
            throw new IllegalArgumentException("union_enable_update 已关闭，拒绝更新请求");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ignored");
        body.put("reason", "YuDream 插件需由管理员手动安装更新包：" + (url == null ? "" : url));
        return body;
    }

    // ---- 黑名单代理（管理端）----

    /** MUA Member Key 是否已配置（黑名单等直连上游接口的前置条件）。 */
    public boolean memberKeyConfigured() {
        return !settings().unionMemberKey().isBlank();
    }

    public YggcUnionClient.UnionResult blacklistQuery(String query) {
        return unionClient.get(apiRoot(), "/blacklist/query" + (query == null || query.isBlank()
                ? "" : "?" + query), memberKey());
    }

    public UnionResult blacklistCreate(Map<String, Object> payload) {
        return unionClient.post(apiRoot(), "/blacklist/restful", payload, memberKey());
    }

    public UnionResult blacklistInvalidate(String id) {
        return unionClient.put(apiRoot(), "/blacklist/invalidate/" + YggcUnionClient.encode(id), Map.of(), memberKey());
    }

    public UnionResult blacklistDelete(String id) {
        return unionClient.delete(apiRoot(), "/blacklist/restful/" + YggcUnionClient.encode(id), memberKey());
    }

    // ---- 内部 ----

    private List<PluginSkinProfile> profilesOf(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        try {
            return skinService().findProfilesByOwner(userId.trim());
        } catch (Exception e) {
            return List.of();
        }
    }

    private PluginSkinService skinService() {
        return context.service(SKIN_PLUGIN_CODE, PluginSkinService.class)
                .orElseThrow(() -> new IllegalArgumentException("yudream-skin 插件未启用"));
    }
}
