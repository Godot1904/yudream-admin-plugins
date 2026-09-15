package online.yudream.base.plugin.eduroam.infrastructure.support;

import java.util.Map;

/** 文档字段读取：文档存储里数字可能是字符串，缺失时回落到默认值。 */
public final class DocValues {

    private DocValues() {
    }

    public static String string(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static long number(Map<String, Object> document, String key, long defaultValue) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static int integer(Map<String, Object> document, String key, int defaultValue) {
        return (int) number(document, key, defaultValue);
    }

    public static boolean bool(Map<String, Object> document, String key, boolean defaultValue) {
        Object value = document.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }
}
