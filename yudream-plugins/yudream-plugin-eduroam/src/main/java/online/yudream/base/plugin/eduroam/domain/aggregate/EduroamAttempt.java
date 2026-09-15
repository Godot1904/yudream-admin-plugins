package online.yudream.base.plugin.eduroam.domain.aggregate;

import java.util.UUID;

/**
 * 一次核验尝试的审计记录（成功与失败都记）。
 *
 * <p>用途有三：管理员排查「为什么某个同学一直失败」、按 IP/账号统计异常、以及在缺少日志的部署里还原时间线。
 * 密码、完整上游响应都不入库：只存改写过的短摘要。
 */
public record EduroamAttempt(
        String id,
        String email,
        String identity,
        String account,
        String domain,
        boolean success,
        String reasonCode,
        String message,
        String detail,
        String clientIp,
        long latencyMs,
        long createdAt
) {

    private static final int MAX_DETAIL_LENGTH = 400;

    public EduroamAttempt {
        id = text(id).isEmpty() ? UUID.randomUUID().toString() : text(id);
        email = text(email).toLowerCase(java.util.Locale.ROOT);
        identity = text(identity);
        account = text(account);
        domain = text(domain).toLowerCase(java.util.Locale.ROOT);
        reasonCode = text(reasonCode);
        message = text(message);
        detail = cut(text(detail), MAX_DETAIL_LENGTH);
        clientIp = text(clientIp);
        if (latencyMs < 0) {
            latencyMs = 0;
        }
    }

    public static EduroamAttempt of(String email, String identity, String account, String domain,
                                    boolean success, String reasonCode, String message, String detail,
                                    String clientIp, long latencyMs, long now) {
        return new EduroamAttempt(null, email, identity, account, domain, success, reasonCode, message,
                detail, clientIp, latencyMs, now);
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
