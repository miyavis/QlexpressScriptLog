package com.example.rulelog.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次脚本执行的完整记录。
 *
 * traceId 标识"这一次执行"：脚本入口生成，贯穿 AOP 与手动埋点，
 * 保证这条规则调用的所有 Spring Service 内部步骤都精确归属到本次执行。
 */
public class RuleExecutionRecord {

    private String traceId;

    /** W3C traceparent（由 traceId 映射，便于对接 OTel 生态） */
    private String traceparent;

    private String scriptId;

    private String scriptName;

    /** 执行时的脚本快照，防止后来脚本被改导致记录无法复原 */
    private String scriptSnapshot;

    /** 本次实际使用的参数（默认参数 + 请求覆盖后的合并快照） */
    private Map<String, Object> params = new LinkedHashMap<>();

    private boolean success;

    /** 脚本返回值 */
    private Object result;

    private String resultJson;

    /** 失败类型：语法错误 / 运行错误 / 系统错误 */
    private String errorType;

    private String errorMessage;

    private long startTime;

    private long endTime;

    private long durationMs;

    /** 脚本根层级（非 Service 内部）的步骤日志 */
    private List<StepLog> rootSteps = new ArrayList<>();

    /** 顶层 Service 调用（各自可带 children 嵌套） */
    private List<ServiceCallLog> calls = new ArrayList<>();

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTraceparent() {
        return traceparent;
    }

    public void setTraceparent(String traceparent) {
        this.traceparent = traceparent;
    }

    public String getScriptId() {
        return scriptId;
    }

    public void setScriptId(String scriptId) {
        this.scriptId = scriptId;
    }

    public String getScriptName() {
        return scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }

    public String getScriptSnapshot() {
        return scriptSnapshot;
    }

    public void setScriptSnapshot(String scriptSnapshot) {
        this.scriptSnapshot = scriptSnapshot;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params == null ? new LinkedHashMap<>() : params;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
        this.resultJson = com.example.rulelog.util.JsonUtils.toJsonTruncated(result, 2000);
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public List<StepLog> getRootSteps() {
        return rootSteps;
    }

    public void setRootSteps(List<StepLog> rootSteps) {
        this.rootSteps = rootSteps;
    }

    public List<ServiceCallLog> getCalls() {
        return calls;
    }

    public void setCalls(List<ServiceCallLog> calls) {
        this.calls = calls;
    }

    /** 按脚本行号分组的执行记录，用于前端“每一行脚本对应的日志”展示 */
    private java.util.Map<Integer, LineExecution> lineExecutions = new java.util.LinkedHashMap<>();

    public java.util.Map<Integer, LineExecution> getLineExecutions() {
        return lineExecutions;
    }

    public void setLineExecutions(java.util.Map<Integer, LineExecution> lineExecutions) {
        this.lineExecutions = lineExecutions;
    }
}
