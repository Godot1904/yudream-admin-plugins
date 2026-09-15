package online.yudream.base.plugin.eduroam.domain.aggregate;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Eduroam 第三方登录渠道配置。
 *
 * <p>字段语义对齐原 Blessing Skin auth-eduroam 插件的环境变量：{@code eduDomain} 对应
 * {@code EDUROAM_HOST}（用户名后自动补的认证域），{@code storeDomain} 对应
 * {@code EDUROAM_STORE_HOST}（真正入库的邮箱域）。两者可以不同，因此学校无线域与邮箱域不一致时也能用。
 *
 * <p>{@code maxAttemptsPerHour} 按客户端 IP 计算，用于挡住暴力试密码。
 */
public record EduroamSettings(
        boolean enabled,
        String eduDomain,
        String storeDomain,
        String verifyEndpoint,
        int connectTimeoutSeconds,
        int requestTimeoutSeconds,
        int maxAttemptsPerHour,
        String tutorialMarkdown
) {

    public static final String DEFAULT_ENDPOINT = "https://eduroam.ustc.edu.cn/cgi-bin/eduroam-test.cgi";
    public static final int MAX_DOMAIN_LENGTH = 120;
    private static final int MAX_TUTORIAL_LENGTH = 4000;
    private static final int MAX_ENDPOINT_LENGTH = 500;

    public static final String DEFAULT_TUTORIAL = """
            1. 先连接学校无线网络，或任何已开启 Eduroam 漫游的校园网（无需回到本校）。
            2. 账号填校园网/Eduroam 登录名：本校一般只填学号或工号；若提示需要完整账号，请填 `学号@学校域名`。
            3. 密码即校园网/Eduroam 密码，与网上办事大厅密码通常一致。
            4. 校验通过后会自动回到本站登录流程：首次登录需要把该 Eduroam 账号绑定到本站账号，绑定后再次登录可直接进入。
            """;

    public EduroamSettings {
        eduDomain = domain(eduDomain);
        storeDomain = domain(storeDomain);
        verifyEndpoint = endpoint(verifyEndpoint);
        connectTimeoutSeconds = clamp(connectTimeoutSeconds, 2, 60, 5);
        requestTimeoutSeconds = clamp(requestTimeoutSeconds, 5, 120, 20);
        maxAttemptsPerHour = clamp(maxAttemptsPerHour, 1, 200, 10);
        tutorialMarkdown = cut(tutorialMarkdown, MAX_TUTORIAL_LENGTH);
    }

    public static EduroamSettings defaults() {
        return new EduroamSettings(true, "", "", DEFAULT_ENDPOINT, 5, 20, 10, DEFAULT_TUTORIAL);
    }

    public static EduroamSettings from(Map<String, Object> document) {
        EduroamSettings defaults = defaults();
        if (document == null || document.isEmpty()) {
            return defaults;
        }
        return new EduroamSettings(
                bool(document, "enabled", defaults.enabled),
                text(document, "eduDomain", defaults.eduDomain),
                text(document, "storeDomain", defaults.storeDomain),
                text(document, "verifyEndpoint", defaults.verifyEndpoint),
                integer(document, "connectTimeoutSeconds", defaults.connectTimeoutSeconds),
                integer(document, "requestTimeoutSeconds", defaults.requestTimeoutSeconds),
                integer(document, "maxAttemptsPerHour", defaults.maxAttemptsPerHour),
                text(document, "tutorialMarkdown", defaults.tutorialMarkdown)
        );
    }

    public Map<String, Object> toDocument() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("enabled", enabled);
        document.put("eduDomain", eduDomain);
        document.put("storeDomain", storeDomain);
        document.put("verifyEndpoint", verifyEndpoint);
        document.put("connectTimeoutSeconds", connectTimeoutSeconds);
        document.put("requestTimeoutSeconds", requestTimeoutSeconds);
        document.put("maxAttemptsPerHour", maxAttemptsPerHour);
        document.put("tutorialMarkdown", tutorialMarkdown);
        return document;
    }

    /** 入库邮箱域：未单独配置时与认证域一致。 */
    public String effectiveStoreDomain() {
        return storeDomain.isBlank() ? eduDomain : storeDomain;
    }

    /** 认证域是否限定成员：为空表示接受任意域（含完整账号写法）。 */
    public boolean restrictToEduDomain() {
        return !eduDomain.isBlank();
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.min(Math.max(value, min), max);
    }

    /** 域名只保留小写裸域：容忍前端误填 @ 前缀、协议或路径。 */
    private static String domain(String value) {
        String trimmed = cut(value, MAX_DOMAIN_LENGTH).toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1);
        }
        int scheme = trimmed.indexOf("://");
        if (scheme >= 0) {
            trimmed = trimmed.substring(scheme + 3);
        }
        int slash = trimmed.indexOf('/');
        if (slash >= 0) {
            trimmed = trimmed.substring(0, slash);
        }
        int at = trimmed.indexOf('@');
        if (at >= 0) {
            trimmed = trimmed.substring(at + 1);
        }
        return trimmed.trim();
    }

    /** 认证服务地址必须是 http(s) 绝对地址，否则回落到默认值，避免配置写坏后整条渠道不可用。 */
    private static String endpoint(String value) {
        String trimmed = cut(value, MAX_ENDPOINT_LENGTH);
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return DEFAULT_ENDPOINT;
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return DEFAULT_ENDPOINT;
        }
        return trimmed;
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String text(Map<String, Object> document, String key, String fallback) {
        Object value = document.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Map<String, Object> document, String key, int fallback) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
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
        return text.isEmpty() ? fallback : Boolean.parseBoolean(text);
    }
}
