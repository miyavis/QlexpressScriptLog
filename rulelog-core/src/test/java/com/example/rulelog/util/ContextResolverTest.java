package com.example.rulelog.util;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ContextResolverTest {

    private Map<String, Object> context() {
        Map<String, Object> addr = new LinkedHashMap<>();
        addr.put("city", "北京");
        Map<String, Object> u1 = new LinkedHashMap<>();
        u1.put("name", "张三"); u1.put("age", 28); u1.put("addr", addr);
        Map<String, Object> u2 = new LinkedHashMap<>();
        u2.put("name", "李四"); u2.put("age", 35);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("user", u1);
        ctx.put("users", Arrays.asList(u1, u2));
        ctx.put("city", "上海");
        return ctx;
    }

    @Test
    void resolvesNestedPath() {
        Map<String, Object> ctx = context();
        assertEquals("张三", ContextResolver.resolvePath(ctx, "user.name"));
        assertEquals("北京", ContextResolver.resolvePath(ctx, "user.addr.city"));
        assertEquals("上海", ContextResolver.resolvePath(ctx, "city"));
    }

    @Test
    void resolvesArrayIndex() {
        Map<String, Object> ctx = context();
        assertEquals("李四", ContextResolver.resolvePath(ctx, "users[1].name"));
        assertEquals("张三", ContextResolver.resolvePath(ctx, "users[0].name"));
        assertNull(ContextResolver.resolvePath(ctx, "users[9].name"));
    }

    @Test
    void wholePlaceholderKeepsType() {
        Map<String, Object> ctx = context();
        Object v = ContextResolver.resolveValue("${user}", ctx);
        assertTrue(v instanceof Map, "整体占位符应返回原对象，保持类型");
        assertEquals("张三", ContextResolver.resolveValue("${user.name}", ctx));
    }

    @Test
    void mixedTextReplacesPlaceholders() {
        Map<String, Object> ctx = context();
        assertEquals("用户张三在28岁", ContextResolver.resolveValue("用户${user.name}在${user.age}岁", ctx));
    }

    @Test
    void resolveParamsMapsAll() {
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("name", "${user.name}");
        mapping.put("city", "${user.addr.city}");
        Map<String, Object> out = ContextResolver.resolveParams(mapping, context());
        assertEquals("张三", out.get("name"));
        assertEquals("北京", out.get("city"));
    }

    @Test
    void missingPathReturnsNull() {
        assertNull(ContextResolver.resolvePath(context(), "user.missing"));
        assertNull(ContextResolver.resolvePath(null, "user"));
    }
}
