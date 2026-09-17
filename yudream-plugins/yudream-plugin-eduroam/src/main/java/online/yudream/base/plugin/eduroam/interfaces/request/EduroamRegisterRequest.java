package online.yudream.base.plugin.eduroam.interfaces.request;

/** 已通过校园认证的用户设置本站密码，不接受邮箱、用户 ID 或角色参数。 */
public record EduroamRegisterRequest(String ticket, String state, String password, String confirmPassword) {
    @Override
    public String toString() {
        return "EduroamRegisterRequest[redacted]";
    }
}
