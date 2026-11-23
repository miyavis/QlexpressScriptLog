package com.example.rulelog.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ContentHash 与前端 hashContent 一致性测试：
 * 用决策表演示脚本的固定样本验证 Java 实现产生与前端相同的值（4e58573e）。
 */
class ContentHashTest {

    @Test
    void matchesFrontendHashForDemoScript() {
        String script = "积分 = 查询用户积分(user);\n"
                + "等级 = 查询用户等级(user);\n"
                + "如果 (积分 > 100 && 等级 == \"VIP\") 则 {\n"
                + "    折扣 = 0.7;\n"
                + "} 否则 {\n"
                + "    如果 (积分 > 50) 则 {\n"
                + "        折扣 = 0.85;\n"
                + "    } 否则 {\n"
                + "        折扣 = 1;\n"
                + "    }\n"
                + "}\n"
                + "返回 折扣;";
        assertEquals("4e58573e", ContentHash.hashContent(script));
    }

    @Test
    void emptyAndNullSafe() {
        assertEquals(ContentHash.hashContent(""), ContentHash.hashContent(null));
    }
}
