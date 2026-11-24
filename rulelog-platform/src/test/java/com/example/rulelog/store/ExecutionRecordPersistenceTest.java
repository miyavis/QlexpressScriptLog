package com.example.rulelog.store;

import com.example.rulelog.model.RuleExecutionRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 执行记录存储测试（运行时走 H2）：
 * 写入/查询、按脚本过滤、保留上限裁剪、清空。
 * JSON 只在启动时作为种子由 JsonToH2LoadRunner 加载，不再由 Store 读写文件。
 */
class ExecutionRecordPersistenceTest {

    private RuleExecutionRecord rec(String traceId, String scriptId, long start) {
        RuleExecutionRecord r = new RuleExecutionRecord();
        r.setTraceId(traceId);
        r.setScriptId(scriptId);
        r.setSuccess(true);
        r.setResult("VIP");
        r.setStartTime(start);
        r.setEndTime(start + 50);
        return r;
    }

    @Test
    void savesToH2AndListsNewestFirst() {
        ExecutionRecordStore store = new ExecutionRecordStore(200);
        store.save(rec("t1", "s1", 1000L));
        store.save(rec("t2", "s1", 2000L));

        List<RuleExecutionRecord> all = store.listRecent(10);
        assertEquals(2, all.size());
        assertEquals("t2", all.get(0).getTraceId());
        assertEquals("t1", store.get("t1").getTraceId());
        assertEquals(2, store.listByScript("s1", 10).size());
    }

    @Test
    void trimsToMaxRecords() {
        ExecutionRecordStore store = new ExecutionRecordStore(2);
        store.save(rec("t1", "s1", 1000L));
        store.save(rec("t2", "s1", 2000L));
        store.save(rec("t3", "s1", 3000L));

        assertEquals(2, store.listRecent(10).size(), "超出上限应裁剪最旧记录");
    }

    @Test
    void clearRemovesAll() {
        ExecutionRecordStore store = new ExecutionRecordStore(200);
        store.save(rec("t1", "s1", 1000L));
        store.clear();
        assertTrue(store.listRecent(10).isEmpty());
    }
}
