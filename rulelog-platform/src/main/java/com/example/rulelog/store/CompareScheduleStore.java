package com.example.rulelog.store;

import com.example.rulelog.compare.CompareSchedule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 比对定时任务持久化：运行时读写 H2 的 QL_COMPARE_SCHEDULE 表。 */
@Component
public class CompareScheduleStore {

    private static final String DDL = "CREATE TABLE IF NOT EXISTS QL_COMPARE_SCHEDULE ("
            + "ID VARCHAR(64) PRIMARY KEY, NAME VARCHAR(128), RULE_ID VARCHAR(64), "
            + "SORT_TIME BIGINT, DATA CLOB)";

    private final H2JsonTable table;

    @Autowired
    public CompareScheduleStore(JdbcTemplate jdbc) {
        this.table = new H2JsonTable(jdbc, "QL_COMPARE_SCHEDULE", DDL);
    }

    public CompareScheduleStore(String ignoredDataDir) {
        this(ScriptStore.testJdbc("compareschedule"));
    }

    public List<CompareSchedule> list() {
        return table.all(CompareSchedule.class, 10000);
    }

    public List<CompareSchedule> listByRule(String ruleId) {
        return table.byRule(ruleId, CompareSchedule.class, 10000);
    }

    public CompareSchedule get(String id) {
        return table.get(id, CompareSchedule.class);
    }

    public CompareSchedule put(CompareSchedule s) {
        if (s.getId() == null || s.getId().trim().isEmpty()) {
            s.setId("sched_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        if (s.getCreatedAt() == 0) s.setCreatedAt(System.currentTimeMillis());
        table.upsert(s.getId(), s.getName(), s.getRuleId(), s.getCreatedAt(), s);
        return s;
    }

    public boolean remove(String id) {
        return table.delete(id);
    }
}
