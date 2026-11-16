package com.example.rulelog.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * 全局 Jackson 工具：序列化统一走这里的 ObjectMapper，
 * 便于控制截断长度与格式，避免各处散落 new ObjectMapper。
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private JsonUtils() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** 序列化并按最大长度截断（用于日志字段），失败时降级为 toString */
    public static String toJsonTruncated(Object obj, int maxLength) {
        if (obj == null) {
            return "null";
        }
        try {
            String json = MAPPER.writeValueAsString(obj);
            if (maxLength > 0 && json.length() > maxLength) {
                json = json.substring(0, maxLength) + "...(已截断,总长" + json.length() + ")";
            }
            return json;
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    public static String pretty(Object obj) {
        try {
            return obj == null ? "null" : MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
