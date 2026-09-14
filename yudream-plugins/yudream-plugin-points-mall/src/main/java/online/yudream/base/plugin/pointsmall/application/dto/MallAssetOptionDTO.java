package online.yudream.base.plugin.pointsmall.application.dto;

/** 结算资产选项：来自钱包插件实际的资产列表，管理员在设置页里选，不手写代码。 */
public record MallAssetOptionDTO(
        String code,
        String name,
        String symbol,
        int scale,
        boolean enabled,
        boolean money
) {
}
