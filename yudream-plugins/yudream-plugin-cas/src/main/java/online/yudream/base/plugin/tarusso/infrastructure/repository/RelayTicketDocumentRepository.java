package online.yudream.base.plugin.tarusso.infrastructure.repository;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.tarusso.domain.aggregate.RelayTicket;
import online.yudream.base.plugin.tarusso.domain.repo.RelayTicketRepository;
import online.yudream.base.plugin.tarusso.infrastructure.support.DocValues;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 兜底待回调记录的文档存储实现。
 *
 * <p>以宿主 state 作为文档 id（state 本身是 43 字符的 URL 安全随机串），同时把 hostState 也写进文档体，
 * 这样扫描时不必依赖存储层是否回填 id 字段。文档存储没有"按时间排序"的查询能力，因此匹配时按页扫描
 * 一个有界范围再取 createdAt 最大的一条；兜底路径的并发量极小，这样比引入额外索引更简单可靠。
 */
public final class RelayTicketDocumentRepository implements RelayTicketRepository {

    static final String COLLECTION = "relay-tickets";
    private static final int SCAN_PAGE_SIZE = 100;
    /** 最多扫描多少页（= 500 条）用于匹配 / 清理，避免异常情况下全表遍历。 */
    private static final int MAX_SCAN_PAGES = 5;

    private final PluginDocumentStore documents;

    public RelayTicketDocumentRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    @Override
    public void save(RelayTicket ticket) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("hostState", ticket.hostState());
        document.put("platformType", ticket.platformType());
        document.put("createdAt", ticket.createdAt());
        document.put("expiresAt", ticket.expiresAt());
        documents.save(COLLECTION, ticket.hostState(), document);
    }

    @Override
    public Optional<RelayTicket> latest(String platformType, long now) {
        return scan().stream()
                .filter(ticket -> !ticket.expired(now))
                .filter(ticket -> ticket.matchesPlatform(platformType))
                .max(Comparator.comparingLong(RelayTicket::createdAt));
    }

    @Override
    public void consume(String hostState) {
        if (hostState != null && !hostState.isBlank()) {
            documents.delete(COLLECTION, hostState.trim());
        }
    }

    @Override
    public int purgeExpired(long now) {
        int removed = 0;
        for (RelayTicket ticket : scan()) {
            if (ticket.expired(now)) {
                documents.delete(COLLECTION, ticket.hostState());
                removed++;
            }
        }
        return removed;
    }

    private List<RelayTicket> scan() {
        List<RelayTicket> tickets = new ArrayList<>();
        for (int page = 1; page <= MAX_SCAN_PAGES; page++) {
            List<Map<String, Object>> batch = documents.findAll(COLLECTION, page, SCAN_PAGE_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            for (Map<String, Object> document : batch) {
                RelayTicket ticket = toTicket(document);
                if (ticket != null) {
                    tickets.add(ticket);
                }
            }
            if (batch.size() < SCAN_PAGE_SIZE) {
                break;
            }
        }
        return tickets;
    }

    private RelayTicket toTicket(Map<String, Object> document) {
        if (document == null) {
            return null;
        }
        String hostState = firstText(document, "hostState", "id", "_id");
        if (hostState == null) {
            return null;
        }
        try {
            return new RelayTicket(hostState, DocValues.string(document, "platformType"),
                    DocValues.number(document, "createdAt", 0L), DocValues.number(document, "expiresAt", 0L));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String firstText(Map<String, Object> document, String... keys) {
        for (String key : keys) {
            String value = DocValues.string(document, key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
