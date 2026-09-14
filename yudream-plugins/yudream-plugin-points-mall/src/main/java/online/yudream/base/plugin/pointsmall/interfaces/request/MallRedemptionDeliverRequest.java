package online.yudream.base.plugin.pointsmall.interfaces.request;

/** 管理员发放兑换：发放备注 + 至少一张凭证（上传接口返回的地址与文件信息）。 */
public record MallRedemptionDeliverRequest(String note, java.util.List<MallDeliveryProofRequest> files) {
}
