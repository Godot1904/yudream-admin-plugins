package online.yudream.base.plugin.eduroam.infrastructure.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import online.yudream.base.plugin.eduroam.domain.valobj.EduroamProbeOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class HttpEduroamProbeTest {

    private HttpServer server;
    private String origin;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private int pageStatus = 200;
    private String pageBody;
    private int responseStatus = 200;
    private String responseBody = "{\"logtitle\":[\"SUCCESS\"],\"testinfo\":[\"password=secret\"]}";
    private int sessions;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/checkc/pkudetection", exchange -> {
            capture(exchange);
            int session = ++sessions;
            exchange.getResponseHeaders().add("Set-Cookie", "probe_session=" + session + "; Path=/; HttpOnly");
            String html = pageBody == null ? "<meta name=\"csrf-param\" content=\"_csrf-f\">"
                    + "<meta content='token-" + session + "+/=' name='csrf-token'>" : pageBody;
            respond(exchange, pageStatus, html);
        });
        server.createContext("/checkc/peapmschap", exchange -> {
            capture(exchange);
            if (responseStatus == 307) {
                exchange.getResponseHeaders().add("Location", origin + "/redirect-target");
            }
            respond(exchange, responseStatus, responseBody);
        });
        server.createContext("/redirect-target", exchange -> {
            capture(exchange);
            respond(exchange, 200, responseBody);
        });
        server.createContext("/legacy", exchange -> {
            capture(exchange);
            respond(exchange, 200, "<pre>EAP authentication completed successfully</pre>");
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesCsrfAndCookieBeforePostingEncodedCredentials() {
        String identity = "学号+test@example.edu.cn";
        String password = "p+&= 空格?#%";
        EduroamProbeOutcome result = new HttpEduroamProbe().probe(identity, password, settings("/checkc/pkudetection"));
        assertTrue(result.success(), result.toString());
        assertEquals(2, requests.size());
        assertEquals("GET", requests.get(0).method());
        assertEquals("", requests.get(0).body());
        Request post = requests.get(1);
        assertEquals("POST", post.method());
        assertEquals("/checkc/peapmschap", post.path());
        assertEquals(Map.of("username", identity, "passwd", password, "_csrf-f", "token-1+/="), form(post.body()));
        assertTrue(post.cookie().contains("probe_session=1"));
        assertEquals(origin + "/checkc/pkudetection", post.referer());
        assertFalse(result.detail().contains("secret"));
    }

    @Test
    void usesIndependentSessionsAndSupportsTrailingSlash() {
        HttpEduroamProbe probe = new HttpEduroamProbe();
        assertTrue(probe.probe("one@example.edu.cn", "one", settings("/checkc/pkudetection/")).success());
        assertTrue(probe.probe("two@example.edu.cn", "two", settings("/checkc/pkudetection/")).success());
        assertEquals(4, requests.size());
        assertNull(requests.get(0).cookie());
        assertNull(requests.get(2).cookie());
        assertTrue(requests.get(1).cookie().contains("probe_session=1"));
        assertTrue(requests.get(3).cookie().contains("probe_session=2"));
        assertEquals("token-2+/=", form(requests.get(3).body()).get("_csrf-f"));
    }

    @Test
    void doesNotSubmitCredentialsWhenCsrfIsMissing() {
        pageBody = "<html>service unavailable</html>";
        EduroamProbeOutcome result = new HttpEduroamProbe().probe("user", "secret", settings("/checkc/pkudetection"));
        assertFalse(result.success());
        assertEquals("UPSTREAM_ERROR", result.reasonCode());
        assertEquals(1, requests.size());
    }

    @Test
    void doesNotSubmitCredentialsWhenPageFails() {
        pageStatus = 503;
        EduroamProbeOutcome result = new HttpEduroamProbe().probe("user", "secret", settings("/checkc/pkudetection"));
        assertFalse(result.success());
        assertEquals("UPSTREAM_ERROR", result.reasonCode());
        assertEquals(1, requests.size());
    }

    @Test
    void rejectsRedirectsWithoutForwardingCredentials() {
        responseStatus = 307;
        EduroamProbeOutcome result = new HttpEduroamProbe().probe("user", "secret", settings("/checkc/pkudetection"));
        assertFalse(result.success());
        assertEquals("UPSTREAM_ERROR", result.reasonCode());
        assertEquals(2, requests.size());
    }

    @Test
    void rejectsHtmlAndHttpErrorsFromProbe() {
        responseBody = "<pre>EAP authentication completed successfully</pre>";
        assertFalse(new HttpEduroamProbe().probe("user", "secret", settings("/checkc/pkudetection")).success());
        responseStatus = 403;
        responseBody = "{\"logtitle\":\"SUCCESS\"}";
        assertEquals("UPSTREAM_ERROR", new HttpEduroamProbe()
                .probe("user", "secret", settings("/checkc/pkudetection")).reasonCode());
    }

    @Test
    void keepsLegacyCustomFormProtocol() {
        EduroamProbeOutcome result = new HttpEduroamProbe().probe("test@example.edu.cn", "a+& b", settings("/legacy"));
        assertTrue(result.success());
        assertEquals(1, requests.size());
        assertEquals("POST", requests.get(0).method());
        assertEquals(Map.of("login", "test@example.edu.cn", "password", "a+& b"), form(requests.get(0).body()));
    }

    private EduroamSettings settings(String path) {
        return EduroamSettings.from(Map.of("verifyEndpoint", origin + path));
    }

    private void capture(HttpExchange exchange) throws IOException {
        requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("Cookie"), exchange.getRequestHeaders().getFirst("Referer"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static Map<String, String> form(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String field : body.split("&")) {
            String[] parts = field.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }

    private record Request(String method, String path, String cookie, String referer, String body) {
    }
}
