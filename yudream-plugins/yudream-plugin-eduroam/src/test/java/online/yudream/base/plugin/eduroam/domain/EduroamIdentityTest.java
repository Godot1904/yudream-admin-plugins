package online.yudream.base.plugin.eduroam.domain;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import online.yudream.base.plugin.eduroam.domain.valobj.EduroamIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 账号解析测试：对齐原 auth-eduroam 的 EDUROAM_HOST / EDUROAM_STORE_HOST 语义。
 */
class EduroamIdentityTest {

    private static EduroamSettings settings(String eduDomain, String storeDomain) {
        return new EduroamSettings(true, eduDomain, storeDomain, EduroamSettings.DEFAULT_ENDPOINT, 5, 20, 10, "");
    }

    @Test
    void appendsEduDomainAndKeepsStoreDomainSeparate() {
        EduroamIdentity identity = EduroamIdentity.resolve("2023123456", settings("example.edu.cn", "mail.example.edu.cn"));
        assertEquals("2023123456@example.edu.cn", identity.identity());
        assertEquals("2023123456@mail.example.edu.cn", identity.email());
        assertEquals("mail.example.edu.cn", identity.domain());
    }

    @Test
    void withoutStoreDomainStoredEmailUsesEduDomain() {
        EduroamIdentity identity = EduroamIdentity.resolve("2023123456", settings("example.edu.cn", ""));
        assertEquals("2023123456@example.edu.cn", identity.email());
    }

    @Test
    void acceptsFullAccountWhenEduDomainConfigured() {
        EduroamIdentity identity = EduroamIdentity.resolve("Alice@Example.edu.cn", settings("example.edu.cn", ""));
        assertEquals("Alice@example.edu.cn", identity.identity());
        assertEquals("alice@example.edu.cn", identity.email());
    }

    @Test
    void rejectsOtherDomainWhenRestricted() {
        assertEquals("Eduroam 账号域名必须是 @example.edu.cn",
                assertThrows(IllegalArgumentException.class,
                        () -> EduroamIdentity.resolve("alice@other.edu.cn", settings("example.edu.cn", "")))
                        .getMessage());
    }

    @Test
    void requiresFullAccountWhenNoEduDomainConfigured() {
        assertEquals("请填写完整的 Eduroam 账号（形如 学号@学校域名），或联系管理员配置认证域",
                assertThrows(IllegalArgumentException.class,
                        () -> EduroamIdentity.resolve("2023123456", settings("", "")))
                        .getMessage());

        EduroamIdentity identity = EduroamIdentity.resolve("alice@some.edu.cn", settings("", ""));
        assertEquals("alice@some.edu.cn", identity.identity());
        assertEquals("alice@some.edu.cn", identity.email());
    }

    @Test
    void rejectsMalformedAccounts() {
        assertThrows(IllegalArgumentException.class, () -> EduroamIdentity.resolve("  ", settings("", "")));
        assertThrows(IllegalArgumentException.class, () -> EduroamIdentity.resolve("alice@", settings("", "")));
        assertThrows(IllegalArgumentException.class, () -> EduroamIdentity.resolve("alice@nodot", settings("", "")));
        assertThrows(IllegalArgumentException.class, () -> EduroamIdentity.resolve("ali ce", settings("", "")));
    }

    @Test
    void normalizesEmailAndValidatesShape() {
        assertEquals("alice@example.edu.cn", EduroamIdentity.normalizeEmail("  Alice@Example.EDU.CN "));
        assertTrue(EduroamIdentity.looksLikeEmail("alice@example.edu.cn"));
        assertFalse(EduroamIdentity.looksLikeEmail("alice@nodot"));
        assertFalse(EduroamIdentity.looksLikeEmail("@example.edu.cn"));
        assertFalse(EduroamIdentity.looksLikeEmail(""));
    }

    @Test
    void settingsTolerateSlopyDomainInput() {
        EduroamSettings slopy = settings("@Example.EDU.CN", "https://mail.example.edu.cn/path");
        assertEquals("example.edu.cn", slopy.eduDomain());
        assertEquals("mail.example.edu.cn", slopy.storeDomain());
        assertEquals("mail.example.edu.cn", slopy.effectiveStoreDomain());
        assertTrue(slopy.restrictToEduDomain());
    }
}
