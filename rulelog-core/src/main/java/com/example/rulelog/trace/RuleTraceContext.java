package com.example.rulelog.trace;

/**
 * 当前执行的 traceId（ThreadLocal）。
 *
 * 一次脚本执行 = 一个 traceId：
 *   脚本入口生成 -> 写入这里 -> AOP / 手动埋点全部自动携带 -> finally 清理。
 *
 * 同一个 Service 被 A 脚本、B 脚本先后调用，各自 traceId 不同，
 * 日志不会串号。
 */
public final class RuleTraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private RuleTraceContext() {
    }

    public static void set(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String get() {
        return TRACE_ID.get();
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
