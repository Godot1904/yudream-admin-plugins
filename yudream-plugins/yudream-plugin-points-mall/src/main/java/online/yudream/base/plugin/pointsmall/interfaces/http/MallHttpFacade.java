package online.yudream.base.plugin.pointsmall.interfaces.http;

import online.yudream.base.plugin.pointsmall.application.service.MallAppService;
import online.yudream.base.plugin.pointsmall.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.pointsmall.interfaces.assembler.MallWebAssembler;
import online.yudream.base.plugin.pointsmall.interfaces.request.MallItemEnabledRequest;
import online.yudream.base.plugin.pointsmall.interfaces.request.MallItemSaveRequest;
import online.yudream.base.plugin.pointsmall.interfaces.request.MallRedeemRequest;
import online.yudream.base.plugin.pointsmall.interfaces.request.MallRedemptionCancelRequest;
import online.yudream.base.plugin.pointsmall.interfaces.request.MallRedemptionDeliverRequest;
import online.yudream.base.plugin.pointsmall.interfaces.request.MallSettingsSaveRequest;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 积分商城的 HTTP 装配层。
 *
 * <p>用户端方法只从 {@code request.principal()} 取人，请求体里没有用户字段；管理员端方法才接受显式的
 * 筛选条件。控制器保持最薄，业务规则全部在应用服务与领域里。
 */
public class MallHttpFacade {

    private final MallAppService appService;
    private final MallWebAssembler assembler = new MallWebAssembler();

    public MallHttpFacade(MallAppService appService) {
        this.appService = appService;
    }

    // ------------------------------------------------------------------ 用户端

    public PluginHttpResponse overview(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(appService.overview(currentUserId(request))));
    }

    public PluginHttpResponse items(PluginHttpRequest request) {
        String keyword = stringQuery(request, "keyword");
        int page = page(request);
        int size = size(request);
        return PluginHttpResponse.ok(Map.of(
                "records", appService.listOpenItems(keyword, page, size).stream().map(assembler::toRes).toList(),
                "total", appService.countOpenItems(keyword)));
    }

    public PluginHttpResponse myRedemptions(PluginHttpRequest request) {
        String userId = currentUserId(request);
        int page = page(request);
        int size = size(request);
        return PluginHttpResponse.ok(Map.of(
                "records", appService.listMyRedemptions(userId, page, size).stream().map(assembler::toRes).toList(),
                "total", appService.countMyRedemptions(userId)));
    }

    public PluginHttpResponse redeem(PluginHttpRequest request) {
        MallRedeemRequest body = JsonSupport.read(request.body(), MallRedeemRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.redeem(currentUserId(request), assembler.toCmd(body))));
    }

    public PluginHttpResponse cancelMyRedemption(PluginHttpRequest request) {
        MallRedemptionCancelRequest body = JsonSupport.read(request.body(), MallRedemptionCancelRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.cancelMyRedemption(
                currentUserId(request), pathSegment(request.path(), 2), body == null ? null : body.reason())));
    }

    /** 用户确认收到已发放的兑换；只能确认自己的记录。 */
    public PluginHttpResponse confirmMyRedemption(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(
                appService.confirmReceipt(currentUserId(request), pathSegment(request.path(), 2))));
    }

    // ------------------------------------------------------------------ 管理员端

    public PluginHttpResponse adminItems(PluginHttpRequest request) {
        String keyword = stringQuery(request, "keyword");
        Boolean enabled = booleanQuery(request, "enabled");
        int page = page(request);
        int size = size(request);
        return PluginHttpResponse.ok(Map.of(
                "records",
                appService.adminItems(keyword, enabled, page, size).stream().map(assembler::toRes).toList(),
                "total", appService.adminItemCount(keyword, enabled)));
    }

    public PluginHttpResponse createItem(PluginHttpRequest request) {
        MallItemSaveRequest body = JsonSupport.read(request.body(), MallItemSaveRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.createItem(assembler.toCmd(body))));
    }

    public PluginHttpResponse updateItem(PluginHttpRequest request) {
        MallItemSaveRequest body = JsonSupport.read(request.body(), MallItemSaveRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(
                appService.updateItem(pathSegment(request.path(), 2), assembler.toCmd(body))));
    }

    public PluginHttpResponse setItemEnabled(PluginHttpRequest request) {
        MallItemEnabledRequest body = JsonSupport.read(request.body(), MallItemEnabledRequest.class);
        boolean enabled = body != null && body.enabled() != null && body.enabled();
        return PluginHttpResponse.ok(assembler.toRes(
                appService.setItemEnabled(pathSegment(request.path(), 2), enabled)));
    }

    public PluginHttpResponse deleteItem(PluginHttpRequest request) {
        appService.deleteItem(pathSegment(request.path(), 2));
        return PluginHttpResponse.ok(Map.of("deleted", true));
    }

    public PluginHttpResponse adminRedemptions(PluginHttpRequest request) {
        String userId = stringQuery(request, "userId");
        String itemId = stringQuery(request, "itemId");
        String status = stringQuery(request, "status");
        int page = page(request);
        int size = size(request);
        return PluginHttpResponse.ok(Map.of(
                "records",
                appService.adminRedemptions(userId, itemId, status, page, size).stream().map(assembler::toRes).toList(),
                "total", appService.adminRedemptionCount(userId, itemId, status)));
    }

    /** 管理员发放：必须带凭证，发放后等用户确认收货。 */
    public PluginHttpResponse deliverRedemption(PluginHttpRequest request) {
        MallRedemptionDeliverRequest body = JsonSupport.read(request.body(), MallRedemptionDeliverRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.deliverRedemption(
                pathSegment(request.path(), 2), currentUserId(request), assembler.toCmd(body))));
    }

    public PluginHttpResponse cancelRedemption(PluginHttpRequest request) {
        MallRedemptionCancelRequest body = JsonSupport.read(request.body(), MallRedemptionCancelRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.cancelRedemption(
                pathSegment(request.path(), 2), currentUserId(request), body == null ? null : body.reason())));
    }

    public PluginHttpResponse settings(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(appService.settings()));
    }

    public PluginHttpResponse saveSettings(PluginHttpRequest request) {
        MallSettingsSaveRequest body = JsonSupport.read(request.body(), MallSettingsSaveRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.saveSettings(assembler.toCmd(body))));
    }

    public PluginHttpResponse assets(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.assetOptions().stream().map(assembler::toRes).toList());
    }

    // ------------------------------------------------------------------ 解析

    /** 当前登录人；用户端只认这个来源，请求体与查询参数都不能指定别人。 */
    private String currentUserId(PluginHttpRequest request) {
        PluginPrincipal principal = request.principal();
        if (principal == null || principal.userId() == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return String.valueOf(principal.userId());
    }

    private int page(PluginHttpRequest request) {
        return intQuery(request, "page", 1);
    }

    private int size(PluginHttpRequest request) {
        return intQuery(request, "size", 10);
    }

    private int intQuery(PluginHttpRequest request, String key, int defaultValue) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() || values.get(0).isBlank()
                ? defaultValue
                : Integer.parseInt(values.get(0).trim());
    }

    /** 三态布尔查询：缺省返回 null（不限），避免把「未筛选」和「筛选 false」混为一谈。 */
    private Boolean booleanQuery(PluginHttpRequest request, String key) {
        List<String> values = request.query().get(key);
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(values.get(0).trim());
    }

    private String stringQuery(PluginHttpRequest request, String key) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() || values.get(0).isBlank() ? null : values.get(0).trim();
    }

    private String pathSegment(String path, int index) {
        String[] segments = trim(path).split("/");
        return index >= 0 && index < segments.length ? decode(segments[index]) : null;
    }

    private String trim(String path) {
        String value = path == null ? "" : path.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private String decode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
