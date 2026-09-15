package online.yudream.base.plugin.yggc.application.service;

/**
 * 「某位玩家的角色刚刚被用到」的通知口。
 *
 * <p>玩家创建角色发生在 yudream-skin 插件里，yggc 无法直接感知；因此这里退一步：
 * 玩家用启动器登录（authserver/authenticate）或进入服务器（sessionserver/join）时，
 * 由 {@code YggcAppService} 通知一次，同步器再把这位玩家名下的新角色立刻补推到 Union 主服务器。
 * 实现必须立即返回，真正的推送放到后台线程。
 */
public interface YggcProfileSyncTrigger {

    /** 玩家刚登录 / 刚进入服务器；userId 为宿主用户 ID。 */
    void profilesInUse(String userId);
}
