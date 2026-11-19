package com.example.rulelog.service;

import com.example.rulelog.biz.DiscountService;
import com.example.rulelog.biz.UserService;
import com.example.rulelog.express.Express4Engine;
import com.example.rulelog.express.FunctionAliasRegistry;
import com.example.rulelog.model.RuleExecutionRecord;
import com.example.rulelog.model.Script;
import com.example.rulelog.store.ExecutionRecordStore;
import com.example.rulelog.store.ScriptStore;
import com.example.rulelog.trace.OtelTraceService;
import com.example.rulelog.trace.RuleTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 韧性测试：OTel 导出异常绝不能吞掉执行记录、也不能泄漏 ThreadLocal。
 */
class OtelFailureResilienceTest {

    @TempDir
    Path tempDir;

    private Express4Engine engine;
    private FunctionAliasRegistry registry;
    private ScriptStore scriptStore;
    private ExecutionRecordStore recordStore;

    @BeforeEach
    void setUp() throws Exception {
        UserService userService = new UserService();
        engine = new Express4Engine(userService, new DiscountService(userService));
        Field f = Express4Engine.class.getDeclaredField("aliasRegistry");
        f.setAccessible(true);
        registry = (FunctionAliasRegistry) f.get(engine);
        scriptStore = new ScriptStore(tempDir.toString());
        recordStore = new ExecutionRecordStore(200);
        Script s = new Script();
        s.setId("s1");
        s.setName("测试");
        s.setContent("等级 = 查询用户等级(user);\n返回 等级;");
        s.setDefaultParams(new LinkedHashMap<>(Collections.singletonMap("user", "张三")));
        scriptStore.put(s);
    }

    @AfterEach
    void cleanup() {
        RuleTraceContext.clear();
    }

    @Test
    void otelExportFailureDoesNotSwallowRecordOrLeakContext() throws Exception {
        OtelTraceService badOtel = mock(OtelTraceService.class);
        // exportRecord 抛异常（模拟 collector 不可达等故障）
        doThrow(new RuntimeException("collector down")).when(badOtel).exportRecord(any(), any());

        RuleExecutionService service = new RuleExecutionService(
                engine, registry, scriptStore, recordStore, 5000L, badOtel);

        RuleExecutionRecord record = service.run("s1", null);

        // 执行记录仍正常返回、落盘
        assertNotNull(record);
        assertTrue(record.isSuccess());
        assertEquals("VIP", record.getResult());
        assertNotNull(recordStore.get(record.getTraceId()), "OTel 失败时记录仍须落盘");
        // ThreadLocal 已清理
        assertNull(RuleTraceContext.get(), "RuleTraceContext 必须被清理");
        // safeClear 被调用，确保 OTel 的 IdGenerator ThreadLocal 也清理
        verify(badOtel).safeClear();
    }
}
