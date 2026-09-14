package online.yudream.base.plugin.pointsmall.application.dto;

import java.util.List;

/**
 * 用户端商城概览：在架商品用到的各种资产余额、可兑换商品数、我的兑换数，以及商城是否开放与兑换须知。
 *
 * <p>只包含当前登录人自己的聚合值，不含任何其他用户的统计。资产可能不止一种（不同商品可以挂不同资产），
 * 所以这里是一个列表而不是单个余额。
 */
public record MallOverviewDTO(
        boolean enabled,
        List<MallAssetBalanceDTO> assets,
        long itemCount,
        long pendingCount,
        long deliveredCount,
        long totalCount,
        String notice
) {
}
