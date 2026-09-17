package online.yudream.base.plugin.tarusso.infrastructure.cas;

import online.yudream.base.plugin.tarusso.domain.aggregate.RelayTicket;
import online.yudream.base.plugin.tarusso.domain.aggregate.SsoSettings;
import online.yudream.base.plugin.tarusso.domain.enumerate.SsoProtocol;
import online.yudream.base.plugin.tarusso.domain.repo.RelayTicketRepository;
import online.yudream.base.plugin.tarusso.domain.service.SsoProtocolClient;
import online.yudream.base.plugin.tarusso.infrastructure.http.HttpClientSupport;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * CAS 协议客户端：授权跳转 + /p3/serviceValidate 票据校验。
 *
 * <p>state 有两种承载方式（{@link SsoSettings#casStateMode()}）：
 * <ul>
 *   <li>{@code query}（默认）：把宿主 state 追加在 service 查询串上，CAS 回跳时原样带回；</li>
 *   <li>{@code relay}（兜底）：部分 CAS 部署不接受 service 里带查询串，此时 service 用插件的固定中转地址
 *       （{@code /api/plugins/cas/public/relay}，不含任何查询串），发起登录时把宿主 state 存成待回调记录，
 *       CAS 追加 ticket 回跳到中转端点后，由插件按「最近一次记录」把 state 补回宿主回调。
 *       service 在两次调用中都是同一个固定串，因此票据校验仍然成立。</li>
 * </ul>
 */
public final class CasProtocolClient implements SsoProtocolClient {

    private final RelayTicketRepository relayTickets;

    public CasProtocolClient(RelayTicketRepository relayTickets) {
        this.relayTickets = relayTickets;
    }

    @Override
    public SsoProtocol protocol() {
        return SsoProtocol.CAS;
    }

    @Override
    public String authorizationUrl(SsoSettings settings, String state) {
        String service = settings.relayStateMode()
                ? relayService(settings, state)
                : settings.casServiceUrl(state);
        return settings.loginUrl() + "?service=" + encode(service);
    }

    @Override
    public ExternalIdentity exchange(SsoSettings settings, String ticket, String state, String clientSecret) {
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalArgumentException("CAS 回调缺少 ticket");
        }
        // 兜底模式下 CAS 看到的是固定中转地址（没有 state），校验必须用同一个串，否则 service 不匹配。
        String service = settings.relayStateMode()
                ? requireRelayService(settings)
                : settings.casServiceUrl(state);
        String url = settings.validateUrl() + "?service=" + encode(service) + "&ticket=" + encode(ticket);
        HttpClientSupport.HttpResponse response = HttpClientSupport.get(url, null);
        if (!response.ok()) {
            throw new IllegalStateException("CAS 票据校验失败（HTTP " + response.status() + "）");
        }
        CasServiceResponseParser.CasIdentity identity = CasServiceResponseParser.parse(response.body());
        return new ExternalIdentity(identity.user(), identity.displayName(), identity.avatarUrl(), "", "", identity.attributes());
    }

    /**
     * 兜底中转：取该平台类型下最近一次待回调记录（一次性消费），换成宿主回调地址。
     * 找不到记录时返回空，由调用方给出可诊断的提示。
     */
    @Override
    public Optional<String> relayForwardUrl(SsoSettings settings, String providerCode,
                                            String platformType, String ticket) {
        if (!settings.relayStateMode()) {
            return Optional.empty();
        }
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        relayTickets.purgeExpired(now);
        Optional<RelayTicket> pending = relayTickets.latest(platformType, now);
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        RelayTicket matched = pending.get();
        relayTickets.consume(matched.hostState());
        String target = settings.relayForwardUrl(providerCode, platformType, matched.hostState(), ticket);
        return target.isBlank() ? Optional.empty() : Optional.of(target);
    }

    @Override
    public ConnectivityResult probe(SsoSettings settings, String clientSecret) {
        if (settings.callbackUrl().isBlank()) {
            return ConnectivityResult.fail("请先填写本站回调地址");
        }
        if (settings.relayStateMode() && !settings.relayReady()) {
            return ConnectivityResult.fail("兜底模式要求回调地址包含 " + SsoSettings.EXTERNAL_LOGIN_PREFIX
                    + "（用于反推站点基址与中转地址）");
        }
        try {
            HttpClientSupport.HttpResponse login = HttpClientSupport.get(settings.loginUrl(), null);
            if (login.status() >= 500) {
                return ConnectivityResult.fail("登录页不可达（HTTP " + login.status() + "）");
            }
            String service = settings.relayStateMode() ? settings.relayServiceUrl() : settings.callbackUrl();
            HttpClientSupport.HttpResponse validate = HttpClientSupport.get(
                    settings.validateUrl() + "?service=" + encode(service) + "&ticket=ST-probe",
                    null
            );
            if (validate.status() >= 500) {
                return ConnectivityResult.fail("票据校验端点不可达（HTTP " + validate.status() + "）");
            }
            if (validate.body() != null && validate.body().contains("authenticationFailure")) {
                return ConnectivityResult.ok("CAS 登录页与 /cas/p3/serviceValidate 可达；探测票据按预期被拒绝"
                        + (settings.relayStateMode() ? "（当前为兜底模式，service=" + service + "）" : ""));
            }
            return ConnectivityResult.ok("CAS 登录页可达（HTTP " + login.status() + "），校验端点 HTTP " + validate.status());
        } catch (RuntimeException e) {
            return ConnectivityResult.fail(e.getMessage());
        }
    }

    /** 记录一次待回调并返回固定中转地址。 */
    private String relayService(SsoSettings settings, String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("授权请求缺少 state");
        }
        String relay = requireRelayService(settings);
        relayTickets.save(RelayTicket.issue(state, protocol().typeCode(), System.currentTimeMillis()));
        return relay;
    }

    private String requireRelayService(SsoSettings settings) {
        String relay = settings.relayServiceUrl();
        if (relay.isBlank()) {
            throw new IllegalStateException("兜底模式要求回调地址包含 " + SsoSettings.EXTERNAL_LOGIN_PREFIX
                    + "（当前回调地址无法反推站点基址：" + settings.callbackUrl() + "）");
        }
        return relay;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
