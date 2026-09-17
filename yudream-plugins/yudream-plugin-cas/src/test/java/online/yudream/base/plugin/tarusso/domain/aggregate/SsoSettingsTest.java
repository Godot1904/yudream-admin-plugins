package online.yudream.base.plugin.tarusso.domain.aggregate;

import online.yudream.base.plugin.tarusso.domain.enumerate.SsoProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoSettingsTest {

    @Test
    void casReadyWhenCallbackPresent() {
        SsoSettings settings = new SsoSettings(
                true, SsoProtocol.CAS, "塔大", "i-ri:school-line",
                "https://auth.taru.edu.cn/", "/authserver/login", "/cas/p3/serviceValidate",
                SsoSettings.DEFAULT_OIDC_ISSUER, SsoSettings.DEFAULT_OIDC_AUTHORIZE_PATH, SsoSettings.DEFAULT_OIDC_TOKEN_PATH,
                SsoSettings.DEFAULT_OIDC_USERINFO_PATH, SsoSettings.DEFAULT_OIDC_JWKS_PATH, SsoSettings.DEFAULT_OIDC_REGISTER_PATH,
                "", false, SsoSettings.DEFAULT_SCOPES, "https://site.example/api/external-login/callback"
        );
        assertTrue(settings.ready());
        assertTrue(settings.loginEnabled());
        assertEquals("https://auth.taru.edu.cn", settings.casBaseUrl());
        assertEquals("https://site.example/api/external-login/callback?state=abc", settings.casServiceUrl("abc"));
    }

    @Test
    void oidcNotReadyWithoutSecret() {
        SsoSettings settings = new SsoSettings(
                true, SsoProtocol.OIDC, SsoSettings.DEFAULT_DISPLAY_NAME, SsoSettings.DEFAULT_ICON,
                SsoSettings.DEFAULT_CAS_BASE_URL, SsoSettings.DEFAULT_LOGIN_PATH, SsoSettings.DEFAULT_VALIDATE_PATH,
                SsoSettings.DEFAULT_OIDC_ISSUER, SsoSettings.DEFAULT_OIDC_AUTHORIZE_PATH, SsoSettings.DEFAULT_OIDC_TOKEN_PATH,
                SsoSettings.DEFAULT_OIDC_USERINFO_PATH, SsoSettings.DEFAULT_OIDC_JWKS_PATH, SsoSettings.DEFAULT_OIDC_REGISTER_PATH,
                "client", false, SsoSettings.DEFAULT_SCOPES, "https://site.example/callback"
        );
        assertFalse(settings.ready());
        assertTrue(settings.withClientSecretConfigured(true).ready());
    }

    @Test
    void appendsStateWhenCallbackAlreadyHasQuery() {
        SsoSettings settings = SsoSettings.defaults();
        SsoSettings withCallback = new SsoSettings(
                false, SsoProtocol.CAS, settings.displayName(), settings.icon(), settings.casBaseUrl(),
                settings.loginPath(), settings.validatePath(), settings.oidcIssuer(), settings.oidcAuthorizePath(),
                settings.oidcTokenPath(), settings.oidcUserinfoPath(), settings.oidcJwksPath(), settings.oidcRegisterPath(),
                "", false, settings.scopes(), "https://site.example/callback?from=login"
        );
        assertEquals("https://site.example/callback?from=login&state=s1", withCallback.casServiceUrl("s1"));
    }
}
