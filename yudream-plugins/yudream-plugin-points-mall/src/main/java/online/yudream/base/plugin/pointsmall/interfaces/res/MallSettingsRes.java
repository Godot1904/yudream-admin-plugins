package online.yudream.base.plugin.pointsmall.interfaces.res;

/** 商城设置响应：商城开关与兑换须知。结算资产按商品设置。 */
public record MallSettingsRes(boolean enabled, String notice) {
}
