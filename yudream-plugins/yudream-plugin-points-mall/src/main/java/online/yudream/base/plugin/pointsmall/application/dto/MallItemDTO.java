package online.yudream.base.plugin.pointsmall.application.dto;

/** 商品视图。{@code available} 由领域判定，前端不再重复算「是否还能兑」。 */
public record MallItemDTO(
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
