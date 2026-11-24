package com.example.rulelog.store;

import com.example.rulelog.compare.CompareRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 比对执行历史：运行时读写 H2 的 QL_COMPARE_RECORD 表。 */
@Component
public class CompareRecordStore {

    private static final String DDL = "CREATE TABLE IF NOT EXISTS QL_COMPARE_RECORD ("
            + "ID VARCHAR(64) PRIMARY KEY, NAME VARCHAR(128), RULE_ID VARCHAR(64), "
            + "SORT_TIME BIGINT, DATA CLOB)";

    private final H2JsonTable table;

    @Autowired
    public CompareRecordStore(JdbcTemplate jdbc) {
        this.table = new H2JsonTable(jdbc, "QL_COMPARE_RECORD", DDL);
    }

    public CompareRecordStore(String ignoredDataDir) {
        this(ScriptStore.testJdbc("comparerecord"));
    }

    public void append(CompareRecord record) {
        if (record == null || record.getId() == null) return;
        table.upsert(record.getId(), record.getRuleName(), record.getRuleId(), record.getSavedAt(), record);
    }

    /** 历史列表：可按 ruleId + 时间范围（epoch millis，含端点）过滤，按时间倒序 */
    public List<CompareRecord> list(String ruleId, int limit, Long from, Long to) {
        return table.between(ruleId, from, to, CompareRecord.class, limit);
    }
}
