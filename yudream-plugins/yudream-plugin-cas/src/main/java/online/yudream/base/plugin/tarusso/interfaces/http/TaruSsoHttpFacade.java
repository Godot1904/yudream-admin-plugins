package online.yudream.base.plugin.tarusso.interfaces.http;

import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.tarusso.application.dto.SsoSettingsDto;
import online.yudream.base.plugin.tarusso.application.service.SettingsService;
import online.yudream.base.plugin.tarusso.application.service.StudentInfoService;
import online.yudream.base.plugin.tarusso.domain.aggregate.StudentMapping;
import online.yudream.base.plugin.tarusso.domain.service.SsoProtocolClient;
import online.yudream.base.plugin.tarusso.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.tarusso.interfaces.request.OidcRegisterRequest;
import online.yudream.base.plugin.tarusso.interfaces.request.SettingsSaveRequest;
import online.yudream.base.plugin.tarusso.interfaces.request.StudentMappingSaveRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TaruSsoHttpFacade {

    private final SettingsService settings;
    private final StudentInfoService studentInfo;

    public TaruSsoHttpFacade(SettingsService settings, StudentInfoService studentInfo) {
        this.settings = settings;
        this.studentInfo = studentInfo;
    }

    public PluginHttpResponse settings() {
        return PluginHttpResponse.ok(settings.view());
    }

    public PluginHttpResponse saveSettings(PluginHttpRequest request) {
        SettingsSaveRequest body = JsonSupport.read(request.body(), SettingsSaveRequest.class);
        SsoSettingsDto current = settings.view();
        SsoSettingsDto incoming = new SsoSettingsDto(
                body.enabled() == null ? current.enabled() : body.enabled(),
                firstNonBlank(body.protocol(), current.protocol()),
                firstNonBlank(body.displayName(), current.displayName()),
                firstNonBlank(body.icon(), current.icon()),
                firstNonBlank(body.casBaseUrl(), current.casBaseUrl()),
                firstNonBlank(body.loginPath(), current.loginPath()),
                firstNonBlank(body.validatePath(), current.validatePath()),
                firstNonBlank(body.oidcIssuer(), current.oidcIssuer()),
                firstNonBlank(body.oidcAuthorizePath(), current.oidcAuthorizePath()),
                firstNonBlank(body.oidcTokenPath(), current.oidcTokenPath()),
                firstNonBlank(body.oidcUserinfoPath(), current.oidcUserinfoPath()),
                firstNonBlank(body.oidcJwksPath(), current.oidcJwksPath()),
                firstNonBlank(body.oidcRegisterPath(), current.oidcRegisterPath()),
                body.clientId() == null ? current.clientId() : body.clientId().trim(),
                current.clientSecretConfigured(),
                firstNonBlank(body.scopes(), current.scopes()),
                firstNonBlank(body.callbackUrl(), current.callbackUrl()),
                false
        );
        return PluginHttpResponse.ok(settings.save(incoming, body.clientSecret()));
    }

    public PluginHttpResponse test() {
        SsoProtocolClient.ConnectivityResult result = settings.test();
        return PluginHttpResponse.ok(Map.of("ok", result.ok(), "message", result.message()));
    }

    public PluginHttpResponse registerOidc(PluginHttpRequest request) {
        OidcRegisterRequest body = JsonSupport.read(request.body(), OidcRegisterRequest.class);
        return PluginHttpResponse.ok(settings.registerOidcClient(body == null ? null : body.clientName()));
    }

    public PluginHttpResponse mapping() {
        return PluginHttpResponse.ok(mappingToMap(studentInfo.mapping()));
    }

    public PluginHttpResponse saveMapping(PluginHttpRequest request) {
        StudentMappingSaveRequest body = JsonSupport.read(request.body(), StudentMappingSaveRequest.class);
        if (body == null) {
            throw new IllegalArgumentException("映射配置不能为空");
        }
        StudentMapping current = studentInfo.mapping();
        StudentMapping incoming = new StudentMapping(
                body.requireBinding() == null ? current.requireBinding() : body.requireBinding(),
                firstNonBlank(body.nameKey(), current.nameKey()),
                firstNonBlank(body.deptKey(), current.deptKey()),
                firstNonBlank(body.majorKey(), current.majorKey()),
                firstNonBlank(body.gradeKey(), current.gradeKey()),
                firstNonBlank(body.classKey(), current.classKey())
        );
        return PluginHttpResponse.ok(mappingToMap(studentInfo.saveMapping(incoming)));
    }

    public PluginHttpResponse students(PluginHttpRequest request) {
        int page = intParam(request, "page", 1);
        int size = intParam(request, "size", 20);
        if (size < 1 || size > 100) {
            size = 20;
        }
        return PluginHttpResponse.ok(studentInfo.page(page, size, queryParam(request, "keyword")));
    }

    public PluginHttpResponse studentDetail(PluginHttpRequest request) {
        String socialUid = queryParam(request, "socialUid");
        if (socialUid == null || socialUid.isBlank()) {
            throw new IllegalArgumentException("缺少学工号参数");
        }
        return studentInfo.detail(socialUid.trim())
                .map(PluginHttpResponse::ok)
                .orElseThrow(() -> new IllegalArgumentException("学生档案不存在: " + socialUid));
    }

    /**
     * 绑定门禁公开端点：前端全局挂件轮询。
     * requireBinding 仅在 CAS 登录入口启用时生效，避免配置半途把用户全部锁死。
     */
    public PluginHttpResponse gate() {
        boolean loginReady = settings.current().loginEnabled();
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("requireBinding", studentInfo.mapping().requireBinding() && loginReady);
        gate.put("providerCode", "cas");
        gate.put("type", settings.current().protocol().typeCode());
        gate.put("displayName", settings.current().displayName());
        return PluginHttpResponse.ok(gate);
    }

    /**
     * 学生档案预填公开端点：已绑定 CAS 的登录用户按绑定记录里的学工号取预填字段，
     * 前端据此把姓名/学号/学院（以及 CAS 提供时的班级）填进 yudream-student-info 插件的「我的档案」表单。
     * <p>端点本身不设权限注解，但要求已登录（principal 存在），且只返回预填所需的最小字段集。</p>
     */
    public PluginHttpResponse myProfile(PluginHttpRequest request) {
        if (request.principal() == null || request.principal().userId() == null) {
            throw new IllegalArgumentException("请先登录");
        }
        String socialUid = queryParam(request, "socialUid");
        if (socialUid == null) {
            throw new IllegalArgumentException("缺少学工号参数");
        }
        return studentInfo.prefill(socialUid)
                .map(PluginHttpResponse::ok)
                .orElseGet(() -> PluginHttpResponse.rawJson(404, Map.of("message", "学生档案不存在")));
    }

    private static Map<String, Object> mappingToMap(StudentMapping mapping) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("requireBinding", mapping.requireBinding());
        view.put("nameKey", mapping.nameKey());
        view.put("deptKey", mapping.deptKey());
        view.put("majorKey", mapping.majorKey());
        view.put("gradeKey", mapping.gradeKey());
        view.put("classKey", mapping.classKey());
        return view;
    }

    private static String queryParam(PluginHttpRequest request, String name) {
        List<String> values = request.query().get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String value = values.get(0);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int intParam(PluginHttpRequest request, String name, int fallback) {
        String raw = queryParam(request, name);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
