package online.yudream.base.plugin.eduroam.infrastructure;

import online.yudream.base.plugin.eduroam.application.service.EduroamLocalUserPort;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 测试用站内账号目录：按邮箱预置本地账号，用于验证台账里的「是否已注册」。 */
public class FakeLocalUserPort implements EduroamLocalUserPort {

    private final Map<String, LocalUser> users = new LinkedHashMap<>();

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
}
