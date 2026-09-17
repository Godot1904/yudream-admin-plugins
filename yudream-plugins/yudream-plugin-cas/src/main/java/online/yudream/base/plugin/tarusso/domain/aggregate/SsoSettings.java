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
            String callbackUrl
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
                ""
        );
    }

    public SsoSettings withClientSecretConfigured(boolean configured) {
        return new SsoSettings(
                enabled, protocol, displayName, icon, casBaseUrl, loginPath, validatePath,
                oidcIssuer, oidcAuthorizePath, oidcTokenPath, oidcUserinfoPath, oidcJwksPath,
                oidcRegisterPath, clientId, configured, scopes, callbackUrl
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
