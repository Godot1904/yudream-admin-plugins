package online.yudream.base.plugin.pointsmall.interfaces.res;

/** 一张发放凭证：图片直接展示，其他类型给下载链接。 */
public record MallDeliveryProofRes(String url, String filename, String contentType, long size, boolean image) {
}
