package online.yudream.base.plugin.eduroam.interfaces.request;

/** 保存渠道配置；null 字段表示不修改。 */
public record EduroamSettingsSaveRequest(
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
