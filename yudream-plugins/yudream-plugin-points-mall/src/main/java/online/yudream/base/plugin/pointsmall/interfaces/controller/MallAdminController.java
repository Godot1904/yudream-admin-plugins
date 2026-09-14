package online.yudream.base.plugin.pointsmall.interfaces.controller;

import online.yudream.base.plugin.pointsmall.bootstrap.PointsMallPlugin;
import online.yudream.base.plugin.pointsmall.interfaces.http.MallHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

/**
 * 管理端接口：跨用户查询与维护，全部挂在 {@code /admin/**} 下并要求管理权限。
 *
 * <p>用户自己的兑换在用户端取消；这里是管理员代客操作（发放、取消退款），每条记录都会写入操作人。
 */
public class MallAdminController {

    private final MallHttpFacade http;

    public MallAdminController(MallHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/items", permission = PointsMallPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse items(PluginHttpRequest request) {
        return http.adminItems(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/items", permission = PointsMallPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse createItem(PluginHttpRequest request) {
        return http.createItem(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/items/{itemId}", permission = PointsMallPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse updateItem(PluginHttpRequest request) {
        return http.updateItem(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/items/{itemId}/enabled",
            permission = PointsMallPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse setItemEnabled(PluginHttpRequest request) {
        return http.setItemEnabled(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/items/{itemId}", permission = PointsMallPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deleteItem(PluginHttpRequest request) {
        return http.deleteItem(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/redemptions", permission = PointsMallPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse redemptions(PluginHttpRequest request) {
        return http.adminRedemptions(request);
    }

    /** 发放兑换：请求体带发放备注与凭证；发放后记录转为「待确认收货」。 */
    @PluginHttpEndpoint(method = "POST", path = "/admin/redemptions/{redemptionId}/deliver",
            permission = PointsMallPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deliverRedemption(PluginHttpRequest request) {
        return http.deliverRedemption(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/redemptions/{redemptionId}/cancel",
            permission = PointsMallPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse cancelRedemption(PluginHttpRequest request) {
        return http.cancelRedemption(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/settings", permission = PointsMallPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse settings(PluginHttpRequest request) {
        return http.settings(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/settings", permission = PointsMallPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveSettings(PluginHttpRequest request) {
        return http.saveSettings(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/assets", permission = PointsMallPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse assets(PluginHttpRequest request) {
        return http.assets(request);
    }
}
