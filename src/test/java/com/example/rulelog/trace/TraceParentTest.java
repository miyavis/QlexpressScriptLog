package com.example.rulelog.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W3C traceparent 格式测试：把平台 16 位 traceId 映射为标准 traceparent，
 * 便于与 OpenTelemetry / SkyWalking 等生态对接（轻量版：不引入 SDK）。
 */
class TraceParentTest {

    @Test
    void generatesValidTraceparent() {
        // 平台的 16 位大写 hex traceId
        String traceId = "A1B2C3D4E5F60718";
        String tp = TraceParent.fromPlatformTraceId(traceId);
        // W3C 格式：00-<32 hex trace-id>-<16 hex span-id>-01
        assertTrue(tp.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"), "格式应为 W3C traceparent: " + tp);
        assertTrue(tp.contains("a1b2c3d4e5f60718"), "平台 traceId 应作为 trace-id 前缀");
    }

    @Test
    void nullTraceIdReturnsNull() {
        assertNull(TraceParent.fromPlatformTraceId(null));
        assertNull(TraceParent.fromPlatformTraceId(""));
    }

    @Test
    void nonHexTraceIdIsStillPaddedDeterministically() {
        // 平台 traceId 必为 hex，但防御性：非 hex 也不应抛异常
        String tp = TraceParent.fromPlatformTraceId("XYZ");
        assertNotNull(tp);
        assertTrue(tp.startsWith("00-"));
    }
}
