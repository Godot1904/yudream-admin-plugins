package online.yudream.base.plugin.pointsmall.interfaces.request;

/** 用户兑换请求体。不含用户 ID：兑换人只能是当前登录人。 */
public record MallRedeemRequest(String itemId, Integer quantity, String remark) {
}
