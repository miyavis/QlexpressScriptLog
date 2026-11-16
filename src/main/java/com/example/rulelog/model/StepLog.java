package com.example.rulelog.model;

/**
 * Service 方法内部的一步业务动作（手动埋点：StepLogger.log）。
 * AOP 记不了同一个 Bean 内部的自调用，所以关键步骤由 Service 显式打点。
 */
public class StepLog {

    /** 所属执行 traceId */
    private String traceId;

    /** 该步骤挂在哪个 Service 调用下（null 表示脚本根层级） */
    private String serviceName;

    /** 步骤名，如"查询会员基础信息" */
    private String stepName;

    /** 步骤详情，业务可读文本 */
    private String detail;

    private long timestamp;

    public StepLog() {
    }

    public StepLog(String traceId, String serviceName, String stepName, String detail, long timestamp) {
        this.traceId = traceId;
        this.serviceName = serviceName;
        this.stepName = stepName;
        this.detail = detail;
        this.timestamp = timestamp;
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

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
