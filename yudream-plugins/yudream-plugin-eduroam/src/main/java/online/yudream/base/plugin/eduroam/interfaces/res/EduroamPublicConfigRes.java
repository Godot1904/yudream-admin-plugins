package online.yudream.base.plugin.eduroam.interfaces.res;

/** 公开渠道信息响应；不含认证服务地址与「本站邮箱域」。 */
public record EduroamPublicConfigRes(
        boolean enabled,
        String eduDomain,
        String accountHint,
        String tutorialMarkdown
) {
}
