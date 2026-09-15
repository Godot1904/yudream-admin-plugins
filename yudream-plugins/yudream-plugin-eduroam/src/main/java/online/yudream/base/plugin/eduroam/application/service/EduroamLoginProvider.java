package online.yudream.base.plugin.eduroam.application.service;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamLoginTicket;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginAuthorizeRequest;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginDescriptor;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginExchangeRequest;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginIdentity;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginProvider;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Eduroam 外部登录提供方。
 *
 * <p>Eduroam 没有像 CAS/OIDC 那样的授权服务器可跳转，它校验的是「校园网账号 + 密码」本身，
 * 所以这里的「授权方」就是本站自己的凭据页：
 * <ol>
 *   <li>{@link #authorizationUrl} 把浏览器带到本站 {@code /eduroam} 凭据页，并把宿主的 state 原样带上；</li>
 *   <li>凭据页认证成功后拿到一次性票据，回宿主回调端点；</li>
 *   <li>{@link #exchange} 用票据换出外部身份（{@code socialUid} = Eduroam 账号），宿主据此建立绑定并签发会话。</li>
 * </ol>
 *
 * <p>{@code descriptor()} 与 {@code enabled()} 会被宿主高频调用，两者都只读应用服务里的内存缓存配置，
 * 不做任何 IO。
 */
public final class EduroamLoginProvider implements PluginExternalLoginProvider {

    /** 提供方编码，必须与插件 code 一致：宿主按它路由 authorize/callback。 */
    public static final String PROVIDER_CODE = "eduroam";
    /** 本提供方唯一的登录方式类型，出现在 /api/external-login/eduroam/{type}/authorize 里。 */
    public static final String PROVIDER_TYPE = "eduroam";
    /** 登录页按钮文案。 */
    public static final String DISPLAY_NAME = "Eduroam 认证";
    private static final String ICON = "i-ri:wifi-line";
    /** 凭据页路由：插件公开页，本体在插件前端包里。 */
    private static final String CREDENTIAL_PAGE = "/eduroam";

    private final EduroamAppService app;

    public EduroamLoginProvider(EduroamAppService app) {
        this.app = app;
    }

    @Override
    public PluginExternalLoginDescriptor descriptor() {
        return new PluginExternalLoginDescriptor(PROVIDER_CODE, DISPLAY_NAME, ICON, List.of(PROVIDER_TYPE), 30);
    }

    @Override
    public boolean enabled() {
        return app.settings().enabled();
    }

    @Override
    public String authorizationUrl(PluginExternalLoginAuthorizeRequest request) {
        if (request == null || request.state() == null || request.state().isBlank()) {
            throw new IllegalArgumentException("登录请求缺少 state");
        }
        ensureType(request.platformType());
        return CREDENTIAL_PAGE
                + "?state=" + encode(request.state())
                + "&provider=" + encode(PROVIDER_CODE)
                + "&type=" + encode(PROVIDER_TYPE);
    }

    @Override
    public PluginExternalLoginIdentity exchange(PluginExternalLoginExchangeRequest request) {
        if (request == null || request.ticket() == null || request.ticket().isBlank()) {
            throw new IllegalArgumentException("登录回调缺少票据");
        }
        ensureType(request.platformType());
        EduroamLoginTicket ticket = app.consumeTicket(request.ticket(), request.state(), request.platformType());
        return new PluginExternalLoginIdentity(
                ticket.identity(),
                ticket.account().isBlank() ? ticket.email() : ticket.account(),
                null,
                null,
                null
        );
    }

    private void ensureType(String platformType) {
        if (platformType != null && !platformType.isBlank() && !PROVIDER_TYPE.equalsIgnoreCase(platformType.trim())) {
            throw new IllegalArgumentException("当前只支持 " + PROVIDER_TYPE + " 登录方式，不支持 " + platformType);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
