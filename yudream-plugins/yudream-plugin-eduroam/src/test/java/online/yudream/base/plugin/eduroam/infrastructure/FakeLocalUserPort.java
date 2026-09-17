package online.yudream.base.plugin.eduroam.infrastructure;

import online.yudream.base.plugin.eduroam.application.service.EduroamLocalUserPort;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 测试用站内账号目录：按邮箱预置本地账号，用于验证台账里的「是否已注册」。 */
public class FakeLocalUserPort implements EduroamLocalUserPort {

    private final Map<String, LocalUser> users = new LinkedHashMap<>();
    public int createCount;
    public String createdPassword;
    public boolean unavailable;

    public FakeLocalUserPort register(String email, String userId, String username, String nickname) {
        users.put(email.trim().toLowerCase(Locale.ROOT), new LocalUser(userId, username, nickname));
        return this;
    }

    @Override
    public Optional<LocalUser> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(users.get(email.trim().toLowerCase(Locale.ROOT)));
    }

    @Override
    public boolean existsByEmail(String email) {
        if (unavailable) {
            throw new IllegalArgumentException("用户服务不可用");
        }
        return findByEmail(email).isPresent();
    }

    @Override
    public LocalUser create(String email, String password) {
        if (existsByEmail(email)) {
            throw new IllegalArgumentException("账号已存在");
        }
        createCount++;
        createdPassword = password;
        register(email, "357806992028471296", email, email.substring(0, email.indexOf('@')));
        return users.get(email);
    }
}
