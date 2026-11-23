package com.example.rulelog.engine;

import com.example.rulelog.annotation.RuleFunction;
import com.example.rulelog.annotation.RuleParam;
import com.example.rulelog.loader.FileRuleLoader;
import com.example.rulelog.loader.RuleDefinition;
import com.example.rulelog.loader.RuleLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** SDK 端到端：builder + loader + 注解 + 参数校验 + 执行 */
class RuleEngineSdkTest {

    @Test
    void runByIdWithFileLoader() throws Exception {
        Path dir = Files.createTempDirectory("sdk-rules");
        Files.writeString(dir.resolve("calc.rule"),
                "结果 = 积分 * 2;\n返回 结果;");
        Files.writeString(dir.resolve("calc.meta.json"),
                "{\"name\":\"翻倍\",\"params\":[{\"name\":\"积分\",\"label\":\"积分\",\"type\":\"number\",\"required\":true}]}");

        RuleEngine engine = RuleEngine.builder()
                .loader(FileRuleLoader.fromDirectory(dir.toString()))
                .build();

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("积分", 100);
        Result r = engine.runById("calc", ok);
        assertTrue(r.isSuccess(), r.getError());
        assertEquals(200, ((Number) r.getValue()).intValue());
        assertNotNull(r.getTraceId());

        // 参数缺失 → 校验失败
        Result bad = engine.runById("calc", Collections.emptyMap());
        assertFalse(bad.isSuccess());
        assertTrue(bad.getError().contains("积分"));
    }

    @Test
    void runByIdMissingRule() {
        RuleEngine engine = RuleEngine.builder().loader(new RuleLoader() {
            public RuleDefinition load(String id) { return null; }
            public List<String> listRuleIds() { return Collections.emptyList(); }
        }).build();
        Result r = engine.runById("nope", Collections.emptyMap());
        assertFalse(r.isSuccess());
        assertTrue(r.getError().contains("不存在"));
    }

    static class Biz {
        @RuleFunction(name = "打招呼", params = @RuleParam(name = "name", label = "姓名", type = "string"))
        public String hi(String name) { return "hi " + name; }
    }

    @Test
    void scanFunctionsRegistersMeta() {
        RuleEngine engine = RuleEngine.builder().scanFunctions(new Biz()).build();
        assertTrue(engine.getFunctions().containsKey("打招呼"));
        assertEquals("姓名", engine.getFunctions().get("打招呼").getParams().get(0).getLabel());
    }
}
