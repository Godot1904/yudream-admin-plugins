package online.yudream.base.plugin.eduroam.application.dto;

/**
 * 公开渠道信息：只暴露凭据页需要的字段。
 *
 * <p>不含认证服务地址、限流参数，也不含「本站邮箱域」——该字段只影响管理端台账的展示，
 * 登录页不需要知道它。
 */
public record EduroamPublicConfigDTO(
        boolean enabled,
        String eduDomain,
        String accountHint,
        String tutorialMarkdown
) {
}
