package com.example.rulelog.store;

import com.example.rulelog.model.RuleExecutionRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 执行记录库：运行时读写 H2 的 QL_EXECUTION 表。 */
@Component
public class ExecutionRecordStore {

    private static final String DDL = "CREATE TABLE IF NOT EXISTS QL_EXECUTION ("
            + "ID VARCHAR(64) PRIMARY KEY, NAME VARCHAR(128), RULE_ID VARCHAR(64), "
            + "SORT_TIME BIGINT, DATA CLOB)";

    private final H2JsonTable table;
    private final int maxRecords;

    @Autowired
    public ExecutionRecordStore(@Value("${app.max-execution-records:200}") int maxRecords, JdbcTemplate jdbc) {
        this.maxRecords = maxRecords;
        this.table = new H2JsonTable(jdbc, "QL_EXECUTION", DDL);
    }

    /** 测试构造：独立内存 H2 实例 */
    public ExecutionRecordStore(int maxRecords) {
        this(maxRecords, ScriptStore.testJdbc("executionstore"));
    }

    public void save(RuleExecutionRecord record) {
        if (record == null || record.getTraceId() == null) return;
        table.upsert(record.getTraceId(), null, record.getScriptId(), record.getStartTime(), record);
        trim();
    }

    private void trim() {
        while (table.count() > maxRecords) {
            List<String> ids = table.getAllIds(); // SORT_TIME 升序 → 第一个最旧
            if (ids.isEmpty()) break;
            table.delete(ids.get(0));
        }
    }

    public void clear() {
        table.clear();
    }

    public List<RuleExecutionRecord> listRecent(int limit) {
        return table.all(RuleExecutionRecord.class, limit);
    }

    public RuleExecutionRecord get(String traceId) {
        return table.get(traceId, RuleExecutionRecord.class);
    }

    public List<RuleExecutionRecord> listByScript(String scriptId, int limit) {
        return table.byRule(scriptId, RuleExecutionRecord.class, limit);
    }
}
