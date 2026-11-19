package com.example.rulelog.trace;

import io.opentelemetry.sdk.trace.IdGenerator;

import java.security.SecureRandom;

/**
 * OTel ID 生成器：让 OTel 的 traceId 与平台 traceId 对齐（同一个执行在两套体系里 ID 一致）。
 *
 * 平台 traceId 是 16 位 hex，补零到 OTel 要求的 32 位；spanId 随机生成 16 位 hex。
 * 通过 ThreadLocal 传递当前执行的平台 traceId——与 RuleTraceContext 同源，
 * 由 OtelTraceService 在 startExecution 时设置、endExecution 时清理。
 */
public final class OtelIdGenerator implements IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final ThreadLocal<String> currentTraceId = new ThreadLocal<>();

    /** 设置当前执行的平台 traceId（16 位 hex） */
    public void setPlatformTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            currentTraceId.set(traceId);
        }
    }

    public void clear() {
        currentTraceId.remove();
    }

    @Override
    public String generateTraceId() {
        String plat = currentTraceId.get();
        if (plat == null || plat.isEmpty()) {
            return randomHex(32);
        }
        // 平台 traceId 补零/截取到 32 位；非 hex 字符替换为 0
        StringBuilder sb = new StringBuilder(32);
        String lower = plat.toLowerCase();
        for (int i = 0; i < 32; i++) {
            char c = i < lower.length() ? lower.charAt(i) : '0';
            sb.append(isHex(c) ? c : '0');
        }
        return sb.toString();
    }

    @Override
    public String generateSpanId() {
        return randomHex(16);
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
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }
}
