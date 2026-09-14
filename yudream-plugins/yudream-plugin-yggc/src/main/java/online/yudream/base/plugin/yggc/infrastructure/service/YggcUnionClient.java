package online.yudream.base.plugin.yggc.infrastructure.service;

import online.yudream.base.plugin.yggc.infrastructure.support.JsonSupport;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Union 主服务器（api root）HTTP 客户端：完整覆盖成员站所需的上游接口。
 * 认证方式遵循原 yggdrasil-connect 协议：请求头 X-Union-Member-Key。
 */
public class YggcUnionClient {

    public static final String MEMBER_KEY_HEADER = "X-Union-Member-Key";

    private static final int TIMEOUT_SECONDS = 8;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 一次上游调用的结果。 */
    public record UnionResult(int status, String body, Map<String, Object> json, long latencyMs) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public String text(String field) {
            Object value = json == null ? null : json.get(field);
            return value == null ? null : String.valueOf(value);
        }
    }

    // ---- 基础调用 ----

    public UnionResult get(String apiRoot, String path, String memberKey) {
        return call("GET", apiRoot, path, null, memberKey);
    }

    public UnionResult post(String apiRoot, String path, Object payload, String memberKey) {
        return call("POST", apiRoot, path, payload, memberKey);
    }

    public UnionResult put(String apiRoot, String path, Object payload, String memberKey) {
        return call("PUT", apiRoot, path, payload, memberKey);
    }

    public UnionResult delete(String apiRoot, String path, String memberKey) {
        return call("DELETE", apiRoot, path, null, memberKey);
    }

    public UnionResult call(String method, String apiRoot, String path, Object payload, String memberKey) {
        String url = join(apiRoot, path);
        long startedAt = System.currentTimeMillis();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("Accept", "application/json");
            if (memberKey != null && !memberKey.isBlank()) {
                builder.header(MEMBER_KEY_HEADER, memberKey);
            }
            if (payload != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(
                                payload instanceof String text ? text : JsonSupport.write(payload),
                                StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new UnionResult(response.statusCode(), response.body(), parseJson(response.body()),
                    System.currentTimeMillis() - startedAt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new UnionResult(0, "请求被中断：" + describe(e), null,
                    System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            return new UnionResult(0, "无法连接 Union 主服务器：" + describe(e), null,
                    System.currentTimeMillis() - startedAt);
        }
    }

    /** 异常信息可能为空（如 DNS 解析失败），此时回落到异常类型名，避免提示里出现 null。 */
    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    // ---- 连通性诊断（设置页按钮）----

    public Map<String, Object> diagnose(String apiRoot, String memberKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiRoot", apiRoot == null ? "" : apiRoot);
        result.put("memberKeyConfigured", memberKey != null && !memberKey.isBlank());
        if (apiRoot == null || apiRoot.isBlank()) {
            result.put("reachable", false);
            result.put("message", "未配置 Union API Root");
            return result;
        }
        UnionResult hello = get(apiRoot, "", null);
        result.put("reachable", hello.ok());
        result.put("status", hello.status());
        result.put("latencyMs", hello.latencyMs());
        result.put("body", hello.body());
        if (!hello.ok()) {
            result.put("message", hello.status() == 0 ? hello.body()
                    : "Union 主服务器返回状态码 " + hello.status());
            return result;
        }
        // 成员身份验证：POST /diagnose 需要正确的 member key
        UnionResult diagnose = post(apiRoot, "/diagnose", Map.of(), memberKey);
        result.put("memberKeyValid", diagnose.ok());
        result.put("message", diagnose.ok() ? "Union 主服务器可访问，Member Key 验证通过"
                : (diagnose.status() == 0 ? diagnose.body() : "Member Key 验证失败（HTTP " + diagnose.status() + "）"));
        return result;
    }

    // ---- 内部 ----

    private static String join(String apiRoot, String path) {
        String root = apiRoot == null ? "" : apiRoot.trim();
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        String suffix = path == null ? "" : path;
        if (!suffix.isEmpty() && !suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        return root + suffix;
    }

    private static Map<String, Object> parseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JsonSupport.readMap(body);
        } catch (Exception e) {
            return null;
        }
    }

    /** URL 路径参数编码。 */
    public static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
