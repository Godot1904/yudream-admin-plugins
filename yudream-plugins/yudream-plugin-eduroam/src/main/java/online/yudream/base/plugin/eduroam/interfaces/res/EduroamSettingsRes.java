package online.yudream.base.plugin.eduroam.interfaces.res;

/** 渠道配置响应（管理端）。 */
public record EduroamSettingsRes(
        boolean enabled,
        String eduDomain,
        String storeDomain,
        String verifyEndpoint,
        int connectTimeoutSeconds,
        int requestTimeoutSeconds,
        int maxAttemptsPerHour,
        String tutorialMarkdown
) {
}
