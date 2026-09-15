package online.yudream.base.plugin.eduroam.interfaces.res;

/**
 * 登录过的 Eduroam 账号响应（管理端）。
 *
 * <p>{@code localUserId} 是该「本站邮箱」对应的本地账号，为空表示站内还没注册这个邮箱。
 */
public record EduroamAccountRes(
        String id,
        String email,
        String identity,
        String account,
        String domain,
        String status,
        String statusLabel,
        String lastLoginIp,
        long lastLoginAt,
        int loginCount,
        long firstLoginAt,
        String blockedByUserId,
        String blockReason,
        long blockedAt,
        String localUserId,
        String localUsername,
        String localNickname,
        long createdAt,
        long updatedAt
) {
}
