package online.yudream.base.plugin.eduroam.application.cmd;

/**
 * 保存渠道配置。字段为 null 表示「不修改」，便于前端只提交改动的部分。
 */
public record EduroamSettingsSaveCmd(
        Boolean enabled,
        String eduDomain,
        String storeDomain,
        String verifyEndpoint,
        Integer connectTimeoutSeconds,
        Integer requestTimeoutSeconds,
        Integer maxAttemptsPerHour,
        String tutorialMarkdown
) {
}
