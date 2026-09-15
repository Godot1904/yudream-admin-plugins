package online.yudream.base.plugin.eduroam.infrastructure.repository;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamAccount;
import online.yudream.base.plugin.eduroam.domain.enumerate.EduroamAccountStatus;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamAccountRepository;
import online.yudream.base.plugin.eduroam.infrastructure.support.DocValues;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 登录账号的文档仓储：邮箱就是文档 ID，天然去重，不会出现「同一个邮箱两条记录」。
 */
public class EduroamAccountDocumentRepository implements EduroamAccountRepository {

    private static final String COLLECTION = "eduroam-accounts";
    private static final int SCAN_PAGE_SIZE = 200;

    private final PluginDocumentStore documents;

    public EduroamAccountDocumentRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    @Override
    public EduroamAccount save(EduroamAccount account) {
        return toAccount(documents.save(COLLECTION, account.id(), toDocument(account)));
    }

    @Override
    public Optional<EduroamAccount> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return documents.findById(COLLECTION, id.trim().toLowerCase(Locale.ROOT)).map(this::toAccount);
    }

    @Override
    public List<EduroamAccount> listAll() {
        List<EduroamAccount> result = new ArrayList<>();
        int page = 1;
        while (true) {
            List<EduroamAccount> batch = documents.findAll(COLLECTION, page, SCAN_PAGE_SIZE).stream()
                    .map(this::toAccount)
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
            documents.delete(COLLECTION, id.trim().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public long count() {
        return documents.count(COLLECTION);
    }

    private Map<String, Object> toDocument(EduroamAccount account) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", account.id());
        document.put("email", account.email());
        document.put("identity", account.identity());
        document.put("account", account.account());
        document.put("domain", account.domain());
        document.put("status", account.status().name());
        document.put("lastLoginIp", account.lastLoginIp());
        document.put("lastLoginAt", account.lastLoginAt());
        document.put("loginCount", account.loginCount());
        document.put("firstLoginAt", account.firstLoginAt());
        document.put("blockedByUserId", account.blockedByUserId());
        document.put("blockReason", account.blockReason());
        document.put("blockedAt", account.blockedAt());
        document.put("createdAt", account.createdAt());
        document.put("updatedAt", account.updatedAt());
        return document;
    }

    private EduroamAccount toAccount(Map<String, Object> document) {
        return new EduroamAccount(
                DocValues.string(document, "id"),
                DocValues.string(document, "email"),
                DocValues.string(document, "identity"),
                DocValues.string(document, "account"),
                DocValues.string(document, "domain"),
                EduroamAccountStatus.parse(DocValues.string(document, "status")),
                DocValues.string(document, "lastLoginIp"),
                DocValues.number(document, "lastLoginAt", 0L),
                DocValues.integer(document, "loginCount", 0),
                DocValues.number(document, "firstLoginAt", 0L),
                DocValues.string(document, "blockedByUserId"),
                DocValues.string(document, "blockReason"),
                DocValues.number(document, "blockedAt", 0L),
                DocValues.number(document, "createdAt", 0L),
                DocValues.number(document, "updatedAt", 0L)
        );
    }
}
