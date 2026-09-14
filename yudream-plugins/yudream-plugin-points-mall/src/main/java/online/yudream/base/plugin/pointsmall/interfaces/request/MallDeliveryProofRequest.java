package online.yudream.base.plugin.pointsmall.interfaces.request;

/** 一张发放凭证：地址来自上传接口，其余字段用于展示与审计。 */
public record MallDeliveryProofRequest(String url, String filename, String contentType, Long size) {
}
