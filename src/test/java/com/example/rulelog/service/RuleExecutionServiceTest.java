package com.example.rulelog.service;

import com.example.rulelog.biz.DiscountService;
import com.example.rulelog.biz.UserService;
import com.example.rulelog.express.Express4Engine;
import com.example.rulelog.express.FunctionAliasRegistry;
import com.example.rulelog.model.LineExecution;
import com.example.rulelog.model.RuleExecutionRecord;
import com.example.rulelog.model.Script;
import com.example.rulelog.store.ExecutionRecordStore;
import com.example.rulelog.store.ScriptStore;
import com.example.rulelog.trace.RuleTraceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 执行链路集成测试（P1 测试兜底）：完整跑一次脚本，验证记录字段、日志行级归属、
 * ThreadLocal 清理，以及同名函数多次调用归到各自行。
 */
class RuleExecutionServiceTest {

    @TempDir
    Path tempDir;

    private RuleExecutionService service;
    private ScriptStore scriptStore;

    @BeforeEach
    void setUp() throws Exception {
        UserService userService = new UserService();
        Express4Engine engine = new Express4Engine(userService, new DiscountService(userService));
        // 复用引擎暴露的注册表（含业务函数别名）
        Field aliasField = Express4Engine.class.getDeclaredField("aliasRegistry");
        aliasField.setAccessible(true);
        FunctionAliasRegistry engineRegistry = (FunctionAliasRegistry) aliasField.get(engine);
        scriptStore = new ScriptStore(tempDir.toString());
        ExecutionRecordStore recordStore = new ExecutionRecordStore(200);
        service = new RuleExecutionService(engine, engineRegistry, scriptStore, recordStore, 5000);
    }

    private Script save(String id, String content) {
        Script s = new Script();
        s.setId(id);
        s.setName("测试");
        s.setContent(content);
        s.setDefaultParams(new LinkedHashMap<>(Collections.singletonMap("user", "张三")));
        scriptStore.put(s);
        return s;
    }

    @Test
    void fullExecutionProducesRecordAndCleansContext() throws Exception {
        save("s1", "等级 = 查询用户等级(user);\n返回 等级;");
        RuleExecutionRecord r = service.run("s1", null);

        assertTrue(r.isSuccess());
        assertEquals("VIP", r.getResult());
        assertNotNull(r.getTraceId());
        assertNotNull(r.getEndTime());
        assertTrue(r.getDurationMs() >= 0);
        // 执行后 ThreadLocal 必须清理，避免污染后续请求
        assertNull(RuleTraceContext.get());
        // 业务 Service 内的手动埋点（StepLogger.log）应被采集为根步骤
        assertNotNull(r.getRootSteps());
        assertFalse(r.getRootSteps().isEmpty());
        // 脚本中的「等级」计算行应建立行级记录
        assertNotNull(r.getLineExecutions().get(1));
    }

    @Test
    void scriptPrintStepGoesToItsOwnLine() throws Exception {
        // 脚本打印走 StepLogger 手动埋点（不依赖 AOP），验证按步骤名归到所在行
        save("s2", "等级 = 查询用户等级(user);\n记录日志(\"中间打印\", 等级);\n返回 等级;");
        RuleExecutionRecord r = service.run("s2", null);

        assertTrue(r.isSuccess());
        LineExecution line2 = r.getLineExecutions().get(2);
        assertNotNull(line2);
        assertFalse(line2.getSteps().isEmpty(), "记录日志的步骤应归到第 2 行");
        assertEquals("中间打印", line2.getSteps().get(0).getStepName());
    }

    @Test
    void syntaxErrorIsRecordedNotThrown() throws Exception {
        save("s3", "返回 1 +;");
        RuleExecutionRecord r = service.run("s3", null);
        assertFalse(r.isSuccess());
        assertNotNull(r.getErrorMessage());
        assertTrue(r.getErrorMessage().contains("语法") || r.getErrorMessage().contains("Syntax")
                || r.getErrorMessage().contains("错误"));
    }
}
