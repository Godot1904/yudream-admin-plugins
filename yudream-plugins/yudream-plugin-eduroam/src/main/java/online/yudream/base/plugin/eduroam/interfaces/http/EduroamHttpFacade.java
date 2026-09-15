package online.yudream.base.plugin.eduroam.interfaces.http;

import online.yudream.base.plugin.eduroam.application.service.EduroamAppService;
import online.yudream.base.plugin.eduroam.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.eduroam.interfaces.assembler.EduroamWebAssembler;
import online.yudream.base.plugin.eduroam.interfaces.request.EduroamLoginRequest;
import online.yudream.base.plugin.eduroam.interfaces.request.EduroamReviewRequest;
import online.yudream.base.plugin.eduroam.interfaces.request.EduroamSettingsSaveRequest;
import online.yudream.base.plugin.eduroam.interfaces.res.EduroamAccountRes;
import online.yudream.base.plugin.eduroam.interfaces.res.EduroamAttemptRes;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eduroam 第三方登录的 HTTP 装配层。
 *
 * <p>公开端只服务「凭据页」：读渠道配置、提交校园网账号密码换票据（票据随后由宿主回调端点核销）。
 * 管理端维护登录账号、封禁与渠道配置，全部要求管理权限。
 */
public class EduroamHttpFacade {

    private final EduroamAppService app;
    private final EduroamWebAssembler assembler = new EduroamWebAssembler();

    public EduroamHttpFacade(EduroamAppService app) {
        this.app = app;
    }

    // ------------------------------------------------------------------ 公开端（凭据页）

    public PluginHttpResponse publicConfig() {
        return PluginHttpResponse.ok(assembler.toRes(app.publicConfig()));
    }

    /** 校验校园网凭据并签发一次性登录票据；失败时同样返回 200 + success=false，由页面展示原因。 */
    public PluginHttpResponse login(PluginHttpRequest request) {
        EduroamLoginRequest body = JsonSupport.read(request.body(), EduroamLoginRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(app.authenticate(assembler.toCmd(body), clientIp(request))));
    }

    // ------------------------------------------------------------------ 管理端

    public PluginHttpResponse adminAccounts(PluginHttpRequest request) {
        String status = firstQuery(request, "status");
        String keyword = firstQuery(request, "keyword");
        int page = intQuery(request, "page", 1);
        int size = intQuery(request, "size", 10);
        List<EduroamAccountRes> records = app.listAccounts(status, keyword, page, size).stream()
                .map(assembler::toRes)
                .toList();
        return PluginHttpResponse.ok(page(records, app.countAccounts(status, keyword), page, size));
    }

    public PluginHttpResponse adminAccountDetail(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(app.accountDetail(pathSegment(request.path(), 2))));
    }

    public PluginHttpResponse blockAccount(PluginHttpRequest request) {
        EduroamReviewRequest body = JsonSupport.read(request.body(), EduroamReviewRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(app.blockAccount(
                pathSegment(request.path(), 2), currentUserId(request), assembler.toCmd(body).reason())));
    }

    public PluginHttpResponse unblockAccount(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(
                app.unblockAccount(pathSegment(request.path(), 2), currentUserId(request))));
    }

    public PluginHttpResponse deleteAccount(PluginHttpRequest request) {
        app.deleteAccount(pathSegment(request.path(), 2));
        return PluginHttpResponse.ok(Map.of("deleted", true));
    }

    public PluginHttpResponse adminAttempts(PluginHttpRequest request) {
        String keyword = firstQuery(request, "keyword");
        Boolean success = booleanQuery(request, "success");
        int page = intQuery(request, "page", 1);
        int size = intQuery(request, "size", 20);
        List<EduroamAttemptRes> records = app.listAttempts(keyword, success, page, size).stream()
                .map(assembler::toRes)
                .toList();
        return PluginHttpResponse.ok(page(records, app.countAttempts(keyword, success), page, size));
    }

    public PluginHttpResponse settings() {
        return PluginHttpResponse.ok(assembler.toRes(app.settingsView()));
    }

    public PluginHttpResponse saveSettings(PluginHttpRequest request) {
        EduroamSettingsSaveRequest body = JsonSupport.read(request.body(), EduroamSettingsSaveRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(app.saveSettings(assembler.toCmd(body))));
    }

    // ------------------------------------------------------------------ 解析

    private Map<String, Object> page(List<?> records, long total, int page, int size) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("records", records);
        payload.put("total", total);
        payload.put("page", Math.max(page, 1));
        payload.put("size", size);
        return payload;
    }

    private String currentUserId(PluginHttpRequest request) {
        if (request.principal() == null || request.principal().userId() == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return String.valueOf(request.principal().userId());
    }

    /** 取真实客户端 IP：优先反代头，便于按用户而不是按网关限流。 */
    private String clientIp(PluginHttpRequest request) {
        String forwarded = firstHeader(request, "x-forwarded-for");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        String realIp = firstHeader(request, "x-real-ip");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String original = firstHeader(request, "x-original-forwarded-for");
        return original == null ? "" : original.trim();
    }

    private String firstHeader(PluginHttpRequest request, String name) {
        if (request.headers() == null || name == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            if (entry.getKey() != null && name.equalsIgnoreCase(entry.getKey())
                    && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    private int intQuery(PluginHttpRequest request, String key, int defaultValue) {
        List<String> values = request.query().get(key);
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(values.get(0).trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /** 三态布尔查询：缺省返回 null（不限），避免把「未筛选」和「筛选 false」混为一谈。 */
    private Boolean booleanQuery(PluginHttpRequest request, String key) {
        List<String> values = request.query().get(key);
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(values.get(0).trim());
    }

    private String firstQuery(PluginHttpRequest request, String key) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() || values.get(0).isBlank() ? null : values.get(0).trim();
    }

    private String pathSegment(String path, int index) {
        String value = path == null ? "" : path.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        String[] segments = value.split("/");
        if (index < 0 || index >= segments.length) {
            throw new IllegalArgumentException("路径参数缺失");
        }
        return URLDecoder.decode(segments[index], StandardCharsets.UTF_8);
    }
}
