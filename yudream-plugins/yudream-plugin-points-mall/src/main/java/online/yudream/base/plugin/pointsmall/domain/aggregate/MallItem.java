package online.yudream.base.plugin.pointsmall.domain.aggregate;

import java.util.Locale;
import java.util.UUID;

/**
 * 积分商城的一件商品。
 *
 * <p>{@code assetCode} 是这件商品的结算资产（钱包里的资产代码）：积分商城允许不同商品用不同资产结算，
 * 例如普通积分与活动代币。它随商品保存，兑换时写进兑换记录做快照。
 *
 * <p>{@code assetCode} 只在 {@link #create} 里强制要求：老版本（结算资产还挂在商城设置上）留下的商品没有
 * 这个字段，重新读出来时必须能读到对象，否则一条历史数据就能让整个商品列表接口报错。这类商品由应用层
 * 提示管理员补选资产，用户端显示为不可兑换。
 *
 * <p>{@code stock} 为 {@link #UNLIMITED_STOCK} 表示不限量，为 0 表示已兑完；{@code perUserLimit} 为
 * {@link #UNLIMITED_PER_USER} 表示每人限兑不限。两者都只在这里判定，接口层不再重复实现同一套规则。
 */
public record MallItem(
        String id,
        String name,
        String description,
        String imageUrl,
        String assetCode,
        long pricePoints,
        int stock,
        int perUserLimit,
        boolean enabled,
        int sort,
        long createdAt,
        long updatedAt
) {

    /** 不限量库存。 */
    public static final int UNLIMITED_STOCK = -1;
    /** 不限每人限兑件数。 */
    public static final int UNLIMITED_PER_USER = 0;
    /** 单次兑换的最大件数，避免一次把积分与库存刷成溢出值。 */
    public static final int MAX_QUANTITY = 999;
    private static final long MAX_PRICE_POINTS = 1_000_000_000L;
    private static final int MAX_IMAGE_URL_LENGTH = 1000;

    public MallItem {
        id = requireText(id, "商品 ID 不能为空");
        name = requireText(name, "商品名称不能为空");
        description = text(description);
        imageUrl = normalizeImageUrl(imageUrl);
        assetCode = normalizeAssetCode(assetCode);
        if (pricePoints < 0) {
            throw new IllegalArgumentException("所需积分不能为负");
        }
        if (pricePoints > MAX_PRICE_POINTS) {
            throw new IllegalArgumentException("所需积分过大");
        }
        if (stock < UNLIMITED_STOCK) {
            throw new IllegalArgumentException("库存不能小于 -1（-1 表示不限量）");
        }
        if (perUserLimit < UNLIMITED_PER_USER) {
            throw new IllegalArgumentException("每人限兑不能为负（0 表示不限）");
        }
        if (sort < 0) {
            sort = 0;
        }
    }

    public static MallItem create(String name, String description, String imageUrl, String assetCode, long pricePoints,
                                  int stock, int perUserLimit, boolean enabled, int sort) {
        if (text(assetCode).isEmpty()) {
            throw new IllegalArgumentException("请选择结算资产");
        }
        long now = System.currentTimeMillis();
        return new MallItem(UUID.randomUUID().toString(), name, description, imageUrl, assetCode, pricePoints, stock,
                perUserLimit, enabled, sort, now, now);
    }

    public MallItem update(String name, String description, String imageUrl, String assetCode, long pricePoints,
                           int stock, int perUserLimit, boolean enabled, int sort) {
        return new MallItem(id, name, description, imageUrl, assetCode, pricePoints, stock, perUserLimit, enabled, sort,
                createdAt, System.currentTimeMillis());
    }

    public MallItem withEnabled(boolean nextEnabled) {
        return new MallItem(id, name, description, imageUrl, assetCode, pricePoints, stock, perUserLimit, nextEnabled,
                sort, createdAt, System.currentTimeMillis());
    }

    public boolean unlimitedStock() {
        return stock == UNLIMITED_STOCK;
    }

    public boolean soldOut() {
        return !unlimitedStock() && stock <= 0;
    }

    /** 是否还能被兑换：已上架且还有库存。 */
    public boolean available() {
        return enabled && !soldOut();
    }

    /**
     * 校验一次兑换能否成立。
     *
     * <p>不校验余额与每人限兑：前者属于钱包，后者需要仓储里的历史件数，都属于应用层。
     */
    public void requireAvailable(int quantity) {
        if (!enabled) {
            throw new IllegalArgumentException("商品已下架");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("兑换数量至少为 1 件");
        }
        if (quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("单次兑换最多 " + MAX_QUANTITY + " 件");
        }
        if (soldOut()) {
            throw new IllegalArgumentException("商品已兑完");
        }
        if (!unlimitedStock() && stock < quantity) {
            throw new IllegalArgumentException("库存不足，仅剩 " + stock + " 件");
        }
    }

    /** 扣减库存；不限量商品只更新修改时间。 */
    public MallItem deduct(int quantity) {
        if (unlimitedStock()) {
            return new MallItem(id, name, description, imageUrl, assetCode, pricePoints, stock, perUserLimit, enabled,
                    sort, createdAt, System.currentTimeMillis());
        }
        return new MallItem(id, name, description, imageUrl, assetCode, pricePoints, Math.max(stock - quantity, 0),
                perUserLimit, enabled, sort, createdAt, System.currentTimeMillis());
    }

    /** 归还库存；不限量商品只更新修改时间。 */
    public MallItem restore(int quantity) {
        if (unlimitedStock()) {
            return new MallItem(id, name, description, imageUrl, assetCode, pricePoints, stock, perUserLimit, enabled,
                    sort, createdAt, System.currentTimeMillis());
        }
        return new MallItem(id, name, description, imageUrl, assetCode, pricePoints, stock + Math.max(quantity, 0),
                perUserLimit, enabled, sort, createdAt, System.currentTimeMillis());
    }

    /** 一次兑换需要的积分总额。 */
    public long totalPoints(int quantity) {
        return Math.multiplyExact(pricePoints, quantity);
    }

    /** 资产代码只做归一化（去空白、转大写）：是否必须填写由 {@link #create} 与应用层把关。 */
    private static String normalizeAssetCode(String value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    /** 封面图存对象存储，这里只保留地址：去掉宿主域名前缀，换部署时历史地址仍然可用。 */
    private static String normalizeImageUrl(String value) {
        String text = text(value)
                .replaceAll("(?i)^https?://[^/]+(?=/api/files/)", "")
                .replaceAll("(?i)^/proxy(?=/api/files/)", "");
        if (text.length() > MAX_IMAGE_URL_LENGTH) {
            throw new IllegalArgumentException("封面图地址过长");
        }
        return text;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String requireText(String value, String message) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }
}
