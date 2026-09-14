package online.yudream.base.plugin.pointsmall.infrastructure;

import online.yudream.base.plugin.pointsmall.domain.aggregate.MallItem;
import online.yudream.base.plugin.pointsmall.domain.aggregate.MallRedemption;
import online.yudream.base.plugin.pointsmall.domain.enumerate.MallRedemptionStatus;
import online.yudream.base.plugin.pointsmall.domain.repo.MallRepository;
import online.yudream.base.plugin.pointsmall.domain.valobj.MallSettings;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 测试用的内存仓储：语义与文档仓储保持一致（关键词、状态、时间倒序、分页切片）。 */
public class FakeMallRepository implements MallRepository {

    private final Map<String, MallItem> items = new LinkedHashMap<>();
    private final Map<String, MallRedemption> redemptions = new LinkedHashMap<>();
    private MallSettings settings = MallSettings.defaults();

    @Override
    public MallItem saveItem(MallItem item) {
        items.put(item.id(), item);
        return item;
    }

    @Override
    public Optional<MallItem> findItem(String itemId) {
        return Optional.ofNullable(items.get(itemId));
    }

    @Override
    public List<MallItem> listItems(String keyword, Boolean enabled, int page, int size) {
        return filterItems(keyword, enabled).stream()
                .skip((long) (Math.max(page, 1) - 1) * Math.max(size, 1))
                .limit(Math.max(size, 1))
                .toList();
    }

    @Override
    public long countItems(String keyword, Boolean enabled) {
        return filterItems(keyword, enabled).size();
    }

    @Override
    public List<String> listOpenItemAssetCodes() {
        return items.values().stream()
                .filter(MallItem::enabled)
                .sorted(Comparator.comparingInt(MallItem::sort))
                .map(MallItem::assetCode)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public void deleteItem(String itemId) {
        items.remove(itemId);
    }

    @Override
    public MallRedemption saveRedemption(MallRedemption redemption) {
        redemptions.put(redemption.id(), redemption);
        return redemption;
    }

    @Override
    public Optional<MallRedemption> findRedemption(String redemptionId) {
        return Optional.ofNullable(redemptions.get(redemptionId));
    }

    @Override
    public List<MallRedemption> listRedemptions(String userId, String itemId, MallRedemptionStatus status, int page,
                                                int size) {
        return filterRedemptions(userId, itemId, status).stream()
                .skip((long) (Math.max(page, 1) - 1) * Math.max(size, 1))
                .limit(Math.max(size, 1))
                .toList();
    }

    @Override
    public long countRedemptions(String userId, String itemId, MallRedemptionStatus status) {
        return filterRedemptions(userId, itemId, status).size();
    }

    @Override
    public long countItemRedemptions(String itemId) {
        return redemptions.values().stream().filter(redemption -> redemption.itemId().equals(itemId)).count();
    }

    @Override
    public long sumUserItemQuantity(String userId, String itemId) {
        return redemptions.values().stream()
                .filter(redemption -> redemption.userId().equals(userId))
                .filter(redemption -> redemption.itemId().equals(itemId))
                .filter(redemption -> redemption.status() != MallRedemptionStatus.CANCELLED)
                .mapToLong(MallRedemption::quantity)
                .sum();
    }

    @Override
    public MallSettings findSettings() {
        return settings;
    }

    @Override
    public MallSettings saveSettings(MallSettings settings) {
        this.settings = settings == null ? MallSettings.defaults() : settings.normalize();
        return this.settings;
    }

    private List<MallItem> filterItems(String keyword, Boolean enabled) {
        String needle = normalize(keyword);
        return items.values().stream()
                .filter(item -> enabled == null || item.enabled() == enabled)
                .filter(item -> needle == null
                        || item.name().toLowerCase(Locale.ROOT).contains(needle)
                        || item.description().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparingInt(MallItem::sort)
                        .thenComparing(Comparator.comparingLong(MallItem::createdAt).reversed()))
                .toList();
    }

    private List<MallRedemption> filterRedemptions(String userId, String itemId, MallRedemptionStatus status) {
        return redemptions.values().stream()
                .filter(redemption -> userId == null || redemption.userId().equals(userId))
                .filter(redemption -> itemId == null || redemption.itemId().equals(itemId))
                .filter(redemption -> status == null || redemption.status() == status)
                .sorted(Comparator.comparingLong(MallRedemption::createdAt).reversed())
                .toList();
    }

    private static String normalize(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return text.isEmpty() ? null : text;
    }
}
