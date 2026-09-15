package online.yudream.base.plugin.eduroam.interfaces.res;

/** 核验尝试响应（审计）。 */
public record EduroamAttemptRes(
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
