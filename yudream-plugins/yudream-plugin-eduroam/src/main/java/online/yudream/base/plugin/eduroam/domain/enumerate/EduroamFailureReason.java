package online.yudream.base.plugin.eduroam.domain.enumerate;

/**
 * 核验失败原因。
 *
 * <p>原 auth-eduroam 插件只按上游返回的英文片段区分原因，这里把每种原因映射成可直接展示给用户的中文文案，
 * 前端与审计记录共用同一份口径；{@link #code()} 会写进尝试审计，便于管理员统计与排查。
 */
public enum EduroamFailureReason {

    CREDENTIAL_INVALID("账号或密码错误，Eduroam 认证未通过，请确认后重试"),
    TIMEOUT("认证超时：请先连接校园网 / Eduroam 网络再重试"),
    ILLEGAL("上游拒绝了这次请求（非法操作），请稍后重试或联系管理员"),
    UPSTREAM_UNREACHABLE("无法连接 Eduroam 认证服务，请稍后重试；如持续失败请联系管理员"),
    UPSTREAM_ERROR("Eduroam 认证服务返回异常，请稍后重试"),
    UNKNOWN("认证未通过：上游未返回成功结果，请确认账号密码后重试");

    private final String message;

    EduroamFailureReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    public String code() {
        return name();
    }
}
