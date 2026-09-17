package online.yudream.base.plugin.tarusso.interfaces.http;

import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.tarusso.application.dto.SsoSettingsDto;
import online.yudream.base.plugin.tarusso.application.service.SettingsService;
import online.yudream.base.plugin.tarusso.application.service.StudentInfoService;
import online.yudream.base.plugin.tarusso.domain.aggregate.SsoSettings;
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
                body.loginWarmup() == null ? current.loginWarmup() : body.loginWarmup(),
                false
        );
        return PluginHttpResponse.ok(settings.save(incoming, body.clientSecret()));
    }

    public PluginHttpResponse test() {
        SsoProtocolClient.ConnectivityResult result = settings.test();
        return PluginHttpResponse.ok(Map.of("ok", result.ok(), "message", result.message()));
    }

    /**
     * 登录前预热页（公开、无权限注解）：点第三方登录后先落到本站这个页面。
     *
     * <p>部分前置网关对"首次、不带其会话 cookie（如 {@code route}）"的请求直接回 404，而同 URL 第二次访问
     * 就正常。这里先发一次跨站请求把该 cookie 种下来，再跳真正的认证地址；浏览器拦截第三方 cookie 时预热
     * 自然无效，页面仍会照常跳转（等价于原来的行为，用户再点一次即可）。
     *
     * <p>安全性：页面 URL 只带宿主签发的 {@code state}，真正的目标地址由服务端按当前配置重建，
     * 因此不存在可被外部利用的任意跳转目标。
     */
    public PluginHttpResponse warmup(PluginHttpRequest request) {
        String state = queryParam(request, "state");
        if (state == null) {
            return PluginHttpResponse.rawJson(400, Map.of("message", "缺少 state 参数"));
        }
        SsoSettings current = settings.current();
        String target = settings.client(current.protocol()).rawAuthorizationUrl(current, state);
        return new PluginHttpResponse(200,
                Map.of("Cache-Control", "no-store"),
                "text/html; charset=UTF-8",
                warmupPage(target, current.loginWarmup()),
                false);
    }

    /** 预热页 HTML：先跨站预热再 location.replace 到目标；任何异常都兜底跳转。 */
    static String warmupPage(String target, boolean warmup) {
        String preheat = warmup
                ? """
                  try {
                    var controller = new AbortController()
                    setTimeout(function () { controller.abort() }, 1200)
                    fetch(target, { mode: 'no-cors', credentials: 'include', cache: 'no-store',
                      signal: controller.signal }).catch(function () {}).then(go)
                  } catch (e) { go() }
                  """
                : "go()";
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="referrer" content="no-referrer">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>正在前往统一身份认证…</title>
                </head>
                <body style="font-family:system-ui,-apple-system,'Segoe UI',sans-serif;padding:32px;color:#4b5563">
                <p>正在前往统一身份认证，请稍候…</p>
                <p><a id="fallback" href="#">如果没有自动跳转，请点这里</a></p>
                <script>
                (function () {
                  var target = %s
                  var done = false
                  function go() {
                    if (done) { return }
                    done = true
                    location.replace(target)
                  }
                  document.getElementById('fallback').setAttribute('href', target)
                  %s
                  setTimeout(go, 1600)
                })()
                </script>
                </body>
                </html>
                """.formatted(jsString(target), preheat);
    }

    /** 把 URL 安全地放进 JS 字符串字面量（同时挡掉 </script> 之类的闭合注入）。 */
    private static String jsString(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("<", "\\u003c").replace(">", "\\u003e")
                .replace("&", "\\u0026").replace("\n", "").replace("\r", "") + "\"";
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
