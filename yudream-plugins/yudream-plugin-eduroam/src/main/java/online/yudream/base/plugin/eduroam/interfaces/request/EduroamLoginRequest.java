package online.yudream.base.plugin.eduroam.interfaces.request;

/** 凭据页提交的登录请求：校园网账号 + 密码 + 宿主签发的 state。 */
public record EduroamLoginRequest(String account, String password, String state) {
}
