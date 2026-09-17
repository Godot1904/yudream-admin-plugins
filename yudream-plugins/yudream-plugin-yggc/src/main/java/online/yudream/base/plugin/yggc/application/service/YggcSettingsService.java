package online.yudream.base.plugin.yggc.application.service;

import online.yudream.base.plugin.yggc.domain.aggregate.YggcSettings;
import online.yudream.base.plugin.yggc.infrastructure.repository.YggcRepository;

/**
 * 插件配置读写：进程内缓存 + 保存时校验。
 * 未保存过配置时使用与原插件一致的默认值，首次读取即落库，便于后台展示。
 */
public class YggcSettingsService {

    private static final long DAY = 86400L;

    private final YggcRepository repository;
    private volatile YggcSettings cached;

    public YggcSettingsService(YggcRepository repository) {
        this.repository = repository;
    }

    public YggcSettings current() {
        YggcSettings snapshot = cached;
        if (snapshot != null) {
            return snapshot;
        }
        YggcSettings loaded = repository.settings()
                .map(YggcSettings::normalized)
                .orElseGet(YggcSettings::defaults);
        cached = loaded;
        return loaded;
    }

    /** 保存配置：先强校验，再规范化落库。 */
    public YggcSettings save(YggcSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("配置内容不能为空");
        }
        validate(settings);
        YggcSettings saved = repository.saveSettings(settings.normalized());
        cached = saved;
        return saved;
    }

    public YggcSettings resetToDefaults() {
        return save(YggcSettings.defaults());
    }

    public void invalidate() {
        cached = null;
    }

    private void validate(YggcSettings settings) {
        if (settings.tokenExpire() <= 0) {
            throw new IllegalArgumentException("令牌暂时失效时间必须大于 0 秒");
        }
        if (settings.tokenRefreshExpire() <= 0) {
            throw new IllegalArgumentException("令牌完全失效时间必须大于 0 秒");
        }
        if (settings.tokenRefreshExpire() < settings.tokenExpire()) {
            throw new IllegalArgumentException("令牌完全失效时间不能小于令牌暂时失效时间");
        }
        if (settings.tokenExpire() > 365L * DAY || settings.tokenRefreshExpire() > 365L * DAY) {
            throw new IllegalArgumentException("令牌有效期不能超过 365 天");
        }
        if (settings.tokensLimit() < 1 || settings.tokensLimit() > 1000) {
            throw new IllegalArgumentException("令牌数量限制需在 1 - 1000 之间");
        }
        if (settings.rateLimit() < 0 || settings.rateLimit() > 600000) {
            throw new IllegalArgumentException("频率限制需在 0 - 600000 毫秒之间（0 表示不限制）");
        }
        if (settings.searchProfileMax() < 1 || settings.searchProfileMax() > 100) {
            throw new IllegalArgumentException("批量查询角色数量限制需在 1 - 100 之间");
        }
        String issuer = settings.connectServerUrl();
        if (!issuer.isBlank() && !issuer.startsWith("http://") && !issuer.startsWith("https://")) {
            throw new IllegalArgumentException("OpenID 提供者标识符必须以 http:// 或 https:// 开头");
        }
        String unionRoot = settings.unionApiRoot();
        if (!unionRoot.isBlank() && !unionRoot.startsWith("http://") && !unionRoot.startsWith("https://")) {
            throw new IllegalArgumentException("Union API Root 必须以 http:// 或 https:// 开头");
        }
        if (settings.oauthAccessTtl() <= 0 || settings.oauthRefreshTtl() <= 0 || settings.oauthDeviceTtl() <= 0) {
            throw new IllegalArgumentException("Yggdrasil Connect 令牌有效期必须大于 0 秒");
        }
        if (settings.oauthRefreshTtl() < settings.oauthAccessTtl()) {
            throw new IllegalArgumentException("Refresh Token 有效期不能小于 Access Token 有效期");
        }
        validateSharedClientId(settings.sharedClientId());
    }

    /**
     * 共享客户端（发现文档 shared_client_id）必须是已存在、启用中的公共客户端。
     * 设备流不校验 client_secret，而机密客户端又无法共享密钥，因此只允许公共客户端。
     */
    private void validateSharedClientId(String sharedClientId) {
        if (sharedClientId == null || sharedClientId.isBlank()) {
            return;
        }
        var client = repository.findClient(sharedClientId.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "共享客户端不存在：" + sharedClientId + "，请先在「OAuth 应用管理」里创建公共客户端"));
        if (!client.publicClient()) {
            throw new IllegalArgumentException("共享客户端必须是公共客户端（无 client_secret），"
                    + "否则启动器无法在不携带密钥的情况下完成设备流登录");
        }
        if (!client.enabled()) {
            throw new IllegalArgumentException("共享客户端已被禁用，请先在「OAuth 应用管理」里启用或改绑其他应用");
        }
    }
}
