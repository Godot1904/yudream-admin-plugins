package online.yudream.base.plugin.pointsmall.domain.valobj;

import java.util.Locale;

/**
 * 发放凭证：管理员发放兑换时上传的图片，存在宿主的对象存储（S3 桶）里，这里只留展示与审计需要的字段。
 *
 * <p>地址与商品封面一样去掉宿主域名前缀，换部署后历史凭证仍然能打开。
 */
public record MallDeliveryProof(String url, String filename, String contentType, long size) {

    private static final int MAX_URL_LENGTH = 1000;
    private static final int MAX_FILENAME_LENGTH = 200;

    public MallDeliveryProof {
        url = normalizeUrl(url);
        filename = truncate(filename, MAX_FILENAME_LENGTH);
        contentType = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        size = Math.max(size, 0);
    }

    /** 是否是可预览的图片；非图片在界面上只给下载链接。 */
    public boolean image() {
        return contentType.startsWith("image/");
    }

    private static String normalizeUrl(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("凭证地址不能为空");
        }
        text = text.replaceAll("(?i)^https?://[^/]+(?=/api/files/)", "")
                .replaceAll("(?i)^/proxy(?=/api/files/)", "");
        if (text.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("凭证地址过长");
        }
        return text;
    }

    private static String truncate(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
