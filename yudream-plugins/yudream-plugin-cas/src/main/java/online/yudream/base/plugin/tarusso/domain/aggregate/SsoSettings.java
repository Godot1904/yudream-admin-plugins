package online.yudream.base.plugin.tarusso.domain.aggregate;

import online.yudream.base.plugin.tarusso.domain.enumerate.SsoProtocol;

/**
 * 塔里木大学 SSO 全局设置。clientSecret 不进入本聚合，由密钥库单独保管。
 */
public final class SsoSettings {

    public static final String DEFAULT_CAS_BASE_URL = "https://auth.taru.edu.cn";
    public static final String DEFAULT_LOGIN_PATH = "/authserver/login";
    public static final String DEFAULT_VALIDATE_PATH = "/cas/p3/serviceValidate";
    public static final String DEFAULT_OIDC_AUTHORIZE_PATH = "/authserver/oidc/authorize";
    public static final String DEFAULT_OIDC_TOKEN_PATH = "/authserver/oidc/accessToken";
    public static final String DEFAULT_OIDC_USERINFO_PATH = "/authserver/oidc/profile";
    public static final String DEFAULT_OIDC_JWKS_PATH = "/authserver/oidc/jwks";
    public static final String DEFAULT_OIDC_REGISTER_PATH = "/authserver/oidc/register";
    public static final String DEFAULT_OIDC_ISSUER = "https://auth.taru.edu.cn/authserver/oidc/";
    public static final String DEFAULT_SCOPES = "openid profile email";
    public static final String DEFAULT_DISPLAY_NAME = "塔里木大学统一身份认证";
    public static final String DEFAULT_ICON = "i-ri:graduation-cap-line";

    /** state 承载方式：追加在 service 查询串上（默认，CAS 会原样带回）。 */
    public static final String STATE_MODE_QUERY = "query";
    /** state 承载方式：兜底。service 用插件的固定中转地址（不含查询串），回调时按最近一次记录补回 state。 */
    public static final String STATE_MODE_RELAY = "relay";
    /** 宿主第三方登录回调的固定前缀，用于从回调地址反推站点基址与宿主回调必须路径。 */
    public static final String EXTERNAL_LOGIN_PREFIX = "/api/external-login";
    /** 兜底中转端点（插件 code = cas，见 TaruSsoPlugin.CODE）。 */
    public static final String RELAY_PATH = "/api/plugins/cas/public/relay";

    private final boolean enabled;
    private final SsoProtocol protocol;
    private final String displayName;
    private final String icon;
    private final String casBaseUrl;
    private final String loginPath;
    private final String validatePath;
    private final String oidcIssuer;
    private final String oidcAuthorizePath;
    private final String oidcTokenPath;
    private final String oidcUserinfoPath;
    private final String oidcJwksPath;
    private final String oidcRegisterPath;
    private final String clientId;
    private final boolean clientSecretConfigured;
    private final String scopes;
    private final String callbackUrl;
    private final String casStateMode;

    public SsoSettings(
            boolean enabled,
            SsoProtocol protocol,
            String displayName,
            String icon,
            String casBaseUrl,
            String loginPath,
            String validatePath,
            String oidcIssuer,
            String oidcAuthorizePath,
            String oidcTokenPath,
            String oidcUserinfoPath,
            String oidcJwksPath,
            String oidcRegisterPath,
            String clientId,
            boolean clientSecretConfigured,
            String scopes,
            String callbackUrl,
            String casStateMode
    ) {
        this.enabled = enabled;
        this.protocol = protocol == null ? SsoProtocol.CAS : protocol;
        this.displayName = blankToDefault(displayName, DEFAULT_DISPLAY_NAME);
        this.icon = blankToDefault(icon, DEFAULT_ICON);
        this.casBaseUrl = trimTrailingSlash(blankToDefault(casBaseUrl, DEFAULT_CAS_BASE_URL));
        this.loginPath = normalizePath(blankToDefault(loginPath, DEFAULT_LOGIN_PATH));
        this.validatePath = normalizePath(blankToDefault(validatePath, DEFAULT_VALIDATE_PATH));
        this.oidcIssuer = blankToDefault(oidcIssuer, DEFAULT_OIDC_ISSUER);
        this.oidcAuthorizePath = normalizePath(blankToDefault(oidcAuthorizePath, DEFAULT_OIDC_AUTHORIZE_PATH));
        this.oidcTokenPath = normalizePath(blankToDefault(oidcTokenPath, DEFAULT_OIDC_TOKEN_PATH));
        this.oidcUserinfoPath = normalizePath(blankToDefault(oidcUserinfoPath, DEFAULT_OIDC_USERINFO_PATH));
        this.oidcJwksPath = normalizePath(blankToDefault(oidcJwksPath, DEFAULT_OIDC_JWKS_PATH));
        this.oidcRegisterPath = normalizePath(blankToDefault(oidcRegisterPath, DEFAULT_OIDC_REGISTER_PATH));
        this.clientId = trimToEmpty(clientId);
        this.clientSecretConfigured = clientSecretConfigured;
        this.scopes = blankToDefault(scopes, DEFAULT_SCOPES);
        this.callbackUrl = trimToEmpty(callbackUrl);
        this.casStateMode = STATE_MODE_RELAY.equalsIgnoreCase(trimToEmpty(casStateMode))
                ? STATE_MODE_RELAY : STATE_MODE_QUERY;
    }

    public static SsoSettings defaults() {
        return new SsoSettings(
                false,
                SsoProtocol.CAS,
                DEFAULT_DISPLAY_NAME,
                DEFAULT_ICON,
                DEFAULT_CAS_BASE_URL,
                DEFAULT_LOGIN_PATH,
                DEFAULT_VALIDATE_PATH,
                DEFAULT_OIDC_ISSUER,
                DEFAULT_OIDC_AUTHORIZE_PATH,
                DEFAULT_OIDC_TOKEN_PATH,
                DEFAULT_OIDC_USERINFO_PATH,
                DEFAULT_OIDC_JWKS_PATH,
                DEFAULT_OIDC_REGISTER_PATH,
                "",
                false,
                DEFAULT_SCOPES,
                "",
                STATE_MODE_QUERY
        );
    }

    /**
     * 只切换 state 承载方式：其余字段原样保留（设置页保存与就地切换都用它，避免逐位置构造串位）。
     */
    public SsoSettings withCasStateMode(String mode) {
        return new SsoSettings(
                enabled, protocol, displayName, icon, casBaseUrl, loginPath, validatePath,
                oidcIssuer, oidcAuthorizePath, oidcTokenPath, oidcUserinfoPath, oidcJwksPath,
                oidcRegisterPath, clientId, clientSecretConfigured, scopes, callbackUrl, mode
        );
    }

    public SsoSettings withClientSecretConfigured(boolean configured) {
        return new SsoSettings(
                enabled, protocol, displayName, icon, casBaseUrl, loginPath, validatePath,
                oidcIssuer, oidcAuthorizePath, oidcTokenPath, oidcUserinfoPath, oidcJwksPath,
                oidcRegisterPath, clientId, configured, scopes, callbackUrl, casStateMode
        );
    }

    public SsoSettings withProtocol(SsoProtocol newProtocol, String newClientId) {
        return new SsoSettings(
                enabled, newProtocol, displayName, icon, casBaseUrl, loginPath, validatePath,
                oidcIssuer, oidcAuthorizePath, oidcTokenPath, oidcUserinfoPath, oidcJwksPath,
                oidcRegisterPath, newClientId, clientSecretConfigured, scopes, callbackUrl, casStateMode
        );
    }

    public boolean ready() {
        if (blank(callbackUrl) || blank(casBaseUrl)) {
            return false;
        }
        if (protocol == SsoProtocol.OIDC) {
            return !blank(clientId) && clientSecretConfigured;
        }
        return true;
    }

    public boolean loginEnabled() {
        return enabled && ready();
    }

    public String absolute(String path) {
        return casBaseUrl + normalizePath(path);
    }

    public String loginUrl() {
        return absolute(loginPath);
    }

    public String validateUrl() {
        return absolute(validatePath);
    }

    public String oidcAuthorizeUrl() {
        return absolute(oidcAuthorizePath);
    }

    public String oidcTokenUrl() {
        return absolute(oidcTokenPath);
    }

    public String oidcUserinfoUrl() {
        return absolute(oidcUserinfoPath);
    }

    public String oidcJwksUrl() {
        return absolute(oidcJwksPath);
    }

    public String oidcRegisterUrl() {
        return absolute(oidcRegisterPath);
    }

    /** CAS service URL：回调地址附加 state，CAS 会原样带回并追加 ticket。 */
    public String casServiceUrl(String state) {
        String separator = callbackUrl.contains("?") ? "&" : "?";
        return callbackUrl + separator + "state=" + state;
    }

    /** 兜底模式的固定 service：插件公开中转端点，**不含任何查询串**（正是为了绕开 IdP 对 service 的限制）。 */
    public String relayServiceUrl() {
        String base = siteBaseUrl();
        return base.isEmpty() ? "" : base + RELAY_PATH;
    }

    /**
     * 兜底中转把请求换回宿主回调地址：追加 CAS 的 ticket 与本次尝试的宿主 state。
     * 目标固定用宿主的通用回调 {@code /api/external-login/callback}——它同时接受 ticket 与 code 参数名，
     * 因此即使管理员把回调地址填成了供应商专用回调，这里也能自愈。
     */
    public String relayForwardUrl(String providerCode, String platformType, String hostState, String ticket) {
        String base = siteBaseUrl();
        if (base.isEmpty()) {
            return "";
        }
        StringBuilder url = new StringBuilder(base).append(EXTERNAL_LOGIN_PREFIX).append("/callback")
                .append("?provider=").append(encode(providerCode))
                .append("&type=").append(encode(platformType))
                .append("&state=").append(encode(hostState));
        if (ticket != null && !ticket.isBlank()) {
            url.append("&ticket=").append(encode(ticket));
        }
        return url.toString();
    }

    /** 站点基址：从回调地址里截掉宿主第三方登录路径前缀得到。 */
    public String siteBaseUrl() {
        int index = callbackUrl.indexOf(EXTERNAL_LOGIN_PREFIX);
        return index <= 0 ? "" : trimTrailingSlash(callbackUrl.substring(0, index));
    }

    /** 兜底模式是否可用（需要能从回调地址反推站点基址）。 */
    public boolean relayReady() {
        return !relayServiceUrl().isEmpty() && !callbackUrl.isBlank();
    }

    public boolean relayStateMode() {
        return STATE_MODE_RELAY.equals(casStateMode);
    }

    public boolean enabled() {
        return enabled;
    }

    public SsoProtocol protocol() {
        return protocol;
    }

    public String displayName() {
        return displayName;
    }

    public String icon() {
        return icon;
    }

    public String casBaseUrl() {
        return casBaseUrl;
    }

    public String loginPath() {
        return loginPath;
    }

    public String validatePath() {
        return validatePath;
    }

    public String oidcIssuer() {
        return oidcIssuer;
    }

    public String oidcAuthorizePath() {
        return oidcAuthorizePath;
    }

    public String oidcTokenPath() {
        return oidcTokenPath;
    }

    public String oidcUserinfoPath() {
        return oidcUserinfoPath;
    }

    public String oidcJwksPath() {
        return oidcJwksPath;
    }

    public String oidcRegisterPath() {
        return oidcRegisterPath;
    }

    public String clientId() {
        return clientId;
    }

    public boolean clientSecretConfigured() {
        return clientSecretConfigured;
    }

    public String scopes() {
        return scopes;
    }

    public String callbackUrl() {
        return callbackUrl;
    }

    public String casStateMode() {
        return casStateMode;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String blankToDefault(String value, String defaultValue) {
        String trimmed = trimToEmpty(value);
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizePath(String path) {
        String trimmed = trimToEmpty(path);
        if (trimmed.isEmpty()) {
            return "/";
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
