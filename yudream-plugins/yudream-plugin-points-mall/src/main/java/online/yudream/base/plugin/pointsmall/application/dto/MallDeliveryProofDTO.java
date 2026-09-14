package online.yudream.base.plugin.pointsmall.application.dto;

/** 一张发放凭证：图片直接展示，其他类型给下载链接。 */
public record MallDeliveryProofDTO(String url, String filename, String contentType, long size, boolean image) {
}
