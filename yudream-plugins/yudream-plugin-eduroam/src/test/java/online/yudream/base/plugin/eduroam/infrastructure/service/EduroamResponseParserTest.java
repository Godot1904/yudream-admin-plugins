package online.yudream.base.plugin.eduroam.infrastructure.service;

import online.yudream.base.plugin.eduroam.domain.enumerate.EduroamFailureReason;
import online.yudream.base.plugin.eduroam.domain.valobj.EduroamProbeOutcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上游响应解析测试。
 *
 * <p>样本取自原 auth-eduroam 插件实际判定过的响应片段，保证移植后判定口径不回退。
 */
class EduroamResponseParserTest {

    private static final String SUCCESS_BODY = """
            <html><body>
            <h3>测试结果: <span style="color: green;">OK，认证过程正常</span></h3>
            <pre>EAP authentication completed successfully</pre>
            </body></html>
            """;

    @Test
    void recognizesSuccessfulAuthentication() {
        EduroamProbeOutcome outcome = EduroamResponseParser.parse(200, SUCCESS_BODY, 88L);
        assertTrue(outcome.success());
        assertEquals(EduroamProbeOutcome.SUCCESS, outcome.reasonCode());
        assertEquals(88L, outcome.latencyMs());
    }

    @Test
    void mapsCredentialFailureTimeoutAndIllegal() {
        EduroamProbeOutcome credential = EduroamResponseParser.parse(200, "<p>EAP Failure</p>", 10L);
        assertFalse(credential.success());
        assertEquals(EduroamFailureReason.CREDENTIAL_INVALID.code(), credential.reasonCode());

        EduroamProbeOutcome timeout = EduroamResponseParser.parse(200, "<p>EAPOL test timed out</p>", 10L);
        assertEquals(EduroamFailureReason.TIMEOUT.code(), timeout.reasonCode());

        EduroamProbeOutcome illegal = EduroamResponseParser.parse(200, "<p>illegal request</p>", 10L);
        assertEquals(EduroamFailureReason.ILLEGAL.code(), illegal.reasonCode());

        EduroamProbeOutcome unknown = EduroamResponseParser.parse(200, "<p>something else</p>", 10L);
        assertEquals(EduroamFailureReason.UNKNOWN.code(), unknown.reasonCode());
    }

    @Test
    void treatsHttpErrorsAndEmptyBodiesAsFailures() {
        EduroamProbeOutcome server = EduroamResponseParser.parse(500, "<p>boom</p>", 5L);
        assertFalse(server.success());
        assertEquals(EduroamFailureReason.UPSTREAM_ERROR.code(), server.reasonCode());

        EduroamProbeOutcome empty = EduroamResponseParser.parse(200, "   ", 5L);
        assertFalse(empty.success());
        assertEquals(EduroamFailureReason.UNKNOWN.code(), empty.reasonCode());
    }

    @Test
    void summaryStripsMarkupEntitiesAndTruncates() {
        String summary = EduroamResponseParser.summarize("<script>var a=1;</script><h3>结果 &amp; 说明</h3>\n\n<b>OK</b>");
        assertEquals("结果 & 说明 OK", summary);

        String longBody = "<p>" + "x".repeat(800) + "</p>";
        String truncated = EduroamResponseParser.summarize(longBody);
        assertTrue(truncated.length() <= 401, "摘要应被截断：" + truncated.length());
        assertTrue(truncated.endsWith("…"));
    }
}
