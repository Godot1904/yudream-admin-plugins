package online.yudream.base.plugin.pointsmall.application.cmd;

/**
 * 管理员新建或修改商品。空值一律表示「不改动 / 用默认」，由应用层决定默认值。
 *
 * <p>{@code assetCode} 是这件商品的结算资产；新建时必填，修改时留空表示沿用原资产。
 */
public record MallItemSaveCmd(
        String name,
        String description,
        String imageUrl,
        String assetCode,
        Long pricePoints,
        Integer stock,
        Integer perUserLimit,
        Boolean enabled,
        Integer sort
) {
}
