package online.yudream.base.plugin.eduroam.application.dto;

/** 渠道配置（管理端读写；仅在管理端暴露认证服务地址）。 */
public record EduroamSettingsDTO(
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
