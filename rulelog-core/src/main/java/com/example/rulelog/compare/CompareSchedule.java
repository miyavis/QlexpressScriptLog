package com.example.rulelog.compare;

/**
 * 比对定时任务：按固定间隔自动执行一条比对规则。
 * 面向业务人员用"每 N 分钟"配置，无需写 cron 表达式。
 */
public class CompareSchedule {

    private String id;
    private String ruleId;
    private String name;
    private boolean enabled = true;
    /** 执行间隔（分钟）：5 / 15 / 60 / 1440 */
    private int intervalMinutes = 60;
    /** 运行时上下文快照（JSON 字符串） */
    private String contextJson = "{}";
    private long lastRunAt;
    private long nextRunAt;
    private long createdAt;

    public CompareSchedule() {
    }

    // ---------- getters / setters ----------
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getIntervalMinutes() { return intervalMinutes; }
    public void setIntervalMinutes(int intervalMinutes) { this.intervalMinutes = intervalMinutes; }
    public String getContextJson() { return contextJson; }
    public void setContextJson(String contextJson) { this.contextJson = contextJson; }
    public long getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(long lastRunAt) { this.lastRunAt = lastRunAt; }
    public long getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(long nextRunAt) { this.nextRunAt = nextRunAt; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
