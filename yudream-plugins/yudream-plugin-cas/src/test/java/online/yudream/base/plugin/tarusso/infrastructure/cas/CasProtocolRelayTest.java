package online.yudream.base.plugin.tarusso.infrastructure.cas;

import com.sun.net.httpserver.HttpServer;
import online.yudream.base.plugin.tarusso.domain.aggregate.RelayTicket;
import online.yudream.base.plugin.tarusso.domain.aggregate.SsoSettings;
import online.yudream.base.plugin.tarusso.domain.enumerate.SsoProtocol;
import online.yudream.base.plugin.tarusso.domain.service.SsoProtocolClient;
import online.yudream.base.plugin.tarusso.infrastructure.MemoryDocumentStore;
import online.yudream.base.plugin.tarusso.infrastructure.repository.RelayTicketDocumentRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAS 兜底模式（state 不进 service 查询串）的行为验证：
 * 跳转用固定中转地址、票据校验用同一个串、中转按「最近一次」一次性消费。
 */
class CasProtocolRelayTest {

    private static final String CALLBACK = "https://hall.mc.taru.xj.cn/api/external-login/callback";

    @Test
    void authorizationUrlUsesFixedRelayServiceAndRecordsTheHostState() {
        MemoryDocumentStore documents = new MemoryDocumentStore();
        RelayTicketDocumentRepository tickets = new RelayTicketDocumentRepository(documents);
        CasProtocolClient client = new CasProtocolClient(tickets);
        SsoSettings settings = relaySettings("https://auth.example.edu.cn");

        String url = client.authorizationUrl(settings, "host-state-1");

        String expectedService = URLEncoder.encode(settings.relayServiceUrl(), StandardCharsets.UTF_8);
        assertEquals("https://auth.example.edu.cn/authserver/login?service=" + expectedService, url);
        assertFalse(URLDecoder.decode(expectedService, StandardCharsets.UTF_8).contains("?state="));

        Optional<RelayTicket> pending = tickets.latest("cas", System.currentTimeMillis());
        assertTrue(pending.isPresent());
        assertEquals("host-state-1", pending.get().hostState());
    }

    @Test
    void standardModeStillAppendsStateToTheCallbackUrl() {
        CasProtocolClient client = new CasProtocolClient(new RelayTicketDocumentRepository(new MemoryDocumentStore()));
        SsoSettings settings = settings(SsoSettings.STATE_MODE_QUERY);

        String url = client.authorizationUrl(settings, "host-state-1");

        String expected = URLEncoder.encode(CALLBACK + "?state=host-state-1", StandardCharsets.UTF_8);
        assertEquals("https://auth.example.edu.cn/authserver/login?service=" + expected, url);
    }

    @Test
    void relayForwardConsumesTheLatestAttemptExactlyOnce() {
        MemoryDocumentStore documents = new MemoryDocumentStore();
        RelayTicketDocumentRepository tickets = new RelayTicketDocumentRepository(documents);
        CasProtocolClient client = new CasProtocolClient(tickets);
        SsoSettings settings = relaySettings("https://auth.example.edu.cn");

        long now = System.currentTimeMillis();
        tickets.save(new RelayTicket("older-state", "cas", now - 5_000, now + 60_000));
        tickets.save(new RelayTicket("newer-state", "cas", now, now + 60_000));
        tickets.save(new RelayTicket("expired-state", "cas", now - 600_000, now - 1));

        Optional<String> forward = client.relayForwardUrl(settings, "cas", "cas", "ST-42");
        assertTrue(forward.isPresent());
        assertEquals(CALLBACK + "?provider=cas&type=cas&state=newer-state&ticket=ST-42", forward.get());

        // 已消费 → 下一次只剩下 older-state（过期记录既不匹配也不返回）
        Optional<String> second = client.relayForwardUrl(settings, "cas", "cas", "ST-43");
        assertTrue(second.isPresent());
        assertTrue(second.get().contains("state=older-state"));
        assertTrue(client.relayForwardUrl(settings, "cas", "cas", "ST-44").isEmpty());
    }

    @Test
    void relayForwardIsRejectedWhenNotInRelayMode() {
        CasProtocolClient client = new CasProtocolClient(new RelayTicketDocumentRepository(new MemoryDocumentStore()));
        assertTrue(client.relayForwardUrl(settings(SsoSettings.STATE_MODE_QUERY), "cas", "cas", "ST-1").isEmpty());
    }

    @Test
    void exchangeValidatesAgainstTheSameFixedRelayService() throws IOException {
        AtomicReference<String> serviceSeen = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        HttpServer cas = fakeCas(serviceSeen, calls);
        try {
            CasProtocolClient client = new CasProtocolClient(new RelayTicketDocumentRepository(new MemoryDocumentStore()));
            SsoSettings settings = relaySettings("http://127.0.0.1:" + cas.getAddress().getPort());

            SsoProtocolClient.ExternalIdentity identity = client.exchange(settings, "ST-1", "host-state-1", null);

            assertEquals(settings.relayServiceUrl(), serviceSeen.get());
            assertEquals("20230101", identity.socialUid());
            assertEquals("张三", identity.nickname());
            assertEquals("张三", identity.attributes().get("cn"));
            assertEquals(1, calls.get());
        } finally {
            cas.stop(0);
        }
    }

    private static HttpServer fakeCas(AtomicReference<String> serviceSeen, AtomicInteger calls) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cas/p3/serviceValidate", exchange -> {
            calls.incrementAndGet();
            serviceSeen.set(query(exchange.getRequestURI().getRawQuery()).get("service"));
            byte[] body = ("""
                    <cas:serviceResponse xmlns:cas='http://www.yale.edu/tp/cas'>
                      <cas:authenticationSuccess>
                        <cas:user>20230101</cas:user>
                        <cas:attributes><cas:cn>张三</cas:cn></cas:attributes>
                      </cas:authenticationSuccess>
                    </cas:serviceResponse>
                    """).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return server;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            values.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static SsoSettings relaySettings(String casBaseUrl) {
        return settings(casBaseUrl, SsoSettings.STATE_MODE_RELAY);
    }

    private static SsoSettings settings(String stateMode) {
        return settings("https://auth.example.edu.cn", stateMode);
    }

    private static SsoSettings settings(String casBaseUrl, String stateMode) {
        return new SsoSettings(
                true, SsoProtocol.CAS, SsoSettings.DEFAULT_DISPLAY_NAME, SsoSettings.DEFAULT_ICON,
                casBaseUrl, SsoSettings.DEFAULT_LOGIN_PATH, SsoSettings.DEFAULT_VALIDATE_PATH,
                SsoSettings.DEFAULT_OIDC_ISSUER, SsoSettings.DEFAULT_OIDC_AUTHORIZE_PATH, SsoSettings.DEFAULT_OIDC_TOKEN_PATH,
                SsoSettings.DEFAULT_OIDC_USERINFO_PATH, SsoSettings.DEFAULT_OIDC_JWKS_PATH, SsoSettings.DEFAULT_OIDC_REGISTER_PATH,
                "", false, SsoSettings.DEFAULT_SCOPES, CALLBACK, stateMode
        );
    }
}
