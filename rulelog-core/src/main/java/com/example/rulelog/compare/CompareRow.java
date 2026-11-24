package com.example.rulelog.compare;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 比对结果的一行：按 key 对齐后，各数据源的字段值 + 每个字段的比对结论。
 */
public class CompareRow {

    private String key;                                   // 关联 key 的值
    private Map<String, Map<String, Object>> sourceValues = new LinkedHashMap<>(); // 源key → 字段名→值
    private Map<String, CellResult> cells = new LinkedHashMap<>();  // 字段名 → 比对结论
    private boolean consistent = true;                    // 整行是否全一致

    public static class CellResult {
        private boolean consistent;
        private String detail;
        public CellResult() { }
        public CellResult(boolean consistent, String detail) {
            this.consistent = consistent;
            this.detail = detail;
        }
        public boolean isConsistent() { return consistent; }
        public void setConsistent(boolean consistent) { this.consistent = consistent; }
        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
    }

    // ---------- getters / setters ----------
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public Map<String, Map<String, Object>> getSourceValues() { return sourceValues; }
    public void setSourceValues(Map<String, Map<String, Object>> sourceValues) { this.sourceValues = sourceValues; }
    public Map<String, CellResult> getCells() { return cells; }
    public void setCells(Map<String, CellResult> cells) { this.cells = cells; }
    public boolean isConsistent() { return consistent; }
    public void setConsistent(boolean consistent) { this.consistent = consistent; }
}
