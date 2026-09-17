package online.yudream.base.plugin.tarusso.interfaces.request;

public record SettingsSaveRequest(
        Boolean enabled,
        String protocol,
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
        String clientSecret,
        String scopes,
        String callbackUrl
) {
}
