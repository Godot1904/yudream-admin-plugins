package online.yudream.base.plugin.yggc.infrastructure.service;

import online.yudream.base.plugin.yggc.application.service.YggcProfileSyncService;
import online.yudream.base.plugin.yggc.application.service.YggcProfileSyncTrigger;
import online.yudream.base.plugin.yggc.application.service.YggcSettingsService;
import online.yudream.base.plugin.yggc.domain.aggregate.YggcSettings;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 角色同步的后台调度：单线程、守护线程，全部工作串行执行。
 *
 * <ul>
 *   <li>每 {@link #TICK_SECONDS} 秒醒来一次，按配置的间隔（分钟）决定是否执行一次增量对账；</li>
 *   <li>玩家登录 / 进入服务器时接收 {@link #profilesInUse(String)}，把「补推这位玩家的新角色」排进同一个线程，
 *       因此不会和定时对账并发写快照，也不会阻塞登录请求。</li>
 * </ul>
 *
 * <p>关闭由插件生命周期负责（{@code context.onDispose(scheduler)}），禁用 / 卸载时线程立即释放。
 */
public class YggcUnionProfileSyncScheduler implements YggcProfileSyncTrigger, AutoCloseable {

    /** 检查间隔：真正的同步间隔以配置为准，这里只决定「多久看一次表」。 */
    private static final long TICK_SECONDS = 60L;
    private static final Logger LOG = Logger.getLogger(YggcUnionProfileSyncScheduler.class.getName());

    private final YggcSettingsService settingsService;
    private final YggcProfileSyncService profileSyncService;
    private final ScheduledExecutorService executor;
    /** 已排队但尚未处理的玩家，避免同一玩家连续登录把队列堆满。 */
    private final Set<String> pendingUsers = ConcurrentHashMap.newKeySet();

    private volatile long lastRunAt;

    public YggcUnionProfileSyncScheduler(YggcSettingsService settingsService,
                                         YggcProfileSyncService profileSyncService) {
        this.settingsService = settingsService;
        this.profileSyncService = profileSyncService;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "yggc-union-profile-sync");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 启动定时对账；延迟一个周期再跑第一次，避免插件启用瞬间打扰上游。 */
    public void start() {
        executor.scheduleWithFixedDelay(this::tick, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
        LOG.info("[yggc] 角色定时同步已启动（检查间隔 " + TICK_SECONDS + " 秒）");
    }

    @Override
    public void profilesInUse(String userId) {
        if (userId == null || userId.isBlank() || !settingsService.current().unionSyncOnLogin()) {
            return;
        }
        // 未配置 Member Key 时不做无谓的排队：补推一定失败，等管理员配好再说。
        if (!profileSyncService.ready()) {
            return;
        }
        if (!pendingUsers.add(userId)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    profileSyncService.pushProfilesOf(userId);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "[yggc] 登录补推角色异常：userId=" + userId, e);
                } finally {
                    pendingUsers.remove(userId);
                }
            });
        } catch (RejectedExecutionException e) {
            pendingUsers.remove(userId);
        }
    }

    private void tick() {
        try {
            YggcSettings settings = settingsService.current();
            if (!settings.unionSyncEnabled()) {
                return;
            }
            if (!profileSyncService.ready()) {
                // 没配 Member Key 时静默跳过：默认开启的定时任务不应该在没有联盟配置的站点上刷警告。
                return;
            }
            if (!profileSyncService.intervalDue(lastRunAt)) {
                return;
            }
            // 先记录本轮时间：同步失败也等下个间隔再试，不要每 60 秒重试一次。
            lastRunAt = System.currentTimeMillis();
            profileSyncService.reconcile();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[yggc] 角色定时同步失败", e);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        LOG.info("[yggc] 角色定时同步已停止");
    }
}
