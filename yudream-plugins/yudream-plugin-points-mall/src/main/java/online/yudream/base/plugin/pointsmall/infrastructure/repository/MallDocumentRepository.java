package online.yudream.base.plugin.pointsmall.infrastructure.repository;

import online.yudream.base.plugin.pointsmall.domain.aggregate.MallItem;
import online.yudream.base.plugin.pointsmall.domain.aggregate.MallRedemption;
import online.yudream.base.plugin.pointsmall.domain.enumerate.MallRedemptionStatus;
import online.yudream.base.plugin.pointsmall.domain.repo.MallRepository;
import online.yudream.base.plugin.pointsmall.domain.valobj.MallDeliveryProof;
import online.yudream.base.plugin.pointsmall.domain.valobj.MallSettings;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 积分商城的文档仓储。
 *
 * <p>文档存储只支持按字段等值查询，所以关键词、上下架、状态这类筛选统一在内存里做：先按分页扫描出全部
 * 文档，再过滤、排序，最后切出请求的那一页。这样分页总数与列表内容始终来自同一份过滤结果，不会出现
 * 「筛完再切页」导致的页码错乱。
 */
public class MallDocumentRepository implements MallRepository {

    private static final int SCAN_PAGE_SIZE = 200;
    private static final String ITEMS = "items";
    private static final String REDEMPTIONS = "redemptions";
    private static final String SETTINGS = "settings";
    private static final String SETTINGS_ID = "settings";

    private final PluginDocumentStore documents;

    public MallDocumentRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    // ------------------------------------------------------------------ 商品

    @Override
    public MallItem saveItem(MallItem item) {
        return toItem(documents.save(ITEMS, item.id(), itemDocument(item)));
    }

    @Override
    public Optional<MallItem> findItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        return documents.findById(ITEMS, itemId.trim()).map(this::toItem);
    }

    @Override
    public List<MallItem> listItems(String keyword, Boolean enabled, int page, int size) {
        return filterItems(allItems(), keyword, enabled).stream()
                .skip((long) (Math.max(page, 1) - 1) * Math.max(size, 1))
                .limit(Math.max(size, 1))
                .toList();
    }

    @Override
    public long countItems(String keyword, Boolean enabled) {
        return filterItems(allItems(), keyword, enabled).size();
    }

    /** 概览要按这些资产查余额：没设置结算资产的历史商品不产生余额卡片，也不会拿空代码去问钱包。 */
    @Override
    public List<String> listOpenItemAssetCodes() {
        return allItems().stream()
                .filter(MallItem::enabled)
                .sorted(itemOrder())
                .map(MallItem::assetCode)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public void deleteItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        documents.delete(ITEMS, itemId.trim());
    }

    private List<MallItem> filterItems(List<MallItem> items, String keyword, Boolean enabled) {
        String needle = normalizeKeyword(keyword);
        return items.stream()
                .filter(item -> enabled == null || item.enabled() == enabled)
                .filter(item -> needle == null || matches(needle, item.name()) || matches(needle, item.description()))
                .sorted(itemOrder())
                .toList();
    }

    private static Comparator<MallItem> itemOrder() {
        return Comparator.comparingInt(MallItem::sort)
                .thenComparing(Comparator.comparingLong(MallItem::createdAt).reversed());
    }

    private List<MallItem> allItems() {
        List<MallItem> result = new ArrayList<>();
        int page = 1;
        while (true) {
            List<MallItem> batch = documents.findAll(ITEMS, page, SCAN_PAGE_SIZE).stream().map(this::toItem).toList();
            result.addAll(batch);
            if (batch.size() < SCAN_PAGE_SIZE) {
                return result;
            }
            page++;
        }
    }

    // ------------------------------------------------------------------ 兑换记录

    @Override
    public MallRedemption saveRedemption(MallRedemption redemption) {
        return toRedemption(documents.save(REDEMPTIONS, redemption.id(), redemptionDocument(redemption)));
    }

    @Override
    public Optional<MallRedemption> findRedemption(String redemptionId) {
        if (redemptionId == null || redemptionId.isBlank()) {
            return Optional.empty();
        }
        return documents.findById(REDEMPTIONS, redemptionId.trim()).map(this::toRedemption);
    }

    @Override
    public List<MallRedemption> listRedemptions(String userId, String itemId, MallRedemptionStatus status, int page,
                                                int size) {
        return filterRedemptions(sourceRedemptions(userId), userId, itemId, status).stream()
                .skip((long) (Math.max(page, 1) - 1) * Math.max(size, 1))
                .limit(Math.max(size, 1))
                .toList();
    }

    @Override
    public long countRedemptions(String userId, String itemId, MallRedemptionStatus status) {
        return filterRedemptions(sourceRedemptions(userId), userId, itemId, status).size();
    }

    @Override
    public long countItemRedemptions(String itemId) {
        if (normalizeKeyword(itemId) == null) {
            return 0;
        }
        long count = 0;
        int page = 1;
        while (true) {
            List<MallRedemption> batch = documents.findByField(REDEMPTIONS, "itemId", itemId.trim(), page, SCAN_PAGE_SIZE)
                    .stream().map(this::toRedemption).toList();
            count += batch.size();
            if (batch.size() < SCAN_PAGE_SIZE) {
                return count;
            }
            page++;
        }
    }

    @Override
    public long sumUserItemQuantity(String userId, String itemId) {
        String owner = normalizeKeyword(userId);
        String target = normalizeKeyword(itemId);
        if (owner == null || target == null) {
            return 0;
        }
        long total = 0;
        int page = 1;
        while (true) {
            List<MallRedemption> batch = documents.findByField(REDEMPTIONS, "userId", owner, page, SCAN_PAGE_SIZE)
                    .stream().map(this::toRedemption).toList();
            for (MallRedemption redemption : batch) {
                if (redemption.itemId().equals(target) && redemption.status() != MallRedemptionStatus.CANCELLED) {
                    total += redemption.quantity();
                }
            }
            if (batch.size() < SCAN_PAGE_SIZE) {
                return total;
            }
            page++;
        }
    }

    private List<MallRedemption> sourceRedemptions(String userId) {
        String owner = normalizeKeyword(userId);
        if (owner == null) {
            return allRedemptions();
        }
        List<MallRedemption> result = new ArrayList<>();
        int page = 1;
        while (true) {
            List<MallRedemption> batch = documents.findByField(REDEMPTIONS, "userId", owner, page, SCAN_PAGE_SIZE)
                    .stream().map(this::toRedemption).toList();
            result.addAll(batch);
            if (batch.size() < SCAN_PAGE_SIZE) {
                return result;
            }
            page++;
        }
    }

    private List<MallRedemption> allRedemptions() {
        List<MallRedemption> result = new ArrayList<>();
        int page = 1;
        while (true) {
            List<MallRedemption> batch =
                    documents.findAll(REDEMPTIONS, page, SCAN_PAGE_SIZE).stream().map(this::toRedemption).toList();
            result.addAll(batch);
            if (batch.size() < SCAN_PAGE_SIZE) {
                return result;
            }
            page++;
        }
    }

    private List<MallRedemption> filterRedemptions(List<MallRedemption> source, String userId, String itemId,
                                                   MallRedemptionStatus status) {
        String owner = normalizeKeyword(userId);
        String target = normalizeKeyword(itemId);
        return source.stream()
                .filter(redemption -> owner == null || redemption.userId().equals(owner))
                .filter(redemption -> target == null || redemption.itemId().equals(target))
                .filter(redemption -> status == null || redemption.status() == status)
                .sorted(Comparator.comparingLong(MallRedemption::createdAt).reversed())
                .toList();
    }

    // ------------------------------------------------------------------ 设置

    @Override
    public MallSettings findSettings() {
        return documents.findById(SETTINGS, SETTINGS_ID).map(this::toSettings).orElseGet(MallSettings::defaults);
    }

    @Override
    public MallSettings saveSettings(MallSettings settings) {
        MallSettings safe = settings == null ? MallSettings.defaults() : settings.normalize();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", SETTINGS_ID);
        document.put("enabled", safe.enabled());
        document.put("notice", safe.notice());
        return toSettings(documents.save(SETTINGS, SETTINGS_ID, document));
    }

    // ------------------------------------------------------------------ 映射

    private Map<String, Object> itemDocument(MallItem item) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", item.id());
        document.put("name", item.name());
        document.put("description", item.description());
        document.put("imageUrl", item.imageUrl());
        document.put("assetCode", item.assetCode());
        document.put("pricePoints", item.pricePoints());
        document.put("stock", item.stock());
        document.put("perUserLimit", item.perUserLimit());
        document.put("enabled", item.enabled());
        document.put("sort", item.sort());
        document.put("createdAt", item.createdAt());
        document.put("updatedAt", item.updatedAt());
        return document;
    }

    private MallItem toItem(Map<String, Object> document) {
        return new MallItem(
                string(document, "id"),
                string(document, "name"),
                string(document, "description"),
                string(document, "imageUrl"),
                string(document, "assetCode"),
                number(document, "pricePoints", 0),
                integer(document, "stock", MallItem.UNLIMITED_STOCK),
                integer(document, "perUserLimit", MallItem.UNLIMITED_PER_USER),
                bool(document, "enabled", true),
                integer(document, "sort", 0),
                number(document, "createdAt", 0),
                number(document, "updatedAt", 0));
    }

    private Map<String, Object> redemptionDocument(MallRedemption redemption) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", redemption.id());
        document.put("itemId", redemption.itemId());
        document.put("itemName", redemption.itemName());
        document.put("userId", redemption.userId());
        document.put("assetCode", redemption.assetCode());
        document.put("quantity", redemption.quantity());
        document.put("unitPoints", redemption.unitPoints());
        document.put("totalPoints", redemption.totalPoints());
        document.put("status", redemption.status().name());
        document.put("remark", redemption.remark());
        if (!redemption.proofs().isEmpty()) {
            document.put("proofs", redemption.proofs().stream().map(this::proofDocument).toList());
        }
        document.put("deliveredByUserId", redemption.deliveredByUserId());
        document.put("deliveryNote", redemption.deliveryNote());
        document.put("deliveredAt", redemption.deliveredAt());
        document.put("confirmedAt", redemption.confirmedAt());
        document.put("cancelNote", redemption.cancelNote());
        document.put("createdAt", redemption.createdAt());
        document.put("updatedAt", redemption.updatedAt());
        return document;
    }

    private Map<String, Object> proofDocument(MallDeliveryProof proof) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("url", proof.url());
        document.put("filename", proof.filename());
        document.put("contentType", proof.contentType());
        document.put("size", proof.size());
        return document;
    }

    private MallRedemption toRedemption(Map<String, Object> document) {
        return new MallRedemption(
                string(document, "id"),
                string(document, "itemId"),
                string(document, "itemName"),
                string(document, "userId"),
                string(document, "assetCode"),
                integer(document, "quantity", 1),
                number(document, "unitPoints", 0),
                number(document, "totalPoints", 0),
                status(document),
                string(document, "remark"),
                proofList(document.get("proofs")),
                string(document, "deliveredByUserId"),
                string(document, "deliveryNote"),
                number(document, "deliveredAt", 0),
                number(document, "confirmedAt", 0),
                string(document, "cancelNote"),
                number(document, "createdAt", 0),
                number(document, "updatedAt", 0));
    }

    private List<MallDeliveryProof> proofList(Object value) {
        if (!(value instanceof List<?> rows)) {
            return List.of();
        }
        List<MallDeliveryProof> proofs = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> document = new LinkedHashMap<>();
            raw.forEach((key, item) -> document.put(String.valueOf(key), item));
            try {
                proofs.add(new MallDeliveryProof(string(document, "url"), string(document, "filename"),
                        string(document, "contentType"), number(document, "size", 0)));
            } catch (RuntimeException ignored) {
                // 地址损坏的凭证不值得让整条记录读不出来：跳过它，记录其余部分照常展示。
            }
        }
        return List.copyOf(proofs);
    }

    private MallSettings toSettings(Map<String, Object> document) {
        return new MallSettings(
                bool(document, "enabled", true),
                string(document, "notice"));
    }

    private MallRedemptionStatus status(Map<String, Object> document) {
        try {
            return MallRedemptionStatus.valueOf(string(document, "status").toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return MallRedemptionStatus.PENDING;
        }
    }

    private static boolean matches(String needle, String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String normalizeKeyword(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return text.isEmpty() ? null : text;
    }

    private static String string(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long number(Map<String, Object> document, String key, long defaultValue) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static int integer(Map<String, Object> document, String key, int defaultValue) {
        return (int) number(document, key, defaultValue);
    }

    private static boolean bool(Map<String, Object> document, String key, boolean defaultValue) {
        Object value = document.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null || String.valueOf(value).isBlank()
                ? defaultValue
                : Boolean.parseBoolean(String.valueOf(value).trim());
    }
}
