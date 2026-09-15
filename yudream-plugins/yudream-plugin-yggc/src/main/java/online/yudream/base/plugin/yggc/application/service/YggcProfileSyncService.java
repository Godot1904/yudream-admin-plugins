package online.yudream.base.plugin.yggc.application.service;

import online.yudream.base.plugin.skin.api.PluginSkinProfile;
import online.yudream.base.plugin.skin.api.PluginSkinService;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.user.PluginUserOption;
import online.yudream.base.plugin.yggc.domain.aggregate.YggcSettings;
import online.yudream.base.plugin.yggc.domain.valobj.YggcProfileDelta;
import online.yudream.base.plugin.yggc.infrastructure.repository.YggcRepository;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcUnionClient;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcUnionClient.UnionResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 角色 → Union 主服务器同步：把本站的角色（uuid + 角色名）推送给主服务器，让跨站查询与同名占用
 * 能覆盖到本站。
 *
 * <p>三条入口：
 * <ul>
 *   <li>{@link #fullSync()}：POST /sync 一次性提交全部角色（管理端「全量同步角色」与主服务器回调）；</li>
 *   <li>{@link #reconcile()}：与上次推送成功的快照做差异，只补推新增 / 改名 / 删除的条目（定时任务与「立即对账」）；</li>
 *   <li>{@link #pushProfilesOf(String)}：某位玩家登录时，只检查他本人的角色，把新建好的角色立刻补推上去。</li>
 * </ul>
 *
 * <p>快照存于插件文档集合的 {@code profile-sync} 文档：只有推送成功的条目才会写入快照，
 * 失败的条目留在「待补推」状态，下一轮自动重试。扫描失败时绝不更新快照，避免把一次故障
 * 误判成「角色被删光」而删掉主服务器上的索引。
 */
public class YggcProfileSyncService {

    private static final String SKIN_PLUGIN_CODE = "yudream-skin";
    /** union state 集合中的快照文档 id。 */
    private static final String STATE_ID = "profile-sync";
    private static final String PUSHED = "pushed";
    private static final int USER_PAGE_SIZE = 100;
    private static final Logger LOG = Logger.getLogger(YggcProfileSyncService.class.getName());

    private final PluginContext context;
    private final YggcRepository repository;
    private final YggcSettingsService settingsService;
    private final YggcUnionClient unionClient;
    /** 同步期间独占：定时任务、管理端点击与登录补推不会互相踩到快照。 */
    private final ReentrantLock lock = new ReentrantLock();

    public YggcProfileSyncService(PluginContext context, YggcRepository repository,
                                  YggcSettingsService settingsService, YggcUnionClient unionClient) {
        this.context = context;
        this.repository = repository;
        this.settingsService = settingsService;
        this.unionClient = unionClient;
    }

    // ---- 对外入口 ----

    /** 全量推送：POST /sync {profileList}，成功后把结果记为新的推送快照。 */
    public Map<String, Object> fullSync() {
        YggcSettings settings = settingsService.current();
        requireReady(settings, "全量同步角色");
        if (!lock.tryLock()) {
            throw new IllegalArgumentException("角色同步正在进行，请稍后再试");
        }
        try {
            Map<String, String> local = collectProfiles();
            return pushAll(local, "full");
        } finally {
            lock.unlock();
        }
    }

    /** 增量对账：只推送与快照有差异的角色；首次运行（无快照）回落为一次全量推送。 */
    public Map<String, Object> reconcile() {
        YggcSettings settings = settingsService.current();
        requireReady(settings, "增量同步角色");
        if (!lock.tryLock()) {
            return busyResult();
        }
        try {
            Map<String, String> local = collectProfiles();
            Map<String, String> snapshot = pushedProfiles();
            if (snapshot.isEmpty() && !local.isEmpty()) {
                // 没有基线可比（本功能上线后第一次运行），逐条 POST 会打爆上游，直接全量一次到位。
                return pushAll(local, "bootstrap");
            }
            YggcProfileDelta delta = YggcProfileDelta.between(snapshot, local);
            if (delta.empty()) {
                Map<String, Object> result = summary("delta", 0, 0, 0, 0, local.size());
                result.put("message", "本地角色与主服务器一致，无需推送");
                saveState(snapshot, "delta", result, null);
                return result;
            }
            Map<String, String> updated = new LinkedHashMap<>(snapshot);
            int failed = 0;
            for (YggcProfileDelta.Entry entry : delta.added()) {
                if (pushNew(entry.uuid(), entry.name())) {
                    updated.put(entry.uuid(), entry.name());
                } else {
                    failed++;
                }
            }
            for (YggcProfileDelta.Rename rename : delta.renamed()) {
                if (pushRename(rename.uuid(), rename.to())) {
                    updated.put(rename.uuid(), rename.to());
                } else {
                    failed++;
                }
            }
            for (String uuid : delta.removed()) {
                if (pushRemove(uuid)) {
                    updated.remove(uuid);
                } else {
                    failed++;
                }
            }
            Map<String, Object> result = summary("delta", delta.added().size(), delta.renamed().size(),
                    delta.removed().size(), failed, updated.size());
            result.put("message", failed == 0
                    ? "增量同步完成：新增 " + delta.added().size() + "、改名 " + delta.renamed().size()
                    + "、删除 " + delta.removed().size()
                    : "增量同步部分失败：" + failed + " 个条目将在下一轮重试");
            saveState(updated, "delta", result, failed == 0 ? null
                    : failed + " 个角色推送失败（上游不可达或返回错误），将在下一轮重试；详见服务端日志");
            LOG.info("[yggc] 角色增量同步：" + result.get("message"));
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 玩家登录时的定向补推：只处理这位玩家的角色，且只补新增 / 改名，从不删除。
     * 任何失败都只记日志——登录流程不能被跨站同步拖垮。
     */
    public void pushProfilesOf(String userId) {
        YggcSettings settings = settingsService.current();
        if (!ready(settings) || !settings.unionSyncOnLogin()) {
            return;
        }
        List<PluginSkinProfile> profiles = profilesOf(userId);
        if (profiles.isEmpty() || !lock.tryLock()) {
            return;
        }
        try {
            Map<String, String> snapshot = pushedProfiles();
            Map<String, String> updated = new LinkedHashMap<>(snapshot);
            int added = 0;
            int renamed = 0;
            int failed = 0;
            for (PluginSkinProfile profile : profiles) {
                String previous = snapshot.get(profile.uuid());
                if (profile.name().equals(previous)) {
                    continue;
                }
                boolean isNew = previous == null;
                boolean ok = isNew ? pushNew(profile.uuid(), profile.name())
                        : pushRename(profile.uuid(), profile.name());
                if (ok) {
                    updated.put(profile.uuid(), profile.name());
                    if (isNew) {
                        added++;
                    } else {
                        renamed++;
                    }
                } else {
                    failed++;
                }
            }
            if (added == 0 && renamed == 0 && failed == 0) {
                return;
            }
            Map<String, Object> result = summary("login", added, renamed, 0, failed, updated.size());
            result.put("message", failed == 0
                    ? "玩家登录补推完成：新增 " + added + "、改名 " + renamed
                    : "玩家登录补推部分失败：" + failed + " 个条目将在下一轮重试");
            saveState(updated, "login", result, failed == 0 ? null
                    : failed + " 个角色补推失败（上游不可达或返回错误），将在下一轮重试；详见服务端日志");
            LOG.info("[yggc] " + result.get("message") + "（userId=" + userId + "）");
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[yggc] 玩家登录补推角色失败：userId=" + userId, e);
        } finally {
            lock.unlock();
        }
    }

    /** 推送快照 + 上次结果（管理端展示；不发上游请求）。 */
    public Map<String, Object> state() {
        YggcSettings settings = settingsService.current();
        Map<String, Object> document = repository.findUnionState(STATE_ID).orElseGet(LinkedHashMap::new);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", settings.unionSyncEnabled());
        body.put("intervalMinutes", settings.unionSyncIntervalMinutes());
        body.put("onLoginPush", settings.unionSyncOnLogin());
        body.put("ready", ready(settings));
        body.put("running", lock.isLocked());
        body.put("pushedCount", pushedProfiles().size());
        body.put("syncedAt", document.get("syncedAt"));
        body.put("mode", document.get("mode"));
        body.put("lastResult", document.get("lastResult") == null ? Map.of() : document.get("lastResult"));
        body.put("lastError", document.get("lastError"));
        return body;
    }

    /** 定时任务据此判断本轮是否需要执行。 */
    public boolean intervalDue(long lastRunAt) {
        long intervalMs = Math.max(1L, settingsService.current().unionSyncIntervalMinutes()) * 60_000L;
        return System.currentTimeMillis() - lastRunAt >= intervalMs;
    }

    /** Union API Root 与 Member Key 是否都已配置：未配置时后台任务静默跳过，不刷失败日志。 */
    public boolean ready() {
        return ready(settingsService.current());
    }

    // ---- 内部 ----

    private Map<String, Object> pushAll(Map<String, String> local, String mode) {
        YggcSettings settings = settingsService.current();
        UnionResult result = unionClient.post(settings.unionApiRoot(), "/sync",
                Map.of("profileList", local), settings.unionMemberKey());
        if (!result.ok()) {
            String message = "全量角色同步失败：" + YggcUnionClient.failureMessage(result);
            recordError(message);
            throw new IllegalArgumentException(message);
        }
        Map<String, Object> body = summary(mode, local.size(), 0, 0, 0, local.size());
        body.put("profileCount", local.size());
        body.put("status", result.status());
        body.put("response", result.json() != null ? result.json() : result.body());
        body.put("message", "已向 Union 主服务器推送 " + local.size() + " 个角色");
        saveState(local, mode, body, null);
        LOG.info("[yggc] 全量角色同步完成：" + local.size() + " 个角色");
        return body;
    }

    private boolean pushNew(String uuid, String name) {
        YggcSettings settings = settingsService.current();
        UnionResult result = unionClient.post(settings.unionApiRoot(), "/profile",
                Map.of("id", uuid, "name", name), settings.unionMemberKey());
        if (!result.ok()) {
            LOG.warning("[yggc] 推送角色失败：" + name + "（" + uuid + "）→ " + YggcUnionClient.failureMessage(result));
        }
        return result.ok();
    }

    private boolean pushRename(String uuid, String name) {
        YggcSettings settings = settingsService.current();
        UnionResult result = unionClient.put(settings.unionApiRoot(),
                "/profile/" + YggcUnionClient.encode(uuid), Map.of("name", name), settings.unionMemberKey());
        if (!result.ok()) {
            LOG.warning("[yggc] 更新角色名失败：" + name + "（" + uuid + "）→ " + YggcUnionClient.failureMessage(result));
        }
        return result.ok();
    }

    private boolean pushRemove(String uuid) {
        YggcSettings settings = settingsService.current();
        UnionResult result = unionClient.delete(settings.unionApiRoot(),
                "/profile/" + YggcUnionClient.encode(uuid), settings.unionMemberKey());
        if (!result.ok()) {
            LOG.warning("[yggc] 删除上游角色失败：" + uuid + " → " + YggcUnionClient.failureMessage(result));
        }
        return result.ok();
    }

    /**
     * 扫描本站全部角色。任何一次用户 / 角色读取失败都向上抛出：
     * 调用方据此放弃本轮对账，绝不把不完整的扫描结果当成「角色已被删除」。
     */
    private Map<String, String> collectProfiles() {
        Map<String, String> profiles = new LinkedHashMap<>();
        int page = 1;
        while (true) {
            List<PluginUserOption> users = context.framework().users()
                    .searchUsers("", null, page, USER_PAGE_SIZE);
            for (PluginUserOption user : users) {
                for (PluginSkinProfile profile : skinService().findProfilesByOwner(user.id())) {
                    profiles.put(profile.uuid(), profile.name());
                }
            }
            if (users.size() < USER_PAGE_SIZE) {
                return profiles;
            }
            page++;
        }
    }

    /** 单个用户的角色；读取失败按「没有角色」处理，避免影响登录流程。 */
    private List<PluginSkinProfile> profilesOf(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        try {
            return skinService().findProfilesByOwner(userId.trim());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[yggc] 读取用户角色失败：userId=" + userId, e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> pushedProfiles() {
        Map<String, Object> document = repository.findUnionState(STATE_ID).orElse(null);
        if (document == null) {
            return new LinkedHashMap<>();
        }
        Object raw = document.get(PUSHED);
        if (!(raw instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, String> pushed = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null && value != null) {
                pushed.put(String.valueOf(key), String.valueOf(value));
            }
        });
        return pushed;
    }

    private void saveState(Map<String, String> pushed, String mode, Map<String, Object> result, String error) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(PUSHED, new LinkedHashMap<>(pushed));
        document.put("pushedCount", pushed.size());
        document.put("syncedAt", System.currentTimeMillis());
        document.put("mode", mode);
        document.put("lastResult", result);
        if (error != null) {
            document.put("lastError", error);
        }
        repository.saveUnionState(STATE_ID, document);
    }

    /** 上游不可用等致命失败：只记录，不改动快照，避免把失败当成「角色已删除」。 */
    private void recordError(String message) {
        Map<String, Object> document = new LinkedHashMap<>(
                repository.findUnionState(STATE_ID).orElseGet(LinkedHashMap::new));
        document.put("lastError", message);
        document.put("lastErrorAt", System.currentTimeMillis());
        repository.saveUnionState(STATE_ID, document);
    }

    private Map<String, Object> summary(String mode, int added, int renamed, int removed, int failed, int pushed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        body.put("added", added);
        body.put("renamed", renamed);
        body.put("removed", removed);
        body.put("failed", failed);
        body.put("pushedCount", pushed);
        body.put("syncedAt", System.currentTimeMillis());
        return body;
    }

    private Map<String, Object> busyResult() {
        Map<String, Object> body = summary("busy", 0, 0, 0, 0, pushedProfiles().size());
        body.put("message", "角色同步正在进行，请稍后再试");
        return body;
    }

    private void requireReady(YggcSettings settings, String action) {
        if (!ready(settings)) {
            throw new IllegalArgumentException("未配置 Union API Root 或 Member Key，无法" + action);
        }
    }

    private boolean ready(YggcSettings settings) {
        return settings.unionApiRoot() != null && !settings.unionApiRoot().isBlank()
                && settings.unionMemberKey() != null && !settings.unionMemberKey().isBlank();
    }

    private PluginSkinService skinService() {
        return context.service(SKIN_PLUGIN_CODE, PluginSkinService.class)
                .orElseThrow(() -> new IllegalArgumentException("yudream-skin 插件未启用"));
    }
}
