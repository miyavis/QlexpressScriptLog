package com.example.rulelog.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次 Service 方法调用（由 ServiceCallAspect 自动记录）。
 * children 支持嵌套（Service A 内部调用 Service B 时形成父子关系）。
 */
public class ServiceCallLog {

    /** 所属执行 traceId */
    private String traceId;

    /** Service 简单名，如 "UserService" */
    private String serviceName;

    /** 脚本中使用的中文函数名，如 "查询用户等级"（优先显示，与脚本定义一致） */
    private String chineseName;

    /** 该调用所在的脚本行号（从1开始，用于左右联动高亮） */
    private Integer lineNo;

    /** 方法名 */
    private String methodName;

    /** 入参 JSON（截断保存） */
    private String argsJson;

    /** 出参 JSON（截断保存） */
    private String resultJson;

    /** 失败时的异常摘要 */
    private String errorMessage;

    private boolean success = true;

    private long startTime;

    private long endTime;

    private long durationMs;

    /** 方法内部业务步骤（手动埋点） */
    private List<StepLog> internalSteps = new ArrayList<>();

    /** 嵌套的服务调用 */
    private List<ServiceCallLog> children = new ArrayList<>();

    public ServiceCallLog() {
    }

    public ServiceCallLog(String traceId, String serviceName, String methodName, String argsJson, long startTime) {
        this.traceId = traceId;
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.argsJson = argsJson;
        this.startTime = startTime;
    }

    public ServiceCallLog(String traceId, String serviceName, String chineseName, String methodName, String argsJson, long startTime) {
        this.traceId = traceId;
        this.serviceName = serviceName;
        this.chineseName = chineseName;
        this.methodName = methodName;
        this.argsJson = argsJson;
        this.startTime = startTime;
    }

    public void addChild(ServiceCallLog child) {
        this.children.add(child);
    }

    /** 正常结束 */
    public void finish(Object result) {
        this.success = true;
        this.endTime = System.currentTimeMillis();
        this.durationMs = Math.max(0, endTime - startTime);
        this.resultJson = com.example.rulelog.util.JsonUtils.toJsonTruncated(result, 800);
    }

    /** 异常结束 */
    public void finishError(Throwable t) {
        this.success = false;
        this.endTime = System.currentTimeMillis();
        this.durationMs = Math.max(0, endTime - startTime);
        this.errorMessage = t.toString();
        this.resultJson = null;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public void setChineseName(String chineseName) {
        this.chineseName = chineseName;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    /** 显示名称：优先返回中文函数名，没有则返回 "Service.method" */
    public String getDisplayName() {
        if (chineseName != null && !chineseName.isEmpty()) {
            return chineseName;
        }
        return serviceName + "." + methodName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getArgsJson() {
        return argsJson;
    }

    public void setArgsJson(String argsJson) {
        this.argsJson = argsJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
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

    public List<StepLog> getInternalSteps() {
        return internalSteps;
    }

    public void setInternalSteps(List<StepLog> internalSteps) {
        this.internalSteps = internalSteps;
    }

    public List<ServiceCallLog> getChildren() {
        return children;
    }

    public void setChildren(List<ServiceCallLog> children) {
        this.children = children;
    }
}
