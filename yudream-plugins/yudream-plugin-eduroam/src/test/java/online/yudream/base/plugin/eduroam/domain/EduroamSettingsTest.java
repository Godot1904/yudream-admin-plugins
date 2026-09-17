package online.yudream.base.plugin.eduroam.domain;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EduroamSettingsTest {

    @Test
    void defaultsAndPersistedLegacyEndpointsUsePku() {
        String endpoint = "https://analysis.eduroam.edu.cn/checkc/pkudetection";
        assertEquals(endpoint, EduroamSettings.defaults().verifyEndpoint());
        for (String old : new String[]{"", "invalid", "https://eduroam.ustc.edu.cn/cgi-bin/eduroam-test.cgi",
                " http://eduroam.ustc.edu.cn/cgi-bin/eduroam-test.cgi "}) {
            EduroamSettings settings = EduroamSettings.from(Map.of(
                    "verifyEndpoint", old, "eduDomain", "example.edu.cn", "storeDomain", "mail.example.edu.cn"));
            assertEquals(endpoint, settings.verifyEndpoint());
            assertEquals(endpoint, settings.toDocument().get("verifyEndpoint"));
            assertEquals("example.edu.cn", settings.eduDomain());
            assertEquals("mail.example.edu.cn", settings.storeDomain());
        }
    }

    @Test
    void preservesCustomEndpoints() {
        String endpoint = "https://auth.example.edu.cn/cgi-bin/eduroam-test.cgi";
        assertEquals(endpoint, EduroamSettings.from(Map.of("verifyEndpoint", endpoint)).verifyEndpoint());
    }
}
