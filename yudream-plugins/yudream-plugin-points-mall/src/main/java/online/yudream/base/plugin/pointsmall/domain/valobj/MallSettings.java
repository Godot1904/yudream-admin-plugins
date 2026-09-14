package online.yudream.base.plugin.pointsmall.domain.valobj;

/**
 * 积分商城的全局设置。
 *
 * <p>结算资产不在这里：不同商品可以用不同资产结算，资产随商品保存（见
 * {@code MallItem.assetCode()}），设置只保留商城开关与兑换须知。
 */
public record MallSettings(boolean enabled, String notice) {

    private static final int MAX_NOTICE_LENGTH = 2000;

    public MallSettings {
        notice = truncate(notice);
    }

    public static MallSettings defaults() {
        return new MallSettings(true, "");
    }

    public MallSettings normalize() {
        return new MallSettings(enabled, notice);
    }

    private static String truncate(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= MAX_NOTICE_LENGTH ? text : text.substring(0, MAX_NOTICE_LENGTH);
    }
}
