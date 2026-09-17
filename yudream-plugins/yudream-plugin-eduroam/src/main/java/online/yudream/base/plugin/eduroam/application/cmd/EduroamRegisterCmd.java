package online.yudream.base.plugin.eduroam.application.cmd;

/** 邮箱和身份只能从认证票据取得，客户端只提交新的本站密码。 */
public record EduroamRegisterCmd(String ticket, String state, String password, String confirmPassword) {
    @Override
    public String toString() {
        return "EduroamRegisterCmd[redacted]";
    }
}
