package online.yudream.base.plugin.eduroam.infrastructure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.eduroam.domain.enumerate.EduroamFailureReason;
import online.yudream.base.plugin.eduroam.domain.valobj.EduroamProbeOutcome;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Eduroam 认证服务响应解析。
 *
 * <p>北大探测点以 JSON 的 {@code logtitle} 为认证结果；自定义旧服务按文本判定：
 * 成功看 {@code EAP authentication completed successfully}，失败区分 {@code EAP Failure}（账密错误）、
 * {@code EAPOL test timed out}（超时）与 {@code illegal}（非法操作）。
 *
 * <p>解析出的 {@code detail} 会去掉脚本/样式与标签再截断，既能给管理员看，也不会把整页 HTML 存进库。
 */
public final class EduroamResponseParser {

    private static final String SUCCESS_MARKER = "EAP authentication completed successfully";
    private static final String CREDENTIAL_MARKER = "EAP Failure";
    private static final String TIMEOUT_MARKER = "EAPOL test timed out";
    private static final String ILLEGAL_MARKER = "illegal";
    private static final int MAX_DETAIL_LENGTH = 400;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private EduroamResponseParser() {
    }

    /** 兼容上游的单元素状态数组与字符串；调试日志中的成功片段不能替代最终结果。 */
    public static EduroamProbeOutcome parsePku(int statusCode, String body, long latencyMs) {
        if (statusCode < 200 || statusCode >= 300) {
            return EduroamProbeOutcome.failed(EduroamFailureReason.UPSTREAM_ERROR,
                    "北大探测接口返回 HTTP " + statusCode, latencyMs);
        }
        if (body == null || body.isBlank()) {
            return invalidPkuResponse("响应为空", latencyMs);
        }
        try {
            JsonNode result = JSON.readTree(body);
            if (result == null || !result.isObject()) {
                return invalidPkuResponse("JSON 顶层不是对象", latencyMs);
            }
            if (!result.hasNonNull("logtitle")) {
                return invalidPkuResponse("JSON 缺少 logtitle 认证状态", latencyMs);
            }
            String status = pkuStatus(result.get("logtitle"));
            if (status == null) {
                return invalidPkuResponse("logtitle 必须为字符串或仅含一个字符串的数组", latencyMs);
            }
            if ("SUCCESS".equals(status)) {
                return EduroamProbeOutcome.passed("北大探测点认证成功（PEAP/MSCHAPv2）", latencyMs);
            }
            // testinfo 是 RADIUS 调试日志，可能含凭据或密钥。只识别已知错误，不存储原文。
            StringBuilder diagnostics = new StringBuilder();
            JsonNode info = result.path("testinfo");
            if (info.isArray()) {
                for (JsonNode line : info) {
                    if (line.isTextual()) {
                        diagnostics.append(line.textValue()).append('\n');
                    }
                }
            }
            String text = diagnostics.toString().toLowerCase(Locale.ROOT);
            EduroamFailureReason reason = EduroamFailureReason.UNKNOWN;
            String detail = "北大探测点未返回成功结果";
            if (text.contains(TIMEOUT_MARKER.toLowerCase(Locale.ROOT))) {
                reason = EduroamFailureReason.TIMEOUT;
                detail = "北大探测点认证超时（EAPOL test timed out）";
            } else if (text.contains("access-reject")) {
                reason = EduroamFailureReason.CREDENTIAL_INVALID;
                detail = "北大探测点拒绝认证（RADIUS Access-Reject），请检查学校认证域、账号或密码";
            } else if (text.contains(CREDENTIAL_MARKER.toLowerCase(Locale.ROOT))) {
                reason = EduroamFailureReason.CREDENTIAL_INVALID;
                detail = "北大探测点认证失败（EAP Failure）";
            } else if (text.contains(ILLEGAL_MARKER)) {
                reason = EduroamFailureReason.ILLEGAL;
                detail = "北大探测点拒绝请求（illegal）";
            }
            return EduroamProbeOutcome.failed(reason, detail, latencyMs);
        } catch (JsonProcessingException ignored) {
            return invalidPkuResponse(body.stripLeading().startsWith("<")
                    ? "返回 HTML 页面而非 JSON，可能为站点错误或访问限制"
                    : "响应不是有效的 JSON", latencyMs);
        }
    }

    private static String pkuStatus(JsonNode value) {
        if (value.isTextual()) {
            return value.textValue();
        }
        // 实测接口返回 {"logtitle":["FAILURE"],...}；只接受唯一结果，不能从多个状态中挑成功。
        if (value.isArray() && value.size() == 1 && value.get(0).isTextual()) {
            return value.get(0).textValue();
        }
        return null;
    }

    private static EduroamProbeOutcome invalidPkuResponse(String cause, long latencyMs) {
        return EduroamProbeOutcome.failed(EduroamFailureReason.UPSTREAM_ERROR,
                "北大探测接口响应异常：" + cause, latencyMs);
    }

    public static EduroamProbeOutcome parse(int statusCode, String body, long latencyMs) {
        String summary = summarize(body);
        if (statusCode < 200 || statusCode >= 300) {
            return EduroamProbeOutcome.failed(EduroamFailureReason.UPSTREAM_ERROR,
                    "上游返回 HTTP " + statusCode + (summary.isEmpty() ? "" : "：" + summary), latencyMs);
        }
        String text = body == null ? "" : body;
        if (text.isBlank()) {
            return EduroamProbeOutcome.failed(EduroamFailureReason.UNKNOWN, "", latencyMs);
        }
        if (text.contains(SUCCESS_MARKER)) {
            return EduroamProbeOutcome.passed(summary, latencyMs);
        }
        if (text.contains(CREDENTIAL_MARKER)) {
            return EduroamProbeOutcome.failed(EduroamFailureReason.CREDENTIAL_INVALID, summary, latencyMs);
        }
        if (text.toLowerCase(Locale.ROOT).contains(TIMEOUT_MARKER.toLowerCase(Locale.ROOT))) {
            return EduroamProbeOutcome.failed(EduroamFailureReason.TIMEOUT, summary, latencyMs);
        }
        if (text.toLowerCase(Locale.ROOT).contains(ILLEGAL_MARKER)) {
            return EduroamProbeOutcome.failed(EduroamFailureReason.ILLEGAL, summary, latencyMs);
        }
        return EduroamProbeOutcome.failed(EduroamFailureReason.UNKNOWN, summary, latencyMs);
    }

    /** 去脚本/样式与标签、压缩空白并截断，得到可入库的上游摘要。 */
    public static String summarize(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String text = SCRIPT_OR_STYLE.matcher(body).replaceAll(" ");
        text = TAG.matcher(text).replaceAll(" ");
        text = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        return text.length() <= MAX_DETAIL_LENGTH ? text : text.substring(0, MAX_DETAIL_LENGTH) + "…";
    }
}
