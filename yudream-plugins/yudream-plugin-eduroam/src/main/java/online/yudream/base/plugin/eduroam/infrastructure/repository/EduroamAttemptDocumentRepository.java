package online.yudream.base.plugin.eduroam.infrastructure.repository;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamAttempt;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamAttemptRepository;
import online.yudream.base.plugin.eduroam.infrastructure.support.DocValues;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 核验尝试审计仓储：只追加文档，按创建时间倒序读取。 */
public class EduroamAttemptDocumentRepository implements EduroamAttemptRepository {

    private static final String COLLECTION = "eduroam-attempts";
    private static final int SCAN_PAGE_SIZE = 200;

    private final PluginDocumentStore documents;

    public EduroamAttemptDocumentRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    @Override
    public EduroamAttempt save(EduroamAttempt attempt) {
        return toAttempt(documents.save(COLLECTION, attempt.id(), toDocument(attempt)));
    }

    @Override
    public List<EduroamAttempt> listAll() {
        List<EduroamAttempt> result = new ArrayList<>();
        int page = 1;
        while (true) {
            List<EduroamAttempt> batch = documents.findAll(COLLECTION, page, SCAN_PAGE_SIZE).stream()
                    .map(this::toAttempt)
                    .toList();
            result.addAll(batch);
            if (batch.size() < SCAN_PAGE_SIZE) {
                return result;
            }
            page++;
        }
    }

    @Override
    public void delete(String id) {
        if (id != null && !id.isBlank()) {
            documents.delete(COLLECTION, id.trim());
        }
    }

    @Override
    public long count() {
        return documents.count(COLLECTION);
    }

    private Map<String, Object> toDocument(EduroamAttempt attempt) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", attempt.id());
        document.put("email", attempt.email());
        document.put("identity", attempt.identity());
        document.put("account", attempt.account());
        document.put("domain", attempt.domain());
        document.put("success", attempt.success());
        document.put("reasonCode", attempt.reasonCode());
        document.put("message", attempt.message());
        document.put("detail", attempt.detail());
        document.put("clientIp", attempt.clientIp());
        document.put("latencyMs", attempt.latencyMs());
        document.put("createdAt", attempt.createdAt());
        return document;
    }

    private EduroamAttempt toAttempt(Map<String, Object> document) {
        return new EduroamAttempt(
                DocValues.string(document, "id"),
                DocValues.string(document, "email"),
                DocValues.string(document, "identity"),
                DocValues.string(document, "account"),
                DocValues.string(document, "domain"),
                DocValues.bool(document, "success", false),
                DocValues.string(document, "reasonCode"),
                DocValues.string(document, "message"),
                DocValues.string(document, "detail"),
                DocValues.string(document, "clientIp"),
                DocValues.number(document, "latencyMs", 0L),
                DocValues.number(document, "createdAt", 0L)
        );
    }
}
