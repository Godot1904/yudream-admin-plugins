package online.yudream.base.plugin.pointsmall.interfaces.res;

import java.util.List;

/**
 * 用户端商城概览响应。
 *
 * <p>不同商品可以挂不同结算资产，所以 {@code assets} 是一个列表：在架商品用到的每种资产各一行余额。
 * 余额用字符串传输，避免 JSON 数字被前端当成浮点处理。
 */
public record MallOverviewRes(
        boolean enabled,
        List<MallAssetBalanceRes> assets,
        long itemCount,
        long pendingCount,
        long deliveredCount,
        long totalCount,
        String notice
) {
}
