package online.yudream.base.plugin.pointsmall.application.cmd;

/** 用户兑换商品。{@code quantity} 为空按 1 件处理。 */
public record MallRedeemCmd(String itemId, Integer quantity, String remark) {
}
