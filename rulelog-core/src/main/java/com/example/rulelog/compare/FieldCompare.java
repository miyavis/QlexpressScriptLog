package com.example.rulelog.compare;

/**
 * 字段比对定义：比对数据集中的某个字段，用什么策略。
 *
 * strategy: equal / notEqual / contains / gt / gte / lt / lte / numericTolerance
 * tolerance: numericTolerance 策略的数值容差
 */
public class FieldCompare {

    private String field;            // 字段名（如 等级、积分、金额）
    private String label;            // 中文名
    private String strategy = "equal";
    private double tolerance = 0;    // 数值容差

    public FieldCompare() {
    }

    public FieldCompare(String field, String label, String strategy) {
        this.field = field;
        this.label = label;
        this.strategy = strategy;
    }

    // ---------- getters / setters ----------
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public double getTolerance() { return tolerance; }
    public void setTolerance(double tolerance) { this.tolerance = tolerance; }
}
