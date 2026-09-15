package online.yudream.base.plugin.eduroam.application.dto;

/** 一次核验尝试（审计）。 */
public record EduroamAttemptDTO(
        String id,
        String email,
        String identity,
        String account,
        String domain,
        boolean success,
        String reasonCode,
        String reasonLabel,
        String message,
        String detail,
        String clientIp,
        long latencyMs,
        long createdAt
) {
}
