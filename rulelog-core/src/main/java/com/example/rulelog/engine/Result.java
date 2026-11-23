package com.example.rulelog.engine;

/**
 * 规则执行结果（SDK 对外极简返回）。
 * 保留 traceId 供下钻完整日志。
 */
public class Result {

    private final boolean success;
    private final Object value;        // 规则返回值
    private final String traceId;
    private final String error;

    public Result(boolean success, Object value, String traceId, String error) {
        this.success = success;
        this.value = value;
        this.traceId = traceId;
        this.error = error;
    }

    public static Result ok(Object value, String traceId) {
        return new Result(true, value, traceId, null);
    }

    public static Result fail(String error, String traceId) {
        return new Result(false, null, traceId, error);
    }

    public boolean isSuccess() { return success; }
    public Object getValue() { return value; }
    public String getTraceId() { return traceId; }
    public String getError() { return error; }
}
