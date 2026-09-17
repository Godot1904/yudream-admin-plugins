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
    void acceptsOnlyExplicitPkuSuccessAndDiscardsDebugSecrets() {
        EduroamProbeOutcome result = EduroamResponseParser.parsePku(200,
                "{\"logtitle\":\"SUCCESS\",\"testinfo\":[\"password=secret\",\"MS-MPPE-Recv-Key=key\"]}", 12);
        assertTrue(result.success());
        assertEquals(12, result.latencyMs());
        assertFalse(result.detail().contains("secret"));
        assertFalse(result.detail().contains("key"));

        for (String body : new String[]{
                "{\"logtitle\":\"FAILURE\",\"testinfo\":[\"EAP authentication completed successfully\"]}",
                "{\"logtitle\":\"NOT SUCCESS\"}", "{\"logtitle\":true}", "{\"testinfo\":[\"SUCCESS\"]}",
                "{\"logtitle\":\"SUCCESS\",\"logtitle\":\"FAILURE\"}",
                "{\"logtitle\":\"SUCCESS\"} trailing", "[]", "null", "", SUCCESS_BODY,
                "<script>if (logtitle === 'SUCCESS') {}</script>"}) {
            assertFalse(EduroamResponseParser.parsePku(200, body, 1).success(), body);
        }
        assertFalse(EduroamResponseParser.parsePku(500, "{\"logtitle\":\"SUCCESS\"}", 1).success());
    }

    @Test
    void classifiesPkuFailuresWithoutStoringRawLogs() {
        for (String[] sample : new String[][]{
                {"EAP Failure", "CREDENTIAL_INVALID"}, {"EAPOL test timed out", "TIMEOUT"},
                {"illegal request", "ILLEGAL"}, {"unrecognized error", "UNKNOWN"}}) {
            String body = "{\"logtitle\":\"FAILURE\",\"testinfo\":[\"password=secret\",\"" + sample[0] + "\"]}";
            EduroamProbeOutcome result = EduroamResponseParser.parsePku(200, body, 1);
            assertFalse(result.success());
            assertEquals(sample[1], result.reasonCode());
            assertFalse(result.detail().contains("secret"));
        }
        assertEquals("UPSTREAM_ERROR", EduroamResponseParser.parsePku(200,
                "{\"testinfo\":[\"please input eduroamID!\"]}", 1).reasonCode());
        EduroamProbeOutcome error = EduroamResponseParser.parsePku(403, "password=secret", 1);
        assertEquals("UPSTREAM_ERROR", error.reasonCode());
        assertFalse(error.detail().contains("secret"));
    }

    @Test
    void acceptsSingleItemStatusArraysReturnedByPku() {
        EduroamProbeOutcome success = EduroamResponseParser.parsePku(200,
                "{\"logtitle\":[\"SUCCESS\"],\"testinfo\":[\"password=secret\"]}", 12);
        assertTrue(success.success());
        assertFalse(success.detail().contains("secret"));
        assertEquals(12, success.latencyMs());

        // 北大接口使用合成的 example.invalid 账号实测所得的响应结构与拒绝标志。
        EduroamProbeOutcome rejected = EduroamResponseParser.parsePku(200, """
                {"logtitle":["FAILURE"],"testinfo":[
                  "password - hexdump_ascii(len=6):", "73 65 63 72 65 74 secret",
                  "RADIUS message: code=3 (Access-Reject) identifier=0 length=54",
                  "FAILURE"],"testuser":"probe@example.invalid","port1":1812}
                """, 10);
        assertFalse(rejected.success());
        assertEquals("CREDENTIAL_INVALID", rejected.reasonCode());
        assertTrue(rejected.detail().contains("Access-Reject"));
        assertFalse(rejected.detail().contains("secret"));
        assertFalse(rejected.detail().contains("probe@example.invalid"));

        EduroamProbeOutcome timeout = EduroamResponseParser.parsePku(200,
                "{\"logtitle\":[\"FAILURE\"],\"testinfo\":[\"EAPOL test timed out\"]}", 10);
        assertEquals("TIMEOUT", timeout.reasonCode());
        assertFalse(EduroamResponseParser.parsePku(500, "{\"logtitle\":[\"SUCCESS\"]}", 10).success());

        for (String status : new String[]{"[]", "[\"FAILURE\",\"SUCCESS\"]", "[\"SUCCESS\",\"FAILURE\"]",
                "[\"SUCCESS\",\"SUCCESS\"]", "[[\"SUCCESS\"]]", "[true]", "[null]", "{\"result\":\"SUCCESS\"}"}) {
            EduroamProbeOutcome invalid = EduroamResponseParser.parsePku(200,
                    "{\"logtitle\":" + status + ",\"testinfo\":[\"EAP authentication completed successfully\"]}", 1);
            assertFalse(invalid.success(), status);
            assertEquals("UPSTREAM_ERROR", invalid.reasonCode(), status);
        }
    }

    @Test
    void distinguishesResponseShapeErrorsWithoutExposingResponseContents() {
        for (String[] sample : new String[][]{
                {"", "响应为空"}, {"<html>secret</html>", "HTML"}, {"secret", "不是有效的 JSON"},
                {"[]", "顶层不是对象"}, {"{\"testinfo\":[\"secret\"]}", "缺少 logtitle"},
                {"{\"logtitle\":[1],\"testinfo\":[\"secret\"]}", "仅含一个字符串的数组"}}) {
            EduroamProbeOutcome result = EduroamResponseParser.parsePku(200, sample[0], 1);
            assertFalse(result.success());
            assertEquals("UPSTREAM_ERROR", result.reasonCode());
            assertTrue(result.detail().contains(sample[1]), result.detail());
            assertFalse(result.detail().contains("secret"));
        }
    }

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
