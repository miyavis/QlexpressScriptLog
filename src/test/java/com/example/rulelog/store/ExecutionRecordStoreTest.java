package com.example.rulelog.store;

import com.example.rulelog.model.RuleExecutionRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 执行记录内存库测试：
 * 1. 按脚本过滤 + 最近优先 + limit
 * 2. 超限淘汰最早记录
 */
class ExecutionRecordStoreTest {

    private RuleExecutionRecord rec(String traceId, String scriptId) {
        RuleExecutionRecord r = new RuleExecutionRecord();
        r.setTraceId(traceId);
        r.setScriptId(scriptId);
        return r;
    }

    @Test
    void listByScriptFiltersAndOrdersRecentFirst() {
        ExecutionRecordStore store = new ExecutionRecordStore(200);
        store.save(rec("t1", "a"));
        store.save(rec("t2", "b"));
        store.save(rec("t3", "a"));

        List<RuleExecutionRecord> result = store.listByScript("a", 10);
        assertEquals(2, result.size());
        assertEquals("t3", result.get(0).getTraceId());
        assertEquals("t1", result.get(1).getTraceId());
    }

    @Test
    void listByScriptRespectsLimit() {
        ExecutionRecordStore store = new ExecutionRecordStore(200);
        for (int i = 0; i < 5; i++) store.save(rec("t" + i, "a"));
        assertEquals(2, store.listByScript("a", 2).size());
    }

    @Test
    void evictsOldestWhenOverCapacity() {
        ExecutionRecordStore store = new ExecutionRecordStore(2);
        store.save(rec("t1", "a"));
        store.save(rec("t2", "a"));
        store.save(rec("t3", "a"));
        assertEquals(2, store.listRecent(10).size());
        assertNull(store.get("t1"));
        assertNotNull(store.get("t3"));
    }
}
