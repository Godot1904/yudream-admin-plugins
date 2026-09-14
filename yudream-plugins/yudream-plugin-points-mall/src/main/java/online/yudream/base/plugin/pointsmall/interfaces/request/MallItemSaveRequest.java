package online.yudream.base.plugin.pointsmall.interfaces.request;

/**
 * 管理员保存商品的请求体；空字段表示不改动（新建时用默认值）。
 *
 * <p>{@code imageUrl} 是上传接口返回的封面地址（存宿主对象存储），{@code assetCode} 是这件商品的结算资产。
 */
public record MallItemSaveRequest(
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
