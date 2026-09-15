package online.yudream.base.plugin.eduroam.interfaces.controller;

import online.yudream.base.plugin.eduroam.bootstrap.EduroamPlugin;
import online.yudream.base.plugin.eduroam.interfaces.http.EduroamHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

/**
 * 管理端接口：查看登录过的校园网账号、封禁 / 解封、审计与渠道配置，全部要求管理权限。
 */
public class EduroamAdminController {

    private final EduroamHttpFacade http;

    public EduroamAdminController(EduroamHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/accounts", permission = EduroamPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse accounts(PluginHttpRequest request) {
        return http.adminAccounts(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/accounts/{id}", permission = EduroamPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse accountDetail(PluginHttpRequest request) {
        return http.adminAccountDetail(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/accounts/{id}/block",
            permission = EduroamPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse block(PluginHttpRequest request) {
        return http.blockAccount(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/accounts/{id}/unblock",
            permission = EduroamPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse unblock(PluginHttpRequest request) {
        return http.unblockAccount(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/accounts/{id}",
            permission = EduroamPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse delete(PluginHttpRequest request) {
        return http.deleteAccount(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/attempts", permission = EduroamPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse attempts(PluginHttpRequest request) {
        return http.adminAttempts(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/settings", permission = EduroamPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse settings(PluginHttpRequest request) {
        return http.settings();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/settings", permission = EduroamPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveSettings(PluginHttpRequest request) {
        return http.saveSettings(request);
    }
}
