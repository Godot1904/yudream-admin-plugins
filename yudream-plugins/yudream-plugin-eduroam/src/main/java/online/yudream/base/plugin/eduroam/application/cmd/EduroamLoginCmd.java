package online.yudream.base.plugin.eduroam.application.cmd;

/**
 * 凭据页提交的登录请求。
 *
 * <p>{@code state} 是宿主签发的一次性登录关联票据，凭据正确时会写进交接票据，回调时由宿主核对。
 * 密码只随本次命令传递，不入库。
 */
public record EduroamLoginCmd(String account, String password, String state) {
}
