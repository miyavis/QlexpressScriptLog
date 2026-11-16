package com.example.rulelog.store;

import com.example.rulelog.model.RuleExecutionRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行记录内存库（Demo 简化：重启即清空）。
 * LinkedHashMap 维护插入顺序，供"按时间倒序的执行记录列表"使用。
 */
@Component
public class ExecutionRecordStore {

    private final int maxRecords;

    private final java.util.LinkedHashMap<String, RuleExecutionRecord> records =
            new java.util.LinkedHashMap<>();

    public ExecutionRecordStore(@Value("${app.max-execution-records:200}") int maxRecords) {
        this.maxRecords = maxRecords;
    }

    public void save(RuleExecutionRecord record) {
        synchronized (records) {
            records.put(record.getTraceId(), record);
            // 超限淘汰最早的记录
            while (records.size() > maxRecords) {
                String oldestKey = records.keySet().iterator().next();
                records.remove(oldestKey);
            }
        }
    }

    /** 最近优先的列表 */
    public List<RuleExecutionRecord> listRecent(int limit) {
        synchronized (records) {
            List<RuleExecutionRecord> all =
                    new ArrayList<>(records.values());
            List<RuleExecutionRecord> result = new ArrayList<>();
            for (int i = all.size() - 1; i >= 0 && result.size() < limit; i--) {
                result.add(all.get(i));
            }
            return result;
        }
    }

    public RuleExecutionRecord get(String traceId) {
        synchronized (records) {
            return records.get(traceId);
        }
    }

    /** 查询某脚本的所有执行记录（最近优先） */
    public List<RuleExecutionRecord> listByScript(String scriptId, int limit) {
        synchronized (records) {
            List<RuleExecutionRecord> result = new ArrayList<>();
            for (int i = records.size() - 1; i >= 0 && result.size() < limit; i--) {
                RuleExecutionRecord r = new ArrayList<>(records.values()).get(i);
                if (scriptId.equals(r.getScriptId())) {
                    result.add(r);
                }
            }
            return result;
        }
    }
}
