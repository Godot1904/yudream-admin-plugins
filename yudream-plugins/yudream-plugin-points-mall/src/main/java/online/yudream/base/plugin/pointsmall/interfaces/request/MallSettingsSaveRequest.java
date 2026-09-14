package online.yudream.base.plugin.pointsmall.interfaces.request;

/** 保存商城设置的请求体：商城开关与兑换须知。结算资产按商品设置。 */
public record MallSettingsSaveRequest(Boolean enabled, String notice) {
}
