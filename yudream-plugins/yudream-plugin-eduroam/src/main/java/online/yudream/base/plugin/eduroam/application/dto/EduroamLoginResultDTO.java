package online.yudream.base.plugin.eduroam.application.dto;

/**
 * 凭据认证结果。
 *
 * <p>成功时返回 {@code ticket}：凭据页带着它回宿主的第三方登录回调端点，由宿主调用
 * {@code exchange} 换取外部身份并完成登录/绑定；失败时给出原因码与可直接展示的文案。
 */
public record EduroamLoginResultDTO(
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
