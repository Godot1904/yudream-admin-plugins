package online.yudream.base.plugin.yggc.api;

import java.util.List;

/**
 * yggc 对外的游戏角色与会话签发端口。
 *
 * <p>供以 {@code authlib-injector} 插件 code 部署的兼容补丁插件等可信调用方调用，
 * 把站点身份「提升」为玩家在 ygg 上的可用会话。信任根 = 调用方已持有有效站点会话
 * 并由宿主解析出 userId；yggc 只负责按 userId 签发。
 *
 * <p>该端口仅暴露免密签发能力，不复用 {@code authenticate} 的密码校验路径，
 * 避免把站点密码校验流程开放给插件间调用方。
 */
public interface PluginYggcAuthService {

    /** 列出该站点用户可登录的游戏角色。 */
    List<PluginYggcProfile> listProfiles(String userId);

    /**
     * 为指定站点用户签发一条 Yggdrasil 会话。
     *
     * @param userId               站点用户主键（不可为空）
     * @param clientToken          客户端令牌（为空时自动生成）
     * @param requestedProfileName 期望选中的角色名；为空或不存在时取首个可用角色
     * @return 新的会话快照
     * @throws IllegalArgumentException userId 为空，或该用户没有可用角色
     */
    IssuedSession issueSession(String userId, String clientToken, String requestedProfileName);

    /** 可选登录角色。 */
    record PluginYggcProfile(String id, String name) {
    }

    /** 会话签发结果。 */
    record IssuedSession(
            String userId,
            String username,
            String profileId,
            String accessToken,
            String clientToken
    ) {
    }
}
