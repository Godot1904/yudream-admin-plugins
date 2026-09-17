package online.yudream.base.plugin.tarusso.domain.service;

import online.yudream.base.plugin.tarusso.domain.aggregate.SsoSettings;
import online.yudream.base.plugin.tarusso.domain.enumerate.SsoProtocol;

import java.util.Map;

public interface SsoProtocolClient {

    SsoProtocol protocol();

    String authorizationUrl(SsoSettings settings, String state);

    ExternalIdentity exchange(SsoSettings settings, String ticket, String state, String clientSecret);

    ConnectivityResult probe(SsoSettings settings, String clientSecret);

    /**
     * 兜底中转：IdP 回跳到插件中转端点（没有 state）时，把请求换成宿主回调地址。
     *
     * @param providerCode 第三方登录通道标识（宿主按它匹配扩展点）
     * @param platformType 平台类型
     * @param ticket       IdP 追加在 service 上的票据
     * @return 可直接 302 的宿主回调地址；不支持兜底（或找不到待回调记录）时返回空
     */
    default java.util.Optional<String> relayForwardUrl(SsoSettings settings, String providerCode,
                                                       String platformType, String ticket) {
        return java.util.Optional.empty();
    }

    record ExternalIdentity(
            String socialUid,
            String nickname,
            String avatarUrl,
            String gender,
            String location,
            Map<String, String> attributes
    ) {
        public ExternalIdentity {
            if (socialUid == null || socialUid.isBlank()) {
                throw new IllegalArgumentException("第三方登录未返回有效账号标识");
            }
            if (attributes == null) {
                attributes = Map.of();
            }
        }
    }

    record ConnectivityResult(boolean ok, String message) {
        public static ConnectivityResult ok(String message) {
            return new ConnectivityResult(true, message);
        }

        public static ConnectivityResult fail(String message) {
            return new ConnectivityResult(false, message);
        }
    }
}
