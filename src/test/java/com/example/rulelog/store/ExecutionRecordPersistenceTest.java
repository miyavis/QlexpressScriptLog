package com.example.rulelog.store;

import com.example.rulelog.model.RuleExecutionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 执行记录落盘测试：重启后记录不丢（最小持久化方案，不做 DB/RBAC）。
 */
class ExecutionRecordPersistenceTest {

    @TempDir
    Path tempDir;

    private RuleExecutionRecord rec(String traceId, String scriptId) {
        RuleExecutionRecord r = new RuleExecutionRecord();
        r.setTraceId(traceId);
        r.setScriptId(scriptId);
        r.setSuccess(true);
        r.setResult("VIP");
        r.setStartTime(1000L);
        r.setEndTime(1050L);
        return r;
    }

    @Test
    void recordsSurviveStoreRestart() {
        ExecutionRecordStore store = new ExecutionRecordStore(200, tempDir.toString());
        store.save(rec("t1", "s1"));
        store.save(rec("t2", "s1"));

        // 新建 store 实例模拟重启：应从磁盘加载
        ExecutionRecordStore restarted = new ExecutionRecordStore(200, tempDir.toString());
        List<RuleExecutionRecord> all = restarted.listRecent(10);
        assertEquals(2, all.size());
        assertEquals("t2", all.get(0).getTraceId());
    }

    @Test
    void noFileStartsEmpty() {
        ExecutionRecordStore store = new ExecutionRecordStore(200, tempDir.resolve("fresh").toString());
        assertTrue(store.listRecent(10).isEmpty());
    }

    @Test
    void corruptionStartsEmptyNotCrash() throws Exception {
        java.nio.file.Files.write(tempDir.resolve("executions.json"),
                "{坏 json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ExecutionRecordStore store = new ExecutionRecordStore(200, tempDir.toString());
        assertTrue(store.listRecent(10).isEmpty(), "损坏文件应安全降级为空，不让启动崩溃");
    }
}
