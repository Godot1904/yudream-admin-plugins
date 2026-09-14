package online.yudream.base.plugin.pointsmall.interfaces.controller;

import online.yudream.base.plugin.pointsmall.bootstrap.PointsMallPlugin;
import online.yudream.base.plugin.pointsmall.interfaces.http.MallHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

/**
 * 用户端接口：只服务当前登录人，路径全部挂在 {@code /me/**} 下。
 *
 * <p>这里没有「管理员可以看别人」的分支：兑换、我的兑换、取消都只作用于 {@code request.principal()}
 * 对应的用户。
 */
public class MallUserController {

    private final MallHttpFacade http;

    public MallUserController(MallHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/overview", permission = PointsMallPlugin.VIEW_PERMISSION)
    public PluginHttpResponse overview(PluginHttpRequest request) {
        return http.overview(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/items", permission = PointsMallPlugin.VIEW_PERMISSION)
    public PluginHttpResponse items(PluginHttpRequest request) {
        return http.items(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/redemptions", permission = PointsMallPlugin.VIEW_PERMISSION)
    public PluginHttpResponse myRedemptions(PluginHttpRequest request) {
        return http.myRedemptions(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/redemptions", permission = PointsMallPlugin.USE_PERMISSION)
    public PluginHttpResponse redeem(PluginHttpRequest request) {
        return http.redeem(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/redemptions/{redemptionId}/cancel",
            permission = PointsMallPlugin.USE_PERMISSION)
    public PluginHttpResponse cancelMyRedemption(PluginHttpRequest request) {
        return http.cancelMyRedemption(request);
    }

    /** 确认收到已发放的兑换：只有兑换人自己能确认。 */
    @PluginHttpEndpoint(method = "POST", path = "/me/redemptions/{redemptionId}/confirm",
            permission = PointsMallPlugin.USE_PERMISSION)
    public PluginHttpResponse confirmMyRedemption(PluginHttpRequest request) {
        return http.confirmMyRedemption(request);
    }
}
