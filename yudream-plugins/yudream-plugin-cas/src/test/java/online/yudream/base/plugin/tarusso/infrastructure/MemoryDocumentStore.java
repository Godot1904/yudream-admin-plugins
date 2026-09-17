package online.yudream.base.plugin.tarusso.infrastructure;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 测试用的内存文档存储（只实现插件实际用到的能力）。 */
public final class MemoryDocumentStore implements PluginDocumentStore {

    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> save(String collection, String id, Map<String, Object> document) {
        Map<String, Object> copy = new LinkedHashMap<>(document);
        copy.putIfAbsent("id", id);
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
                .filter(entry -> entry.getKey().startsWith(collection + "/"))
                .<Map<String, Object>>map(entry -> new LinkedHashMap<>(entry.getValue()))
                .toList();
    }

    @Override
    public List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size) {
        return List.of();
    }

    @Override
    public long count(String collection) {
        return store.keySet().stream().filter(key -> key.startsWith(collection + "/")).count();
    }

    @Override
    public void delete(String collection, String id) {
        store.remove(collection + "/" + id);
    }
}
