package online.yudream.base.plugin.eduroam.application.service;

import java.util.Optional;

/**
 * 站内账号查询端口：把「Eduroam 账号映射出的本站邮箱」对到本地账号。
 *
 * <p>管理端查询可降级；首次开户的存在性检查必须严格失败，不能把服务异常当成用户不存在。
 * 外部身份绑定仍由宿主按 {@code providerCode + type + socialUid} 管理。
 *
 * <p>抽成端口而不是直接用宿主 {@code FrameworkServices}：应用层不感知宿主用户服务的接口形状，
 * 单元测试也不必为此造一个几十个方法的假实现。
 */
public interface EduroamLocalUserPort {

    record LocalUser(String userId, String username, String nickname) {
    }

    Optional<LocalUser> findByEmail(String email);

    boolean existsByEmail(String email);

    /** 仅创建新用户，绝不更新已有账号或密码。重复邮箱必须拒绝。 */
    LocalUser create(String email, String password);
}
