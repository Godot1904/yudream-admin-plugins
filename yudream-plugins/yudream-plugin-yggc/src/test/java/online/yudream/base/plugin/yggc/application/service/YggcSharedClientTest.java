package online.yudream.base.plugin.yggc.application.service;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.yggc.application.service.YggcOAuthService.OAuthException;
import online.yudream.base.plugin.yggc.domain.aggregate.OAuthClient;
import online.yudream.base.plugin.yggc.domain.aggregate.YggcSettings;
import online.yudream.base.plugin.yggc.infrastructure.repository.YggcRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 共享客户端（发现文档 shared_client_id）的配置校验、发现文档输出与保护规则。
 * 使用内存文档存储，不依赖宿主运行时。
 */
class YggcSharedClientTest {

    private final FakeDocumentStore documents = new FakeDocumentStore();
    private final YggcRepository repository = new YggcRepository(documents);
    private final YggcSettingsService settingsService = new YggcSettingsService(repository);

    @Test
    void discoveryOmitsSharedClientIdByDefault() {
        YggcOAuthService service = new YggcOAuthService(null, repository, null, null, settingsService);

        Map<String, Object> discovery = service.discovery("https://example.test/api/yggdrasil");

        assertFalse(discovery.containsKey("shared_client_id"),
                "未配置共享客户端时发现文档不得出现 shared_client_id，保持与 1.1.0 一致");
    }

    @Test
    void discoveryEmitsConfiguredSharedClientId() {
        repository.saveClient(publicClient("yggc_shared", true));
        settingsService.save(settingsWithShared("yggc_shared"));
        YggcOAuthService service = new YggcOAuthService(null, repository, null, null, settingsService);

        Map<String, Object> discovery = service.discovery("https://example.test/api/yggdrasil");

        assertEquals("yggc_shared", discovery.get("shared_client_id"));
        assertEquals("https://example.test/api/yggdrasil", discovery.get("issuer"));
    }

    @Test
    void savingRejectsUnknownSharedClient() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> settingsService.save(settingsWithShared("yggc_missing")));

        assertTrue(error.getMessage().contains("共享客户端不存在"));
        assertTrue(settingsService.current().sharedClientId().isEmpty());
    }

    @Test
    void savingRejectsConfidentialSharedClient() {
        repository.saveClient(publicClient("yggc_conf", false));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> settingsService.save(settingsWithShared("yggc_conf")));

        assertTrue(error.getMessage().contains("公共客户端"));
    }

    @Test
    void savingRejectsDisabledSharedClient() {
        repository.saveClient(publicClient("yggc_off", true, false));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> settingsService.save(settingsWithShared("yggc_off")));

        assertTrue(error.getMessage().contains("已被禁用"));
    }

    @Test
    void blankSharedClientIdIsStoredAsEmpty() {
        YggcSettings saved = settingsService.save(settingsWithShared("   "));

        assertTrue(saved.sharedClientId().isEmpty());
        assertTrue(saved.toDocument().containsKey("sharedClientId"));
        assertEquals("", saved.toDocument().get("sharedClientId"));
    }

    @Test
    void eligibleCandidatesOnlyContainEnabledPublicClients() {
        repository.saveClient(publicClient("yggc_public", true));
        repository.saveClient(publicClient("yggc_public_off", true, false));
        repository.saveClient(publicClient("yggc_confidential", false));
        settingsService.save(settingsWithShared("yggc_public"));
        YggcOAuthService service = new YggcOAuthService(null, repository, null, null, settingsService);

        List<Map<String, Object>> candidates = service.eligibleSharedClients();

        assertEquals(1, candidates.size());
        assertEquals("yggc_public", candidates.get(0).get("id"));
        assertEquals(Boolean.TRUE, candidates.get(0).get("shared"));
        assertEquals(Boolean.TRUE, candidates.get(0).get("publicClient"));
    }

    @Test
    void sharedClientCannotBeDeletedOrDisabledOrTurnedConfidential() {
        repository.saveClient(publicClient("yggc_shared", true));
        settingsService.save(settingsWithShared("yggc_shared"));
        YggcOAuthService service = new YggcOAuthService(null, repository, null, null, settingsService);

        IllegalArgumentException deleteError = assertThrows(IllegalArgumentException.class,
                () -> service.deleteClient("yggc_shared"));
        assertTrue(deleteError.getMessage().contains("共享客户端"));

        IllegalArgumentException disableError = assertThrows(IllegalArgumentException.class,
                () -> service.updateClient("yggc_shared", "公共启动器", List.of(), true, false));
        assertTrue(disableError.getMessage().contains("共享客户端"));

        IllegalArgumentException confidentialError = assertThrows(IllegalArgumentException.class,
                () -> service.updateClient("yggc_shared", "公共启动器", List.of("https://app.test/cb"), false, true));
        assertTrue(confidentialError.getMessage().contains("共享客户端"));

        assertTrue(repository.findClient("yggc_shared").isPresent(), "被引用的共享客户端不得被删除");
    }

    @Test
    void otherClientsKeepWorkingWhenSharedClientIsBound() {
        repository.saveClient(publicClient("yggc_shared", true));
        repository.saveClient(publicClient("yggc_other", true));
        settingsService.save(settingsWithShared("yggc_shared"));
        YggcOAuthService service = new YggcOAuthService(null, repository, null, null, settingsService);

        // 非共享客户端仍可正常禁用 / 删除 / 重置密钥
        Map<String, Object> updated = service.updateClient("yggc_other", "其他启动器", List.of(), true, false);
        assertEquals(Boolean.FALSE, updated.get("enabled"));
        service.deleteClient("yggc_other");

        assertTrue(repository.findClient("yggc_other").isEmpty());
        assertEquals("yggc_shared", settingsService.current().sharedClientId());
    }

    @Test
    void unknownClientRequestsAreStillRejected() {
        repository.saveClient(publicClient("yggc_shared", true));
        settingsService.save(settingsWithShared("yggc_shared"));
        YggcOAuthService service = new YggcOAuthService(null, repository, null, null, settingsService);

        Map<String, String> unknown = new LinkedHashMap<>();
        unknown.put("client_id", "yggc_not_registered");
        unknown.put("scope", "openid Yggdrasil.PlayerProfiles.Select Yggdrasil.Server.Join");

        OAuthException error = assertThrows(OAuthException.class,
                () -> service.startDevice(unknown, "https://example.test"));
        assertEquals("invalid_client", error.error());
    }

    private static YggcSettings settingsWithShared(String sharedClientId) {
        YggcSettings defaults = YggcSettings.defaults();
        return new YggcSettings(
                defaults.uuidAlgorithm(), defaults.tokenExpire(), defaults.tokenRefreshExpire(),
                defaults.tokensLimit(), defaults.rateLimit(), defaults.skinDomain(),
                defaults.searchProfileMax(), defaults.showConfigSection(), defaults.enableAli(),
                defaults.restoreApi(), defaults.disableAuthserver(), defaults.connectServerUrl(),
                defaults.unionApiRoot(), defaults.unionMemberKey(), defaults.unionEnableUpdate(),
                defaults.unionEnableOauth2(), defaults.unionSyncEnabled(),
                defaults.unionSyncIntervalMinutes(), defaults.unionSyncOnLogin(),
                defaults.oauthAccessTtl(), defaults.oauthRefreshTtl(), defaults.oauthDeviceTtl(),
                defaults.serverName(), sharedClientId);
    }

    private static OAuthClient publicClient(String id, boolean publicClient) {
        return publicClient(id, publicClient, true);
    }

    private static OAuthClient publicClient(String id, boolean publicClient, boolean enabled) {
        return new OAuthClient(id, "应用 " + id, publicClient ? null : "hash", List.of(), publicClient,
                enabled, System.currentTimeMillis());
    }

    /** 宿主文档存储语义的内存假实现：_id 字典序升序、单页上限 200、save 覆写 id 字段。 */
    private static final class FakeDocumentStore implements PluginDocumentStore {
        private static final int MAX_PAGE = 200;
        private final Map<String, Map<String, Map<String, Object>>> collections = new ConcurrentHashMap<>();

        public Map<String, Object> save(String collection, String id, Map<String, Object> document) {
            Map<String, Object> copy = new HashMap<>(document);
            copy.put("id", id);
            collections.computeIfAbsent(collection, key -> new ConcurrentHashMap<>()).put(id, copy);
            return copy;
        }

        public Optional<Map<String, Object>> findById(String collection, String id) {
            return Optional.ofNullable(collections.getOrDefault(collection, Map.of()).get(id));
        }

        public List<Map<String, Object>> findAll(String collection, int page, int size) {
            return slice(sorted(new ArrayList<>(collections.getOrDefault(collection, Map.of()).values())), page, size);
        }

        public List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size) {
            List<Map<String, Object>> matched = collections.getOrDefault(collection, Map.of()).values().stream()
                    .filter(doc -> String.valueOf(doc.get(field)).equals(String.valueOf(value))).toList();
            return slice(sorted(matched), page, size);
        }

        public long count(String collection) {
            return collections.getOrDefault(collection, Map.of()).size();
        }

        public void delete(String collection, String id) {
            collections.getOrDefault(collection, Map.of()).remove(id);
        }

        private List<Map<String, Object>> sorted(List<Map<String, Object>> docs) {
            return docs.stream().sorted(Comparator.comparing(doc -> String.valueOf(doc.get("id")))).toList();
        }

        private List<Map<String, Object>> slice(List<Map<String, Object>> docs, int page, int size) {
            int capped = Math.min(Math.max(size, 1), MAX_PAGE);
            int from = Math.min((Math.max(page, 1) - 1) * capped, docs.size());
            return new ArrayList<>(docs.subList(from, Math.min(from + capped, docs.size())));
        }
    }
}
