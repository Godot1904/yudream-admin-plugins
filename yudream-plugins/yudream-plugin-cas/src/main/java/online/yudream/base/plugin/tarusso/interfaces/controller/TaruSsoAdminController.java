package online.yudream.base.plugin.tarusso.interfaces.controller;

import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.tarusso.bootstrap.TaruSsoPlugin;
import online.yudream.base.plugin.tarusso.interfaces.http.TaruSsoHttpFacade;

public final class TaruSsoAdminController {

    private final TaruSsoHttpFacade http;

    public TaruSsoAdminController(TaruSsoHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/settings", permission = TaruSsoPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse settings() {
        return http.settings();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/settings", permission = TaruSsoPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveSettings(PluginHttpRequest request) {
        return http.saveSettings(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/test", permission = TaruSsoPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse test() {
        return http.test();
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/oidc/register", permission = TaruSsoPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse registerOidc(PluginHttpRequest request) {
        return http.registerOidc(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/mapping", permission = TaruSsoPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse mapping() {
        return http.mapping();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/mapping", permission = TaruSsoPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveMapping(PluginHttpRequest request) {
        return http.saveMapping(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/students", permission = TaruSsoPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse students(PluginHttpRequest request) {
        return http.students(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/students/detail", permission = TaruSsoPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse studentDetail(PluginHttpRequest request) {
        return http.studentDetail(request);
    }

    /** 公开端点（permission 为空）：登录后的前端绑定门禁挂件读取。 */
    @PluginHttpEndpoint(method = "GET", path = "/public/gate")
    public PluginHttpResponse gate() {
        return http.gate();
    }

    /** 公开端点（permission 为空，但要求已登录）：绑定成功后按学工号取档案预填字段。 */
    @PluginHttpEndpoint(method = "GET", path = "/me/profile")
    public PluginHttpResponse myProfile(PluginHttpRequest request) {
        return http.myProfile(request);
    }
}
