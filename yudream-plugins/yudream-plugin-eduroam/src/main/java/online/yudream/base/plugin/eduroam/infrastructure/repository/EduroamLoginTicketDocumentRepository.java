package online.yudream.base.plugin.eduroam.infrastructure.repository;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamLoginTicket;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamLoginTicketRepository;
import online.yudream.base.plugin.eduroam.infrastructure.support.DocValues;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 登录交接票据的文档仓储。
 *
 * <p>票据存在共享的文档集合里而不是进程内存：多实例部署时，凭据页的认证请求与随后的回调查询
 * 可能落在不同节点，内存实现会让一半登录莫名失败。
 */
public class EduroamLoginTicketDocumentRepository implements EduroamLoginTicketRepository {

    private static final String COLLECTION = "eduroam-tickets";
    private static final int SCAN_PAGE_SIZE = 200;

    private final PluginDocumentStore documents;

    public EduroamLoginTicketDocumentRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    @Override
    public EduroamLoginTicket save(EduroamLoginTicket ticket) {
        return toTicket(documents.save(COLLECTION, ticket.id(), toDocument(ticket)));
    }

    @Override
    public Optional<EduroamLoginTicket> consume(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String key = id.trim();
        Optional<EduroamLoginTicket> found = documents.findById(COLLECTION, key).map(this::toTicket);
        // 先读后删：读不到就没有可删的；读到就立刻删掉，保证同一个票据不可能被用第二次。
        found.ifPresent(ticket -> documents.delete(COLLECTION, ticket.id()));
        return found;
    }

    @Override
    public int purgeExpired(long now) {
        int purged = 0;
        for (EduroamLoginTicket ticket : all()) {
            if (ticket.expired(now)) {
                documents.delete(COLLECTION, ticket.id());
                purged++;
            }
        }
        return purged;
    }

    @Override
    public long count() {
        return documents.count(COLLECTION);
    }

    private List<EduroamLoginTicket> all() {
        List<EduroamLoginTicket> result = new ArrayList<>();
        int page = 1;
        while (true) {
            List<EduroamLoginTicket> batch = documents.findAll(COLLECTION, page, SCAN_PAGE_SIZE).stream()
                    .map(this::toTicket)
                    .toList();
            result.addAll(batch);
            if (batch.size() < SCAN_PAGE_SIZE) {
                return result;
            }
            page++;
        }
    }

    private Map<String, Object> toDocument(EduroamLoginTicket ticket) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", ticket.id());
        document.put("email", ticket.email());
        document.put("identity", ticket.identity());
        document.put("account", ticket.account());
        document.put("state", ticket.state());
        document.put("platformType", ticket.platformType());
        document.put("expiresAt", ticket.expiresAt());
        document.put("createdAt", ticket.createdAt());
        return document;
    }

    private EduroamLoginTicket toTicket(Map<String, Object> document) {
        return new EduroamLoginTicket(
                DocValues.string(document, "id"),
                DocValues.string(document, "email"),
                DocValues.string(document, "identity"),
                DocValues.string(document, "account"),
                DocValues.string(document, "state"),
                DocValues.string(document, "platformType"),
                DocValues.number(document, "expiresAt", 0L),
                DocValues.number(document, "createdAt", 0L)
        );
    }
}
