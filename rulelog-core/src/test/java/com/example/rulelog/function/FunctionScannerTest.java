package com.example.rulelog.function;

import com.example.rulelog.annotation.RuleFunction;
import com.example.rulelog.annotation.RuleParam;
import com.example.rulelog.param.ParamDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 注解扫描测试：@RuleFunction + @RuleParam（含 schema JSON）递归解析成 ParamDef 树。
 */
class FunctionScannerTest {

    static class TestService {
        @RuleFunction(name = "查询用户等级", desc = "查询用户会员等级", params = {
                @RuleParam(name = "user", label = "用户名", type = "string", required = true, desc = "用户标识")
        })
        public String queryLevel(String user) { return "VIP"; }

        // array 的 schema 是元素定义；元素 object 的 fields 递归嵌套
        @RuleFunction(name = "批量查用户", params = {
                @RuleParam(name = "users", label = "用户列表", type = "array",
                        schema = "{\"name\":\"item\",\"label\":\"用户\",\"type\":\"object\",\"fields\":["
                                + "{\"name\":\"name\",\"label\":\"姓名\",\"type\":\"string\",\"required\":true},"
                                + "{\"name\":\"age\",\"label\":\"年龄\",\"type\":\"number\"},"
                                + "{\"name\":\"addr\",\"label\":\"地址\",\"type\":\"object\",\"fields\":["
                                + "{\"name\":\"city\",\"label\":\"城市\",\"type\":\"string\"}]}]}")
        })
        public Object batchQuery(Object users) { return null; }

        public void noAnnotation() { }
    }

    @Test
    void scansAnnotatedMethodsOnly() {
        List<FunctionMeta> metas = FunctionScanner.scan(new TestService());
        assertEquals(2, metas.size());
        FunctionMeta first = metas.stream().filter(m -> "查询用户等级".equals(m.getName())).findFirst().get();
        assertEquals("查询用户会员等级", first.getDesc());
        assertEquals("queryLevel", first.getMethodName());
        assertEquals(1, first.getParams().size());
        assertEquals("用户名", first.getParams().get(0).getLabel());
        assertTrue(first.getParams().get(0).isRequired());
    }

    @Test
    void parsesNestedObjectAndArrayParams() {
        FunctionMeta meta = FunctionScanner.scan(new TestService()).stream()
                .filter(m -> "批量查用户".equals(m.getName())).findFirst().get();
        ParamDef users = meta.getParams().get(0);
        assertEquals("array", users.getType());
        assertNotNull(users.getElement());
        assertEquals("object", users.getElement().getType());
        assertEquals(3, users.getElement().getFields().size());
        ParamDef addr = users.getElement().getFields().stream()
                .filter(f -> "addr".equals(f.getName())).findFirst().get();
        assertEquals("object", addr.getType());
        assertEquals(1, addr.getFields().size());
        assertEquals("city", addr.getFields().get(0).getName());
        assertEquals("城市", addr.getFields().get(0).getLabel());
    }

    @Test
    void nullBeanSafe() {
        assertTrue(FunctionScanner.scan((Object) null).isEmpty());
    }
}
