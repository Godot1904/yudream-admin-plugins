package online.yudream.base.plugin.eduroam.application.service;

import java.util.Optional;

/**
 * 站内账号查询端口：把「Eduroam 账号映射出的本站邮箱」对到本地账号。
 *
 * <p>第三方登录本身不需要它——宿主按 {@code providerCode + type + socialUid} 绑定，与邮箱无关。
 * 它的用途是让管理端台账把「这个学号对应本站哪个账号、还是压根没注册」摆出来，
 * 这样学校无线域与邮箱域不一致时（本站邮箱域配置才有意义），管理员处理绑定类工单不用再手工比对。
 *
 * <p>抽成端口而不是直接用宿主 {@code FrameworkServices}：应用层不感知宿主用户服务的接口形状，
 * 单元测试也不必为此造一个几十个方法的假实现。
 */
public interface EduroamLocalUserPort {

    record LocalUser(String userId, String username, String nickname) {
    }

    Optional<LocalUser> findByEmail(String email);
}
