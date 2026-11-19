package com.example.rulelog.express;

import com.alibaba.qlexpress4.QLResult;
import com.example.rulelog.biz.DiscountService;
import com.example.rulelog.biz.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 决策表生成脚本的可执行性验证：前端 decisionTable.js 生成的如果/否则 + && 能被 QLExpress 4.1.4 正确执行。
 */
class DecisionTableScriptTest {

    private Express4Engine engine;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService();
        engine = new Express4Engine(userService, new DiscountService(userService));
    }

    @Test
    void generatedFirstStrategyScriptExecutes() throws Exception {
        // 对应前端 genDecisionScript(first) 的输出
        String script = String.join("\n",
                "如果 (积分 > 100 && 等级 == \"VIP\") 则 {",
                "    折扣 = 0.8;",
                "} 否则 {",
                "    如果 (积分 > 50 && 等级 == \"普通\") 则 {",
                "        折扣 = 0.9;",
                "    } 否则 {",
                "        折扣 = 1;",
                "    }",
                "}");
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("积分", 380);
        ctx.put("等级", "VIP");
        QLResult r = engine.execute(script + "\n返回 折扣;", ctx, 5000);
        assertEquals(0.8, ((Number) r.getResult()).doubleValue(), 0.0001);
    }

    @Test
    void generatedCollectStrategyScriptExecutes() throws Exception {
        String script = String.join("\n",
                "总分 = 0;",
                "如果 (等级 == \"VIP\") 则 {",
                "    总分 = 总分 + 20;",
                "}",
                "如果 (积分 > 100) 则 {",
                "    总分 = 总分 + 10;",
                "}");
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("等级", "VIP");
        ctx.put("积分", 380);
        QLResult r = engine.execute(script + "\n返回 总分;", ctx, 5000);
        assertEquals(30, ((Number) r.getResult()).intValue());
    }
}
