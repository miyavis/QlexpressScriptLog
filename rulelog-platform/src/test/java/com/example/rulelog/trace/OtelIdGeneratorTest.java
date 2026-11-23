package com.example.rulelog.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OtelIdGeneratorTest {

    private boolean isHex(String s) {
        return s.matches("[0-9a-f]+");
    }

    @Test
    void traceIdAlignsWithPlatformTraceId() {
        OtelIdGenerator gen = new OtelIdGenerator();
        gen.setPlatformTraceId("A1B2C3D4E5F60718");
        String traceId = gen.generateTraceId();
        assertEquals("a1b2c3d4e5f607180000000000000000", traceId);
        assertEquals(32, traceId.length());
        assertTrue(isHex(traceId));
    }

    @Test
    void spanIdIsRandom16Hex() {
        OtelIdGenerator gen = new OtelIdGenerator();
        String s1 = gen.generateSpanId();
        String s2 = gen.generateSpanId();
        assertEquals(16, s1.length());
        assertTrue(isHex(s1));
        assertNotEquals(s1, s2, "spanId 应随机");
    }

    @Test
    void noPlatformIdGeneratesRandomTraceId() {
        OtelIdGenerator gen = new OtelIdGenerator();
        String t = gen.generateTraceId();
        assertEquals(32, t.length());
        assertTrue(isHex(t));
    }
}
