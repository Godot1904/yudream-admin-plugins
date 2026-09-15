package online.yudream.base.plugin.yggc.domain.aggregate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 插件配置项，字段与默认值对齐原 yggdrasil-connect（Union 分支）的 Option 配置：
 * 常规配置（ygg_*）、Restore API（ygg_restore_api）、
 * Yggdrasil Connect（ygg_connect_server_url / ygg_disable_authserver）、
 * Union 相关配置（union_api_root / union_member_key / union_enable_update）。
 * 另外补充了 Yggdrasil Connect 令牌有效期（原插件由 Janus 侧配置，此处内置）。
 */
public record YggcSettings(
        /** UUID 生成算法：v3（离线 UUID）| v4（随机） */
        String uuidAlgorithm,
        /** 传统令牌暂时失效时间（秒），对应 ygg_token_expire_1 */
        long tokenExpire,
        /** 传统令牌完全失效时间（秒），对应 ygg_token_expire_2 */
        long tokenRefreshExpire,
        /** 每个用户令牌数量上限，对应 ygg_tokens_limit */
        int tokensLimit,
        /** 登录 / 登出频率限制（毫秒），对应 ygg_rate_limit */
        long rateLimit,
        /** 额外皮肤白名单域名（半角逗号分隔），对应 ygg_skin_domain */
        String skinDomain,
        /** 批量查询角色数量上限，对应 ygg_search_profile_max */
        int searchProfileMax,
        /** 用户中心显示快速配置板块，对应 ygg_show_config_section */
        boolean showConfigSection,
        /** 开启 API 地址指示（ALI），对应 ygg_enable_ali */
        boolean enableAli,
        /** 启用 Restore API，对应 ygg_restore_api */
        boolean restoreApi,
        /** 禁用 Auth Server（传统用户名密码登录），对应 ygg_disable_authserver */
        boolean disableAuthserver,
        /** OpenID 提供者标识符（Yggdrasil Connect 服务根地址），对应 ygg_connect_server_url */
        String connectServerUrl,
        /** Union API Root，对应 union_api_root */
        String unionApiRoot,
        /** Union Member Key，对应 union_member_key */
        String unionMemberKey,
        /** 允许 Union 数据自动更新，对应 union_enable_update */
        boolean unionEnableUpdate,
        /** 启用 Union OAuth2（允许 Union 主服务器通过本站登录），对应 union_enable_oauth2 */
        boolean unionEnableOauth2,
        /** 定期把本站角色同步到 Union 主服务器（增量对账） */
        boolean unionSyncEnabled,
        /** 角色同步间隔（分钟），仅在 unionSyncEnabled 打开时生效 */
        int unionSyncIntervalMinutes,
        /** 玩家登录（启动器认证 / 进入服务器）时顺带补推他本人的新角色 */
        boolean unionSyncOnLogin,
        /** OAuth 访问令牌有效期（秒） */
        long oauthAccessTtl,
        /** OAuth 刷新令牌有效期（秒） */
        long oauthRefreshTtl,
        /** 设备授权码有效期（秒） */
        long oauthDeviceTtl,
        /** 认证服务器名称（Yggdrasil metadata 的 meta.serverName，展示于启动器），留空则回退站点名 */
        String serverName
) {

    public static final String ALGORITHM_V3 = "v3";
    public static final String ALGORITHM_V4 = "v4";
    public static final String DEFAULT_UNION_API_ROOT = "https://skin.mualliance.ltd/api/union";
    /** 角色同步间隔的合法区间（分钟）。 */
    public static final int MIN_SYNC_INTERVAL_MINUTES = 1;
    public static final int MAX_SYNC_INTERVAL_MINUTES = 1440;

    private static final long MINUTE = 60L;
    private static final long DAY = 86400L;

    public static YggcSettings defaults() {
        return new YggcSettings(
                ALGORITHM_V3,
                259200L,
                604800L,
                10,
                1000L,
                "",
                5,
                true,
                true,
                false,
                false,
                "",
                DEFAULT_UNION_API_ROOT,
                "",
                true,
                false,
                true,
                10,
                true,
                604800L,
                2592000L,
                600L,
                ""
        );
    }

    public static YggcSettings from(Map<String, Object> document) {
        YggcSettings defaults = defaults();
        if (document == null || document.isEmpty()) {
            return defaults;
        }
        return new YggcSettings(
                text(document, "uuidAlgorithm", defaults.uuidAlgorithm),
                number(document, "tokenExpire", defaults.tokenExpire),
                number(document, "tokenRefreshExpire", defaults.tokenRefreshExpire),
                (int) number(document, "tokensLimit", defaults.tokensLimit),
                number(document, "rateLimit", defaults.rateLimit),
                text(document, "skinDomain", defaults.skinDomain),
                (int) number(document, "searchProfileMax", defaults.searchProfileMax),
                bool(document, "showConfigSection", defaults.showConfigSection),
                bool(document, "enableAli", defaults.enableAli),
                bool(document, "restoreApi", defaults.restoreApi),
                bool(document, "disableAuthserver", defaults.disableAuthserver),
                text(document, "connectServerUrl", defaults.connectServerUrl),
                text(document, "unionApiRoot", defaults.unionApiRoot),
                text(document, "unionMemberKey", defaults.unionMemberKey),
                bool(document, "unionEnableUpdate", defaults.unionEnableUpdate),
                bool(document, "unionEnableOauth2", defaults.unionEnableOauth2),
                bool(document, "unionSyncEnabled", defaults.unionSyncEnabled),
                (int) number(document, "unionSyncIntervalMinutes", defaults.unionSyncIntervalMinutes),
                bool(document, "unionSyncOnLogin", defaults.unionSyncOnLogin),
                number(document, "oauthAccessTtl", defaults.oauthAccessTtl),
                number(document, "oauthRefreshTtl", defaults.oauthRefreshTtl),
                number(document, "oauthDeviceTtl", defaults.oauthDeviceTtl),
                text(document, "serverName", defaults.serverName)
        ).normalized();
    }

    /** 越界值收敛到合法区间（保存前也会做一次强校验）。 */
    public YggcSettings normalized() {
        return new YggcSettings(
                ALGORITHM_V4.equalsIgnoreCase(cut(uuidAlgorithm, 8)) ? ALGORITHM_V4 : ALGORITHM_V3,
                clamp(tokenExpire, MINUTE, 365L * DAY, 259200L),
                clamp(tokenRefreshExpire, MINUTE, 365L * DAY, 604800L),
                (int) clamp(tokensLimit, 1L, 1000L, 10L),
                clamp(rateLimit, 0L, 600000L, 1000L),
                cut(skinDomain, 2000),
                (int) clamp(searchProfileMax, 1L, 100L, 5L),
                showConfigSection,
                enableAli,
                restoreApi,
                disableAuthserver,
                endpoint(connectServerUrl),
                endpoint(unionApiRoot).isBlank() ? DEFAULT_UNION_API_ROOT : endpoint(unionApiRoot),
                cut(unionMemberKey, 512),
                unionEnableUpdate,
                unionEnableOauth2,
                unionSyncEnabled,
                (int) clamp(unionSyncIntervalMinutes, MIN_SYNC_INTERVAL_MINUTES, MAX_SYNC_INTERVAL_MINUTES, 10L),
                unionSyncOnLogin,
                clamp(oauthAccessTtl, 5L * MINUTE, 365L * DAY, 604800L),
                clamp(oauthRefreshTtl, 5L * MINUTE, 365L * DAY, 2592000L),
                clamp(oauthDeviceTtl, MINUTE, DAY, 600L),
                cut(serverName, 64)
        );
    }

    /**
     * 轮换 Union Member Key：主服务器下发新密钥时使用。
     * 用具名方法而不是逐个位置构造，避免以后增删字段时静默串位。
     */
    public YggcSettings withUnionMemberKey(String newMemberKey) {
        return new YggcSettings(
                uuidAlgorithm, tokenExpire, tokenRefreshExpire, tokensLimit, rateLimit, skinDomain,
                searchProfileMax, showConfigSection, enableAli, restoreApi, disableAuthserver,
                connectServerUrl, unionApiRoot, cut(newMemberKey, 512), unionEnableUpdate,
                unionEnableOauth2, unionSyncEnabled, unionSyncIntervalMinutes, unionSyncOnLogin,
                oauthAccessTtl, oauthRefreshTtl, oauthDeviceTtl, serverName
        );
    }

    /** 额外皮肤白名单域名（已去空、去重、去掉协议与路径）。 */
    public List<String> extraSkinDomains() {
        List<String> domains = new ArrayList<>();
        for (String item : cut(skinDomain, 2000).split(",")) {
            String domain = item.trim();
            if (domain.isEmpty()) {
                continue;
            }
            domain = domain.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
            int slash = domain.indexOf('/');
            if (slash > -1) {
                domain = domain.substring(0, slash);
            }
            if (!domains.contains(domain)) {
                domains.add(domain);
            }
        }
        return domains;
    }

    /** OAuth / OIDC 的 issuer 覆盖值；未配置时返回空串，由调用方回退到本站 API 地址。 */
    public String oauthIssuerFallback() {
        return endpoint(connectServerUrl);
    }

    public Map<String, Object> toDocument() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("uuidAlgorithm", uuidAlgorithm);
        document.put("tokenExpire", tokenExpire);
        document.put("tokenRefreshExpire", tokenRefreshExpire);
        document.put("tokensLimit", tokensLimit);
        document.put("rateLimit", rateLimit);
        document.put("skinDomain", skinDomain);
        document.put("searchProfileMax", searchProfileMax);
        document.put("showConfigSection", showConfigSection);
        document.put("enableAli", enableAli);
        document.put("restoreApi", restoreApi);
        document.put("disableAuthserver", disableAuthserver);
        document.put("connectServerUrl", connectServerUrl);
        document.put("unionApiRoot", unionApiRoot);
        document.put("unionMemberKey", unionMemberKey);
        document.put("unionEnableUpdate", unionEnableUpdate);
        document.put("unionEnableOauth2", unionEnableOauth2);
        document.put("unionSyncEnabled", unionSyncEnabled);
        document.put("unionSyncIntervalMinutes", unionSyncIntervalMinutes);
        document.put("unionSyncOnLogin", unionSyncOnLogin);
        document.put("oauthAccessTtl", oauthAccessTtl);
        document.put("oauthRefreshTtl", oauthRefreshTtl);
        document.put("oauthDeviceTtl", oauthDeviceTtl);
        document.put("serverName", serverName);
        return document;
    }

    private static long clamp(long value, long min, long max, long fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.min(Math.max(value, min), max);
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /** 规范化站点地址：去掉结尾斜杠，仅接受 http(s) 绝对地址，非法则返回空串。 */
    private static String endpoint(String value) {
        String trimmed = cut(value, 500);
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "";
        }
        return trimmed;
    }

    private static String text(Map<String, Object> document, String key, String fallback) {
        Object value = document.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static long number(Map<String, Object> document, String key, long fallback) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean bool(Map<String, Object> document, String key, boolean fallback) {
        Object value = document.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(text);
    }
}
