package online.yudream.base.plugin.tarusso.interfaces.http;

import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.secret.PluginSecretStore;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.tarusso.application.dto.SsoSettingsDto;
import online.yudream.base.plugin.tarusso.application.service.SettingsService;
import online.yudream.base.plugin.tarusso.application.service.StudentInfoService;
import online.yudream.base.plugin.tarusso.domain.aggregate.SsoSettings;
import online.yudream.base.plugin.tarusso.domain.aggregate.StudentMapping;
import online.yudream.base.plugin.tarusso.infrastructure.cas.CasProtocolClient;
import online.yudream.base.plugin.tarusso.infrastructure.oidc.OidcProtocolClient;
import online.yudream.base.plugin.tarusso.infrastructure.repository.SsoSettingsDocumentRepository;
import online.yudream.base.plugin.tarusso.infrastructure.repository.StudentMappingDocumentRepository;
import online.yudream.base.plugin.tarusso.infrastructure.repository.StudentProfileDocumentRepository;
import online.yudream.base.plugin.tarusso.infrastructure.secret.ClientSecretStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaruSsoHttpFacadeTest {

    @Test
    void gateDisabledByDefault() {
        Env env = new Env();
        env.settings.save(casReady(), null);
        Map<String, Object> gate = gate(env.facade);
        assertEquals(false, gate.get("requireBinding"));
        assertEquals("cas", gate.get("providerCode"));
        assertEquals("cas", gate.get("type"));
    }

    @Test
    void gateRequiresLoginReady() {
        Env env = new Env();
        // 未保存设置 → loginEnabled=false，开关即使打开也不生效
        env.studentInfo.saveMapping(new StudentMapping(true, "", "", "", "", ""));
        assertEquals(false, gate(env.facade).get("requireBinding"));

        env.settings.save(casReady(), null);
        assertEquals(true, gate(env.facade).get("requireBinding"));
    }

    @Test
    void mappingSaveMergesPartialBody() {
        Env env = new Env();
        env.facade.saveMapping(request("""
                {"requireBinding": true, "nameKey": "cn"}
                """));
        Map<String, Object> mapping = body(env.facade.mapping());
        assertEquals(true, mapping.get("requireBinding"));
        assertEquals("cn", mapping.get("nameKey"));
        assertEquals("", mapping.get("deptKey"));

        // 只更新开关，键保留
        env.facade.saveMapping(request("""
                {"requireBinding": false}
                """));
        Map<String, Object> updated = body(env.facade.mapping());
        assertEquals(false, updated.get("requireBinding"));
        assertEquals("cn", updated.get("nameKey"));
    }

    @Test
    void studentsPageParsesQueryAndValidates() {
        Env env = new Env();
        env.studentInfo.record(identity("20230101", "张三", Map.of("cn", "张三", "className", "计算机23-1班")), online.yudream.base.plugin.tarusso.domain.enumerate.SsoProtocol.CAS);
        env.studentInfo.record(identity("20230102", "李四", Map.of("cn", "李四")), online.yudream.base.plugin.tarusso.domain.enumerate.SsoProtocol.CAS);

        Map<String, Object> page = body(env.facade.students(request(Map.of("page", List.of("1"), "size", List.of("10")))));
        assertEquals(2L, page.get("total"));

        Map<String, Object> search = body(env.facade.students(request(Map.of("page", List.of("1"), "size", List.of("10"), "keyword", List.of("李四")))));
        assertEquals(1L, search.get("total"));

        // 班级关键词搜索
        Map<String, Object> classSearch = body(env.facade.students(request(Map.of("page", List.of("1"), "size", List.of("10"), "keyword", List.of("计算机")))));
        assertEquals(1L, classSearch.get("total"));

        assertThrows(IllegalArgumentException.class, () -> env.facade.studentDetail(request(Map.of())));
        Map<String, Object> detail = body(env.facade.studentDetail(request(Map.of("socialUid", List.of("20230101")))));
        assertEquals("张三", detail.get("name"));
    }

    @Test
    void myProfileRequiresLoginAndReturnsPrefillFields() {
        Env env = new Env();
        env.studentInfo.record(identity("20230101", "张三", Map.of(
                "cn", "张三",
                "department", "信息工程学院",
                "className", "计算机23-1班"
        )), online.yudream.base.plugin.tarusso.domain.enumerate.SsoProtocol.CAS);

        // 未登录（principal 为空）→ 报错
        assertThrows(IllegalArgumentException.class,
                () -> env.facade.myProfile(request(Map.of("socialUid", List.of("20230101")))));

        // 已登录 → 返回预填字段
        PluginHttpResponse response = env.facade.myProfile(loggedInRequest(Map.of("socialUid", List.of("20230101"))));
        Map<String, Object> prefill = body(response);
        assertEquals("20230101", prefill.get("studentNo"));
        assertEquals("张三", prefill.get("studentName"));
        assertEquals("信息工程学院", prefill.get("college"));
        assertEquals("计算机23-1班", prefill.get("className"));

        // 档案不存在 → 404
        assertEquals(404, env.facade.myProfile(loggedInRequest(Map.of("socialUid", List.of("404")))).status());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> gate(TaruSsoHttpFacade facade) {
        PluginHttpResponse response = facade.gate();
        return (Map<String, Object>) response.body();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(PluginHttpResponse response) {
        return (Map<String, Object>) response.body();
    }

    private static PluginHttpRequest request(String body) {
        return new PluginHttpRequest("PUT", "/api/plugins/cas/api/admin/mapping", Map.of(), Map.of(), body, null);
    }

    private static PluginHttpRequest request(Map<String, List<String>> query) {
        return new PluginHttpRequest("GET", "/api/plugins/cas/api/admin/students", Map.of(), query, "", null);
    }

    private static PluginHttpRequest loggedInRequest(Map<String, List<String>> query) {
        return new PluginHttpRequest("GET", "/api/plugins/cas/api/me/profile", Map.of(), query, "",
                new online.yudream.base.plugin.spi.system.security.PluginPrincipal(1L, List.of()));
    }

    private static online.yudream.base.plugin.tarusso.domain.service.SsoProtocolClient.ExternalIdentity identity(String uid, String nickname, Map<String, String> attributes) {
        return new online.yudream.base.plugin.tarusso.domain.service.SsoProtocolClient.ExternalIdentity(uid, nickname, "", "", "", attributes);
    }

    private static SsoSettingsDto casReady() {
        return new SsoSettingsDto(
                true, "CAS", SsoSettings.DEFAULT_DISPLAY_NAME, SsoSettings.DEFAULT_ICON,
                SsoSettings.DEFAULT_CAS_BASE_URL, SsoSettings.DEFAULT_LOGIN_PATH, SsoSettings.DEFAULT_VALIDATE_PATH,
                SsoSettings.DEFAULT_OIDC_ISSUER, SsoSettings.DEFAULT_OIDC_AUTHORIZE_PATH, SsoSettings.DEFAULT_OIDC_TOKEN_PATH,
                SsoSettings.DEFAULT_OIDC_USERINFO_PATH, SsoSettings.DEFAULT_OIDC_JWKS_PATH, SsoSettings.DEFAULT_OIDC_REGISTER_PATH,
                "", false, SsoSettings.DEFAULT_SCOPES, "https://site.example/api/external-login/callback", false
        );
    }

    private static final class Env {
        final MemoryDocuments documents = new MemoryDocuments();
        final SettingsService settings = new SettingsService(
                new SsoSettingsDocumentRepository(documents),
                new ClientSecretStore(new MemorySecrets()),
                new CasProtocolClient(),
                new OidcProtocolClient()
        );
        final StudentInfoService studentInfo = new StudentInfoService(
                new StudentMappingDocumentRepository(documents),
                new StudentProfileDocumentRepository(documents)
        );
        final TaruSsoHttpFacade facade = new TaruSsoHttpFacade(settings, studentInfo);
    }

    private static final class MemoryDocuments implements PluginDocumentStore {
        private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

        @Override
        public Map<String, Object> save(String collection, String id, Map<String, Object> document) {
            Map<String, Object> copy = new LinkedHashMap<>(document);
            store.put(collection + "/" + id, copy);
            return copy;
        }

        @Override
        public Optional<Map<String, Object>> findById(String collection, String id) {
            Map<String, Object> document = store.get(collection + "/" + id);
            return document == null ? Optional.empty() : Optional.of(new LinkedHashMap<>(document));
        }

        @Override
        public List<Map<String, Object>> findAll(String collection, int page, int size) {
            return store.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(collection + "/"))
                    .<Map<String, Object>>map(e -> new LinkedHashMap<>(e.getValue()))
                    .toList();
        }

        @Override
        public List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size) {
            return List.of();
        }

        @Override
        public long count(String collection) {
            return store.keySet().stream().filter(k -> k.startsWith(collection + "/")).count();
        }

        @Override
        public void delete(String collection, String id) {
            store.remove(collection + "/" + id);
        }
    }

    private static final class MemorySecrets implements PluginSecretStore {
        private final Map<String, byte[]> store = new ConcurrentHashMap<>();

        @Override
        public void put(String key, byte[] secret) {
            store.put(key, secret);
        }

        @Override
        public Optional<byte[]> get(String key) {
            byte[] value = store.get(key);
            return value == null ? Optional.empty() : Optional.of(value);
        }

        @Override
        public boolean delete(String key) {
            return store.remove(key) != null;
        }
    }
}
