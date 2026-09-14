package online.yudream.base.plugin.pointsmall.application.dto;

/** 用户端概览里的一项资产余额：在架商品用到的资产各占一行。 */
public record MallAssetBalanceDTO(String code, String name, String symbol, String balance) {
}
