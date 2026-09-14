package online.yudream.base.plugin.pointsmall.interfaces.res;

/** 商品响应；{@code assetName}/{@code assetSymbol} 是结算资产在钱包侧的名称与单位。 */
public record MallItemRes(
        String id,
        String name,
        String description,
        String imageUrl,
        String assetCode,
        String assetName,
        String assetSymbol,
        long pricePoints,
        int stock,
        int perUserLimit,
        boolean enabled,
        int sort,
        boolean available,
        long createdAt,
        long updatedAt
) {
}
