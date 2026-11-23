package com.example.rulelog.trace;

import java.security.SecureRandom;

/**
 * W3C Trace Context 轻量适配（不引入 OpenTelemetry SDK）：
 * 把平台内部的 16 位 hex traceId 映射为标准 traceparent 头，
 * 让执行记录能与 OTel / SkyWalking 等生态对接。
 *
 * 格式：{@code 00-<32 hex trace-id>-<16 hex span-id>-01}
 * - trace-id：平台 traceId 补零到 32 位（小写 hex）
 * - span-id：每次调用生成 16 位随机 hex（代表本次执行根 span）
 * - flags：01（sampled）
 */
public final class TraceParent {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceParent() {
    }

    /** 由平台 traceId 生成 W3C traceparent；输入为空返回 null */
    public static String fromPlatformTraceId(String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            return null;
        }
        String normalized = traceId.toLowerCase();
        String traceId32 = pad32(normalized);
        String spanId16 = randomHex(16);
        return "00-" + traceId32 + "-" + spanId16 + "-01";
    }

    /** 补零/截取到 32 位；非 hex 字符替换为 0，保证格式合法 */
    private static String pad32(String s) {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            char c = i < s.length() ? s.charAt(i) : '0';
            sb.append(isHex(c) ? c : '0');
        }
        return sb.toString();
    }

    private static String randomHex(int length) {
        byte[] bytes = new byte[length / 2];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(length);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
