package online.yudream.base.plugin.eduroam.infrastructure.service;

import online.yudream.base.plugin.eduroam.application.service.EduroamProbePort;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import online.yudream.base.plugin.eduroam.domain.valobj.EduroamProbeOutcome;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 通过可配置的 Eduroam 认证服务校验账号密码。
 *
 * <p>提交方式与原 auth-eduroam 插件一致：表单 POST {@code login=账号&password=密码}，再按响应文本判定结果。
 * 每次探测单独创建 HttpClient，只是为了按配置取连接超时；探测本身频率很低，不值得为复用连接池做全局缓存。
 */
public class HttpEduroamProbe implements EduroamProbePort {

    private static final String USER_AGENT = "YuDream-Eduroam/1.0";

    @Override
    public EduroamProbeOutcome probe(String identity, String password, EduroamSettings settings) {
        long startedAt = System.currentTimeMillis();
        EduroamSettings safe = settings == null ? EduroamSettings.defaults() : settings;
        String endpoint = safe.verifyEndpoint() == null || safe.verifyEndpoint().isBlank()
                ? EduroamSettings.DEFAULT_ENDPOINT
                : safe.verifyEndpoint().trim();
        String form = "login=" + encode(identity) + "&password=" + encode(password);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(safe.connectTimeoutSeconds()))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(safe.requestTimeoutSeconds()))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return EduroamResponseParser.parse(response.statusCode(), response.body(), elapsed(startedAt));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return EduroamProbeOutcome.unreachable("请求被中断", elapsed(startedAt));
        } catch (RuntimeException | java.io.IOException failure) {
            return EduroamProbeOutcome.unreachable(describe(failure), elapsed(startedAt));
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static long elapsed(long startedAt) {
        return Math.max(System.currentTimeMillis() - startedAt, 0L);
    }

    /** 异常信息可能为空（如 DNS 解析失败），回落到类型名，避免提示里出现 null。 */
    private static String describe(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
