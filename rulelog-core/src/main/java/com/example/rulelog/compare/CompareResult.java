package com.example.rulelog.compare;

import java.util.ArrayList;
import java.util.List;

/** 比对结果：行列表 + 汇总统计 */
public class CompareResult {

    private final List<CompareRow> rows = new ArrayList<>();
    private int totalRows;
    private int consistentRows;
    private int inconsistentRows;
    private int totalCells;
    private int inconsistentCells;
    private long elapsedMillis;
    /** 取数脚本产生的执行 traceId（供平台下钻执行日志） */
    private final java.util.List<String> traceIds = new java.util.ArrayList<>();
    /** 脚本 ID → traceId（供前端生成深链） */
    private final java.util.Map<String, String> scriptTraceIds = new java.util.LinkedHashMap<>();

    public List<CompareRow> getRows() { return rows; }
    public void addRow(CompareRow row) { rows.add(row); }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getConsistentRows() { return consistentRows; }
    public void setConsistentRows(int consistentRows) { this.consistentRows = consistentRows; }
    public int getInconsistentRows() { return inconsistentRows; }
    public void setInconsistentRows(int inconsistentRows) { this.inconsistentRows = inconsistentRows; }
    public int getTotalCells() { return totalCells; }
    public void setTotalCells(int totalCells) { this.totalCells = totalCells; }
    public int getInconsistentCells() { return inconsistentCells; }
    public void setInconsistentCells(int inconsistentCells) { this.inconsistentCells = inconsistentCells; }
    public long getElapsedMillis() { return elapsedMillis; }
    public void setElapsedMillis(long elapsedMillis) { this.elapsedMillis = elapsedMillis; }
    public java.util.List<String> getTraceIds() { return traceIds; }
    public java.util.Map<String, String> getScriptTraceIds() { return scriptTraceIds; }

    /** 汇总统计（遍历行计算） */
    public void summarize() {
        totalRows = rows.size();
        consistentRows = 0;
        inconsistentRows = 0;
        totalCells = 0;
        inconsistentCells = 0;
        for (CompareRow r : rows) {
            if (r.isConsistent()) consistentRows++; else inconsistentRows++;
            for (CompareRow.CellResult c : r.getCells().values()) {
                totalCells++;
                if (!c.isConsistent()) inconsistentCells++;
            }
        }
    }
}
