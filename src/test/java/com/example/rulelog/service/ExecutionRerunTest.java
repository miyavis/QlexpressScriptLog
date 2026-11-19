package com.example.rulelog.service;

import com.example.rulelog.biz.DiscountService;
import com.example.rulelog.biz.UserService;
import com.example.rulelog.exception.NotFoundException;
import com.example.rulelog.express.Express4Engine;
import com.example.rulelog.express.FunctionAliasRegistry;
import com.example.rulelog.model.RuleExecutionRecord;
import com.example.rulelog.model.Script;
import com.example.rulelog.store.ExecutionRecordStore;
import com.example.rulelog.store.ScriptStore;
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
 * 历史参数重跑测试：从历史记录取 scriptId + params 快照，重新执行生成新记录。
 */
class ExecutionRerunTest {

    @TempDir
    Path tempDir;

    private RuleExecutionService service;
    private ExecutionRecordStore recordStore;
    private ScriptStore scriptStore;

    @BeforeEach
    void setUp() throws Exception {
        UserService userService = new UserService();
        Express4Engine engine = new Express4Engine(userService, new DiscountService(userService));
        Field f = Express4Engine.class.getDeclaredField("aliasRegistry");
        f.setAccessible(true);
        FunctionAliasRegistry registry = (FunctionAliasRegistry) f.get(engine);
        scriptStore = new ScriptStore(tempDir.toString());
        recordStore = new ExecutionRecordStore(200);
        service = new RuleExecutionService(engine, registry, scriptStore, recordStore, 5000);
    }

    private Script save(String content) {
        Script s = new Script();
        s.setId("s1");
        s.setName("测试");
        s.setContent(content);
        s.setDefaultParams(new LinkedHashMap<>(Collections.singletonMap("user", "张三")));
        scriptStore.put(s);
        return s;
    }

    @Test
    void rerunWithHistoricalParamsProducesNewRecord() throws Exception {
        save("等级 = 查询用户等级(user);\n返回 等级;");

        // 第一次执行：用 override 参数 user=王五（也是 VIP）
        Map<String, Object> override = new LinkedHashMap<>();
        override.put("user", "王五");
        RuleExecutionRecord first = service.run("s1", override);
        recordStore.save(first);
        assertEquals("VIP", first.getResult());

        // 重跑：用第一次的参数快照，生成新 traceId 的新记录
        RuleExecutionRecord rerun = service.run(first.getScriptId(), first.getParams());
        assertNotEquals(first.getTraceId(), rerun.getTraceId(), "重跑应生成新 traceId");
        assertEquals("VIP", rerun.getResult());
        assertEquals("王五", rerun.getParams().get("user"), "重跑沿用历史参数");
    }

    @Test
    void unknownScriptStillFails() {
        assertThrows(IllegalArgumentException.class, () -> service.run("no-such-script", null));
    }
}
