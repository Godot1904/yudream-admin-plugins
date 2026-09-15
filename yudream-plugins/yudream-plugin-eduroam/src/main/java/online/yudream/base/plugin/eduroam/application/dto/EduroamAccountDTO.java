package online.yudream.base.plugin.eduroam.application.dto;

/**
 * 登录过的 Eduroam 账号（管理端视图）。
 *
 * <p>{@code localUserId} 是该「本站邮箱」在本地对应的账号，为空表示站内还没有注册这个邮箱；
 * 有了它，管理员能一眼看出某个学号是「已注册但没绑上」还是「根本没来过本站」。
 */
public record EduroamAccountDTO(
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
