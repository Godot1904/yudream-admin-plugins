package online.yudream.base.plugin.pointsmall.interfaces.res;

/** 结算资产选项响应。 */
public record MallAssetOptionRes(
        String code,
        String name,
        String symbol,
        int scale,
        boolean enabled,
        boolean money
) {
}
