package online.yudream.base.plugin.eduroam.infrastructure.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 请求体解析。未知字段忽略，便于前端与后端不完全同步时向前兼容。 */
public final class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonSupport() {
    }

    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json == null || json.isBlank() ? "{}" : json, type);
        } catch (JsonProcessingException e) {
            // 解析异常可能包含原始请求片段，不能把校园密码或本站密码放进响应和日志。
            throw new IllegalArgumentException("请求 JSON 格式不正确");
        }
    }
}
