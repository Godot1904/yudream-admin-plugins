package online.yudream.base.plugin.pointsmall.application.cmd;

/** 一张发放凭证：上传接口返回的地址与文件信息。 */
public record MallDeliveryProofCmd(String url, String filename, String contentType, Long size) {
}
