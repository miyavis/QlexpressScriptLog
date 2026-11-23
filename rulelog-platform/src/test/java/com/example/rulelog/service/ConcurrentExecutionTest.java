package com.example.rulelog.service;

import com.example.rulelog.biz.DiscountService;
import com.example.rulelog.biz.UserService;
import com.example.rulelog.express.Express4Engine;
import com.example.rulelog.express.FunctionAliasRegistry;
import com.example.rulelog.model.RuleExecutionRecord;
import com.example.rulelog.store.ExecutionRecordStore;
import com.example.rulelog.store.ScriptStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发执行测试：验证共享引擎 + ThreadLocal 采集在多请求并发下日志不串号、traceId 唯一。
 * （此前是"需验证"的假设，这里用真实并发压测兜底。）
 */
class ConcurrentExecutionTest {

    @TempDir
    Path tempDir;

    private RuleExecutionService service;

    @BeforeEach
    void setUp() throws Exception {
        UserService userService = new UserService();
        Express4Engine engine = new Express4Engine(userService, new DiscountService(userService));
        Field f = Express4Engine.class.getDeclaredField("aliasRegistry");
        f.setAccessible(true);
        FunctionAliasRegistry registry = (FunctionAliasRegistry) f.get(engine);
        ScriptStore scriptStore = new ScriptStore(tempDir.toString());
        com.example.rulelog.model.Script s = new com.example.rulelog.model.Script();
        s.setId("s1");
        s.setName("并发测试");
        s.setContent("等级 = 查询用户等级(user);\n返回 等级;");
        s.setDefaultParams(new LinkedHashMap<>(Collections.singletonMap("user", "张三")));
        scriptStore.put(s);
        service = new RuleExecutionService(engine, registry, scriptStore,
                new ExecutionRecordStore(200), 5000);
    }

    @Test
    void concurrentExecutionsKeepTraceIdsUniqueAndResultsCorrect() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<RuleExecutionRecord>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> service.run("s1", null));
        }
        List<Future<RuleExecutionRecord>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        List<String> traceIds = new ArrayList<>();
        for (Future<RuleExecutionRecord> future : futures) {
            RuleExecutionRecord r = future.get();
            assertTrue(r.isSuccess());
            assertEquals("VIP", r.getResult(), "并发下结果应正确");
            // 并发下每次执行的 traceId 必须唯一（共享引擎不串号）
            assertNotNull(r.getTraceId());
            // 根步骤（手动埋点，不依赖 AOP）应被采集
            assertFalse(r.getRootSteps().isEmpty(), "并发下手动埋点步骤应被采集，不丢失");
            for (var step : r.getRootSteps()) {
                assertEquals(r.getTraceId(), step.getTraceId(), "步骤 traceId 必须与本次执行一致");
            }
            traceIds.add(r.getTraceId());
        }
        assertEquals(threads, traceIds.stream().distinct().count(), "每次执行 traceId 必须唯一");
    }
}
