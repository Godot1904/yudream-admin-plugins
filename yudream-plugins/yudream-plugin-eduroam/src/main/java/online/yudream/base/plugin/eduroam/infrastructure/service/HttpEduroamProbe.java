package online.yudream.base.plugin.eduroam.infrastructure.service;

import online.yudream.base.plugin.eduroam.application.service.EduroamProbePort;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import online.yudream.base.plugin.eduroam.domain.enumerate.EduroamFailureReason;
import online.yudream.base.plugin.eduroam.domain.valobj.EduroamProbeOutcome;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通过可配置的 Eduroam 认证服务校验账号密码。
 *
 * <p>北大探测点先获取页面 Cookie / CSRF，再向同站的 PEAP-MSCHAP 接口提交凭据。
 * 其他自定义服务保留原有的 {@code login/password} 表单协议。每次探测独立会话，结束后释放连接。
 */
public class HttpEduroamProbe implements EduroamProbePort {

    private static final String USER_AGENT = "YuDream-Eduroam/1.0";
    private static final Pattern META = Pattern.compile("(?is)<meta\\b[^>]*>");
    private static final Pattern ATTRIBUTE = Pattern.compile("(?is)([\\w-]+)\\s*=\\s*([\"'])(.*?)\\2");

    @Override
    public EduroamProbeOutcome probe(String identity, String password, EduroamSettings settings) {
        long startedAt = System.currentTimeMillis();
        EduroamSettings safe = settings == null ? EduroamSettings.defaults() : settings;
        String endpoint = safe.verifyEndpoint() == null || safe.verifyEndpoint().isBlank()
                ? EduroamSettings.DEFAULT_ENDPOINT
                : safe.verifyEndpoint().trim();
        try {
            URI uri = URI.create(endpoint);
            boolean pku = isPkuDetection(uri);
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(safe.connectTimeoutSeconds()))
                    .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                    .followRedirects(pku ? HttpClient.Redirect.NEVER : HttpClient.Redirect.NORMAL)
                    .build()) {
                if (pku) {
                    return probePku(client, uri, identity, password, safe, startedAt);
                }
                String form = "login=" + encode(identity) + "&password=" + encode(password);
                HttpRequest request = request(uri, safe)
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .header("Accept", "text/html,application/xhtml+xml")
                        .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = send(client, request);
                return EduroamResponseParser.parse(response.statusCode(), response.body(), elapsed(startedAt));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return EduroamProbeOutcome.unreachable("请求被中断", elapsed(startedAt));
        } catch (RuntimeException | IOException failure) {
            return EduroamProbeOutcome.unreachable(describe(failure), elapsed(startedAt));
        }
    }

    private static EduroamProbeOutcome probePku(HttpClient client, URI pageUri, String identity, String password,
                                                EduroamSettings settings, long startedAt)
            throws IOException, InterruptedException {
        HttpResponse<String> page = send(client, request(pageUri, settings)
                .header("Accept", "text/html").GET().build());
        if (page.statusCode() != 200) {
            return upstreamError("探测页面返回 HTTP " + page.statusCode(), startedAt);
        }
        String parameter = metaContent(page.body(), "csrf-param");
        String token = metaContent(page.body(), "csrf-token");
        if (!parameter.matches("[A-Za-z_][A-Za-z0-9_-]{0,63}")
                || token.isEmpty() || token.length() > 4096 || !token.matches("[A-Za-z0-9_+/=-]+")) {
            return upstreamError("探测页面缺少有效的 CSRF 令牌", startedAt);
        }
        // 使用固定的同源接口，不从页面脚本提取凭据的提交地址，也不跟随 POST 重定向。
        URI probeUri = pageUri.resolve("/checkc/peapmschap");
        String form = "username=" + encode(identity) + "&passwd=" + encode(password)
                + "&" + encode(parameter) + "=" + encode(token);
        HttpResponse<String> response = send(client, request(probeUri, settings)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json")
                .header("Referer", pageUri.toString())
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build());
        return EduroamResponseParser.parsePku(response.statusCode(), response.body(), elapsed(startedAt));
    }

    private static boolean isPkuDetection(URI uri) {
        return "/checkc/pkudetection".equals(uri.getPath())
                || "/checkc/pkudetection/".equals(uri.getPath());
    }

    private static HttpRequest.Builder request(URI uri, EduroamSettings settings) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds()))
                .header("User-Agent", USER_AGENT);
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String metaContent(String html, String name) {
        Matcher tags = META.matcher(html == null ? "" : html);
        while (tags.find()) {
            String metaName = "";
            String content = "";
            Matcher attributes = ATTRIBUTE.matcher(tags.group());
            while (attributes.find()) {
                if ("name".equalsIgnoreCase(attributes.group(1))) {
                    metaName = attributes.group(3);
                } else if ("content".equalsIgnoreCase(attributes.group(1))) {
                    content = attributes.group(3);
                }
            }
            if (name.equalsIgnoreCase(metaName)) {
                return content;
            }
        }
        return "";
    }

    private static EduroamProbeOutcome upstreamError(String detail, long startedAt) {
        return EduroamProbeOutcome.failed(EduroamFailureReason.UPSTREAM_ERROR, detail, elapsed(startedAt));
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
