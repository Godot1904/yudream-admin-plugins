package online.yudream.base.plugin.pointsmall.interfaces.request;

/** 取消兑换的请求体；用户端与管理端共用，原因会写进记录并展示。 */
public record MallRedemptionCancelRequest(String reason) {
}
