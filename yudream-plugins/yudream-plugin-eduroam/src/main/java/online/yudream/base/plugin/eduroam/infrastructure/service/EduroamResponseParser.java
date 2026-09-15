package online.yudream.base.plugin.eduroam.infrastructure.service;

import online.yudream.base.plugin.eduroam.domain.enumerate.EduroamFailureReason;
import online.yudream.base.plugin.eduroam.domain.valobj.EduroamProbeOutcome;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Eduroam 认证服务响应解析。
 *
 * <p>上游是网页形式的测试工具，只能按文本判定结果；判定口径与原 auth-eduroam 插件一致：
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

    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private EduroamResponseParser() {
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
