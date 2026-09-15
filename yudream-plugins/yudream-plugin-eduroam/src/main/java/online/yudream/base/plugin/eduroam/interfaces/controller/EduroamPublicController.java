package online.yudream.base.plugin.eduroam.interfaces.controller;

import online.yudream.base.plugin.eduroam.interfaces.http.EduroamHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

/**
 * 公开端接口：第三方登录凭据页使用，不需要登录也没有权限要求。
 *
 * <p>只暴露凭据页必需的信息：渠道开关、账号填写提示与教程；认证服务地址、限流参数、审计数据都不在这里。
 * 这里不做登录态签发——会话由宿主在回调端点核销票据后统一签发。
 */
public class EduroamPublicController {

    private final EduroamHttpFacade http;

    public EduroamPublicController(EduroamHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/public/config")
    public PluginHttpResponse config() {
        return http.publicConfig();
    }

    @PluginHttpEndpoint(method = "POST", path = "/public/login")
    public PluginHttpResponse login(PluginHttpRequest request) {
        return http.login(request);
    }
}
