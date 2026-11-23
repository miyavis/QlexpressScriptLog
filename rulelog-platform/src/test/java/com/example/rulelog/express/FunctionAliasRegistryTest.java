package com.example.rulelog.express;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 函数别名注册表单元测试：产品化的函数注册表要能回答
 * "平台有哪些中文函数？各自对应哪个 Service 方法？参数怎么填？"
 */
class FunctionAliasRegistryTest {

    @Test
    void 注册后能通过Java方法签名反查中文名() {
        FunctionAliasRegistry registry = new FunctionAliasRegistry();
        registry.register("查询用户等级", "UserService", "queryLevel", "user");
        assertEquals("查询用户等级", registry.getChineseName("UserService", "queryLevel"));
    }

    @Test
    void 未注册的方法返回null() {
        FunctionAliasRegistry registry = new FunctionAliasRegistry();
        assertEquals(null, registry.getChineseName("UserService", "notExist"));
    }

    @Test
    void list返回注册项的完整元数据() {
        FunctionAliasRegistry registry = new FunctionAliasRegistry();
        registry.register("查询用户等级", "UserService", "queryLevel", "user");
        registry.register("计算折扣", "DiscountService", "calcDiscount", "user");

        List<Map<String, String>> list = registry.list();
        assertEquals(2, list.size());

        Map<String, String> first = list.get(0);
        assertEquals("查询用户等级", first.get("chineseName"));
        assertEquals("UserService", first.get("serviceName"));
        assertEquals("queryLevel", first.get("methodName"));
        assertEquals("user", first.get("paramHint"));
    }

    @Test
    void list按注册顺序返回() {
        FunctionAliasRegistry registry = new FunctionAliasRegistry();
        registry.register("计算折扣", "DiscountService", "calcDiscount", "user");
        registry.register("查询用户等级", "UserService", "queryLevel", "user");
        assertEquals("计算折扣", registry.list().get(0).get("chineseName"));
    }

    @Test
    void 三参数旧签名仍然可用且参数提示为空() {
        FunctionAliasRegistry registry = new FunctionAliasRegistry();
        registry.register("记录日志", "StepLogger", "log");
        assertNotNull(registry.getChineseName("StepLogger", "log"));
        assertEquals("", registry.list().get(0).get("paramHint"));
    }

    @Test
    void paramHint未提供时为空串而不是null() {
        FunctionAliasRegistry registry = new FunctionAliasRegistry();
        registry.register("记录日志", "StepLogger", "log", null);
        assertFalse(registry.list().get(0).containsKey("paramHint") && registry.list().get(0).get("paramHint") == null);
        assertTrue(registry.list().get(0).get("paramHint").isEmpty());
    }
}
