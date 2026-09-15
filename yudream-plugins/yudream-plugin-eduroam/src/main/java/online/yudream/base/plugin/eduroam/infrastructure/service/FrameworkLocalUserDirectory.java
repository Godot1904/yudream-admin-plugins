package online.yudream.base.plugin.eduroam.infrastructure.service;

import online.yudream.base.plugin.eduroam.application.service.EduroamLocalUserPort;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;

import java.util.Optional;

/**
 * 用宿主用户服务实现站内账号查询（SPI {@code FrameworkServices.users().findByEmail}）。
 *
 * <p>只做一次只读查询，且异常一律吞掉返回空：台账页面不该因为用户服务的一次抖动而整页打不开，
 * 「查不到」与「没有账号」在展示上等价（都显示未注册）。
 */
public class FrameworkLocalUserDirectory implements EduroamLocalUserPort {

    private final FrameworkServices framework;

    public FrameworkLocalUserDirectory(FrameworkServices framework) {
        this.framework = framework;
    }

    @Override
    public Optional<LocalUser> findByEmail(String email) {
        String target = email == null ? "" : email.trim();
        if (target.isEmpty()) {
            return Optional.empty();
        }
        try {
            return framework.users().findByEmail(target)
                    .map(FrameworkLocalUserDirectory::toLocalUser);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static LocalUser toLocalUser(PluginUserProfile profile) {
        return new LocalUser(
                profile.id() == null ? "" : String.valueOf(profile.id()),
                profile.username() == null ? "" : profile.username(),
                profile.nickname() == null ? "" : profile.nickname()
        );
    }
}
