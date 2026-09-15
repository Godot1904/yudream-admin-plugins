package online.yudream.base.plugin.eduroam.domain.aggregate;

import online.yudream.base.plugin.eduroam.domain.enumerate.EduroamAccountStatus;

import java.util.Locale;

/**
 * 一个在本站登录过的 Eduroam 账号，以归一化邮箱为主键（一个邮箱最多一条记录）。
 *
 * <p>用途有两点：管理员能看到「谁用哪个校园网账号登录过、登录了几次」，以及在需要时<b>禁止</b>某个账号登录。
 * 密码只在认证请求期间使用，任何字段都不保存密码。
 *
 * <p>{@code identity} 是真正提交给 Eduroam 认证服务的账号（如 {@code 2023123456@example.edu.cn}），
 * {@code email} 是本站使用的邮箱（认证域与邮箱域不同时会不一样），两者都要留档以便对账。
 */
public record EduroamAccount(
        String id,
        String email,
        String identity,
        String account,
        String domain,
        EduroamAccountStatus status,
        String lastLoginIp,
        long lastLoginAt,
        int loginCount,
        long firstLoginAt,
        String blockedByUserId,
        String blockReason,
        long blockedAt,
        long createdAt,
        long updatedAt
) {

    public EduroamAccount {
        id = requireText(id, "账号记录 ID 不能为空");
        email = normalize(email);
        identity = text(identity);
        account = text(account);
        domain = normalize(domain);
        status = status == null ? EduroamAccountStatus.ACTIVE : status;
        lastLoginIp = text(lastLoginIp);
        blockedByUserId = text(blockedByUserId);
        blockReason = text(blockReason);
        if (loginCount < 0) {
            loginCount = 0;
        }
    }

    public static String idOf(String email) {
        return normalize(email);
    }

    /** 首次认证成功。 */
    public static EduroamAccount firstLogin(String email, String identity, String account, String domain,
                                            String clientIp, long now) {
        return new EduroamAccount(idOf(email), email, identity, account, domain, EduroamAccountStatus.ACTIVE,
                clientIp, now, 1, now, "", "", 0L, now, now);
    }

    /** 再次认证成功：刷新登录统计，保留封禁痕迹以外的一切。 */
    public EduroamAccount recordLogin(String identity, String account, String clientIp, long now) {
        return new EduroamAccount(id, email, identity, account, domain, status, clientIp, now, loginCount + 1,
                firstLoginAt, blockedByUserId, blockReason, blockedAt, createdAt, now);
    }

    /** 禁止登录：保留原因与操作人，之后即使密码正确也拒绝。 */
    public EduroamAccount block(String operatorUserId, String reason, long now) {
        if (status == EduroamAccountStatus.BLOCKED) {
            throw new IllegalArgumentException("该账号已经是禁止登录状态");
        }
        return new EduroamAccount(id, email, identity, account, domain, EduroamAccountStatus.BLOCKED, lastLoginIp,
                lastLoginAt, loginCount, firstLoginAt, text(operatorUserId), text(reason), now, createdAt, now);
    }

    /** 解除禁止。 */
    public EduroamAccount unblock(String operatorUserId, long now) {
        if (status == EduroamAccountStatus.ACTIVE) {
            throw new IllegalArgumentException("该账号当前未被禁止");
        }
        String note = operatorUserId == null || operatorUserId.isBlank() ? "" : "管理员 " + operatorUserId + " 解除";
        return new EduroamAccount(id, email, identity, account, domain, EduroamAccountStatus.ACTIVE, lastLoginIp,
                lastLoginAt, loginCount, firstLoginAt, "", note, 0L, createdAt, now);
    }

    public boolean active() {
        return status == EduroamAccountStatus.ACTIVE;
    }

    private static String requireText(String value, String message) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        return text(value).toLowerCase(Locale.ROOT);
    }
}
