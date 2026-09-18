package online.yudream.base.plugin.yggc.interfaces.controller;

import online.yudream.base.plugin.yggc.interfaces.http.YggcHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

/**
 * 传统 Yggdrasil 协议端点（对启动器 / 服务器开放，无权限要求）。
 * 统一挂在 /api/yggdrasil 前缀下，认证服务器地址形如：
 * https://host/api/plugins/yggc/api/yggdrasil
 */
public class YggcProtocolController {
    private final YggcHttpFacade http;

    public YggcProtocolController(YggcHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/api/yggdrasil", wrapResult = false)
    public PluginHttpResponse metadata(PluginHttpRequest request) { return http.metadata(request); }

    /**
     * 启动器免密会话兑换（与 authlib-injector 的 launcher/exchange 同形）：
     * 站点登录会话 → Yggdrasil 会话；{@code ?list=true} 仅列举角色。
     */
    @PluginHttpEndpoint(method = "POST", path = "/launcher/exchange", wrapResult = false)
    public PluginHttpResponse exchange(PluginHttpRequest request) { return http.exchange(request); }

    @PluginHttpEndpoint(method = "POST", path = "/api/yggdrasil/authserver/authenticate", wrapResult = false)
    public PluginHttpResponse authenticate(PluginHttpRequest request) { return http.authenticate(request); }

    @PluginHttpEndpoint(method = "POST", path = "/api/yggdrasil/authserver/refresh", wrapResult = false)
    public PluginHttpResponse refresh(PluginHttpRequest request) { return http.refresh(request); }

    @PluginHttpEndpoint(method = "POST", path = "/api/yggdrasil/authserver/validate", wrapResult = false)
    public PluginHttpResponse validate(PluginHttpRequest request) { return http.validate(request); }

    @PluginHttpEndpoint(method = "POST", path = "/api/yggdrasil/authserver/invalidate", wrapResult = false)
    public PluginHttpResponse invalidate(PluginHttpRequest request) { return http.invalidate(request); }

    @PluginHttpEndpoint(method = "POST", path = "/api/yggdrasil/authserver/signout", wrapResult = false)
    public PluginHttpResponse signout(PluginHttpRequest request) { return http.signout(request); }

    @PluginHttpEndpoint(method = "POST", path = "/api/yggdrasil/sessionserver/session/minecraft/join", wrapResult = false)
    public PluginHttpResponse join(PluginHttpRequest request) { return http.join(request); }

    @PluginHttpEndpoint(method = "GET", path = "/api/yggdrasil/sessionserver/session/minecraft/hasJoined", wrapResult = false)
    public PluginHttpResponse hasJoined(PluginHttpRequest request) { return http.hasJoined(request); }

    @PluginHttpEndpoint(method = "GET", path = "/api/yggdrasil/sessionserver/session/minecraft/profile/{uuid}", wrapResult = false)
    public PluginHttpResponse profile(PluginHttpRequest request) { return http.profile(request); }

    @PluginHttpEndpoint(method = "POST", path = "/api/yggdrasil/api/profiles/minecraft", wrapResult = false)
    public PluginHttpResponse profiles(PluginHttpRequest request) { return http.profiles(request); }

    @PluginHttpEndpoint(method = "GET", path = "/api/yggdrasil/api/users/profiles/minecraft/{username}", wrapResult = false)
    public PluginHttpResponse profileByName(PluginHttpRequest request) { return http.profileByName(request); }

    @PluginHttpEndpoint(method = "POST", path = "/api/yggdrasil/minecraftservices/minecraft/profile/lookup/bulk/byname", wrapResult = false)
    public PluginHttpResponse profilesByService(PluginHttpRequest request) { return http.profiles(request); }

    @PluginHttpEndpoint(method = "GET", path = "/api/yggdrasil/minecraftservices/minecraft/profile/lookup/name/{username}", wrapResult = false)
    public PluginHttpResponse profileByNameByService(PluginHttpRequest request) { return http.profileByName(request); }

    @PluginHttpEndpoint(method = "GET", path = "/api/yggdrasil/restore", wrapResult = false)
    public PluginHttpResponse restoreStatus(PluginHttpRequest request) { return http.restoreStatus(request); }

    @PluginHttpEndpoint(method = "POST", path = "/api/yggdrasil/restore", wrapResult = false)
    public PluginHttpResponse restore(PluginHttpRequest request) { return http.restore(request); }

    @PluginHttpEndpoint(method = "PUT", path = "/api/yggdrasil/api/user/profile/{uuid}/{textureType}", wrapResult = false)
    public PluginHttpResponse setTexture(PluginHttpRequest request) { return http.setTexture(request); }

    @PluginHttpEndpoint(method = "DELETE", path = "/api/yggdrasil/api/user/profile/{uuid}/{textureType}", wrapResult = false)
    public PluginHttpResponse clearTexture(PluginHttpRequest request) { return http.clearTexture(request); }
}
