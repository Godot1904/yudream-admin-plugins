package online.yudream.base.plugin.tarusso.infrastructure.cas;

import online.yudream.base.plugin.tarusso.domain.aggregate.SsoSettings;
import online.yudream.base.plugin.tarusso.domain.enumerate.SsoProtocol;
import online.yudream.base.plugin.tarusso.domain.service.SsoProtocolClient;
import online.yudream.base.plugin.tarusso.infrastructure.http.HttpClientSupport;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class CasProtocolClient implements SsoProtocolClient {

    @Override
    public SsoProtocol protocol() {
        return SsoProtocol.CAS;
    }

    @Override
    public String authorizationUrl(SsoSettings settings, String state) {
        String service = settings.casServiceUrl(state);
        return settings.loginUrl() + "?service=" + encode(service);
    }

    @Override
    public ExternalIdentity exchange(SsoSettings settings, String ticket, String state, String clientSecret) {
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalArgumentException("CAS 回调缺少 ticket");
        }
        String service = settings.casServiceUrl(state);
        String url = settings.validateUrl() + "?service=" + encode(service) + "&ticket=" + encode(ticket);
        HttpClientSupport.HttpResponse response = HttpClientSupport.get(url, null);
        if (!response.ok()) {
            throw new IllegalStateException("CAS 票据校验失败（HTTP " + response.status() + "）");
        }
        CasServiceResponseParser.CasIdentity identity = CasServiceResponseParser.parse(response.body());
        return new ExternalIdentity(identity.user(), identity.displayName(), identity.avatarUrl(), "", "", identity.attributes());
    }

    @Override
    public ConnectivityResult probe(SsoSettings settings, String clientSecret) {
        if (settings.callbackUrl().isBlank()) {
            return ConnectivityResult.fail("请先填写本站回调地址");
        }
        try {
            HttpClientSupport.HttpResponse login = HttpClientSupport.get(settings.loginUrl(), null);
            if (login.status() >= 500) {
                return ConnectivityResult.fail("登录页不可达（HTTP " + login.status() + "）");
            }
            HttpClientSupport.HttpResponse validate = HttpClientSupport.get(
                    settings.validateUrl() + "?service=" + encode(settings.callbackUrl()) + "&ticket=ST-probe",
                    null
            );
            if (validate.status() >= 500) {
                return ConnectivityResult.fail("票据校验端点不可达（HTTP " + validate.status() + "）");
            }
            if (validate.body() != null && validate.body().contains("authenticationFailure")) {
                return ConnectivityResult.ok("CAS 登录页与 /cas/p3/serviceValidate 可达；探测票据按预期被拒绝");
            }
            return ConnectivityResult.ok("CAS 登录页可达（HTTP " + login.status() + "），校验端点 HTTP " + validate.status());
        } catch (RuntimeException e) {
            return ConnectivityResult.fail(e.getMessage());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
