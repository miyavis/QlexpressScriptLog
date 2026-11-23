package com.example.rulelog.compare;

import java.util.Map;

/**
 * 一次比对执行的落盘记录（含上下文快照 + 结果摘要 + traceId 关联）。
 * 存 data/compare-records.json，供比对画布的历史回看。
 */
public class CompareRecord {

    private String id;
    private String ruleId;
    private String ruleName;
    private long savedAt;
    private Map<String, Object> context;
    private CompareResult result;
    private String summary;          // 人类可读摘要，如 "2 行 / 1 处不一致"

    public CompareRecord() {
    }

    // ---------- getters / setters ----------
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public long getSavedAt() { return savedAt; }
    public void setSavedAt(long savedAt) { this.savedAt = savedAt; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    public CompareResult getResult() { return result; }
    public void setResult(CompareResult result) { this.result = result; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
