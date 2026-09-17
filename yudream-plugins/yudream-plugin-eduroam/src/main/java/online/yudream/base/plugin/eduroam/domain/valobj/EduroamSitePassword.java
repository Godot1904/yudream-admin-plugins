package online.yudream.base.plugin.eduroam.domain.valobj;

import java.nio.charset.StandardCharsets;

/** 新的本站密码由用户单独设置；限制 UTF-8 字节数，避免 BCrypt 静默截断。 */
public final class EduroamSitePassword {
    private EduroamSitePassword() {
    }

    public static void validate(String password, String confirmation) {
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("本站密码至少 8 个字符，且 UTF-8 编码不能超过 72 字节");
        }
        if (!password.equals(confirmation)) {
            throw new IllegalArgumentException("两次输入的本站密码不一致");
        }
    }
}
