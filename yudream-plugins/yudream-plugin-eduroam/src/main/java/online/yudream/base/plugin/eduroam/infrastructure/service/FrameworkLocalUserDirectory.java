package online.yudream.base.plugin.eduroam.infrastructure.service;

import online.yudream.base.plugin.eduroam.application.service.EduroamLocalUserPort;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.spi.system.user.PluginUserCreate;

import java.util.Optional;

/**
 * 通过已发布的用户 SPI 查询与创建站内账号。
 *
 * <p>只有台账展示查询可降级。开户查询与写入异常必须中止，不记录宿主原始异常或密码。
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

    @Override
    public boolean existsByEmail(String email) {
        try {
            return framework.users().findByEmail(email).isPresent();
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("暂时无法查询本站账号，请稍后重新认证");
        }
    }

    @Override
    public LocalUser create(String email, String password) {
        // 使用完整映射邮箱作用户名，避免不同学校同学号冲突。身份已由校园认证证明。
        // 密码编码、普通用户角色与默认部门由宿主的 create 契约完成。
        if (existsByEmail(email)) {
            throw new IllegalArgumentException("本站账号已存在，请使用已有密码登录完成绑定");
        }
        try {
            if (framework.users().findByUsername(email).isPresent()) {
                throw new IllegalArgumentException("本站账号名称已被使用");
            }
            PluginUserProfile profile = framework.users().create(new PluginUserCreate(
                    email, email.substring(0, email.indexOf('@')), email, null, null, password, null, true));
            if (profile == null || profile.id() == null) {
                throw new IllegalArgumentException("宿主未返回创建结果");
            }
            return toLocalUser(profile);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("创建本站账号未完成，请重新认证；如账号已创建，请使用刚设置的密码登录");
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
