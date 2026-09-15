package online.yudream.base.plugin.eduroam.domain.valobj;

import online.yudream.base.plugin.eduroam.domain.enumerate.EduroamFailureReason;

/**
 * 一次 Eduroam 探测的结果。
 *
 * <p>{@code detail} 是上游响应的短摘要（已去标签并截断），只用于管理员排查；密码永远不会出现在任何字段里。
 */
public record EduroamProbeOutcome(boolean success, String reasonCode, String message, String detail, long latencyMs) {

    public static final String SUCCESS = "SUCCESS";

    public static EduroamProbeOutcome passed(String detail, long latencyMs) {
        return new EduroamProbeOutcome(true, SUCCESS, "", detail, latencyMs);
    }

    public static EduroamProbeOutcome failed(EduroamFailureReason reason, String detail, long latencyMs) {
        EduroamFailureReason safe = reason == null ? EduroamFailureReason.UNKNOWN : reason;
        return new EduroamProbeOutcome(false, safe.code(), safe.message(), detail, latencyMs);
    }

    /** 连接层失败（DNS/超时/拒绝连接）时附带原因文本，便于管理员定位是网络还是配置问题。 */
    public static EduroamProbeOutcome unreachable(String cause, long latencyMs) {
        String detail = cause == null || cause.isBlank() ? "网络不可达" : cause.trim();
        return new EduroamProbeOutcome(false, EduroamFailureReason.UPSTREAM_UNREACHABLE.code(),
                EduroamFailureReason.UPSTREAM_UNREACHABLE.message(), detail, latencyMs);
    }
}
