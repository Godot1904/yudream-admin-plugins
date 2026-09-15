package online.yudream.base.plugin.eduroam.interfaces.res;

/** 凭据认证结果响应：成功时带上一次性登录票据，凭据页据此回宿主回调端点。 */
public record EduroamLoginResultRes(
        boolean success,
        String email,
        String identity,
        String account,
        String ticket,
        long expiresAt,
        String reasonCode,
        String message
) {
}
