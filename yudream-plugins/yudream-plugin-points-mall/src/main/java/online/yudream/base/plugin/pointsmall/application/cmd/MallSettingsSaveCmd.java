package online.yudream.base.plugin.pointsmall.application.cmd;

/** 保存商城设置：商城开关与兑换须知。结算资产按商品设置，不在这里。 */
public record MallSettingsSaveCmd(Boolean enabled, String notice) {
}
