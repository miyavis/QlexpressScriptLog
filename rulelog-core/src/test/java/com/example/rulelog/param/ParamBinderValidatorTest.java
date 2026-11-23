package com.example.rulelog.param;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 参数绑定器 + 校验器测试。
 */
class ParamBinderValidatorTest {

    private ParamDef p(String name, String label, String type) {
        return new ParamDef(name, label, type);
    }

    // ---------- ParamValidator ----------
    @Test
    void validatesRequiredAndTypes() {
        List<ParamDef> defs = new ArrayList<>();
        ParamDef user = p("user", "用户名", "string"); user.setRequired(true);
        ParamDef age = p("age", "年龄", "number");
        defs.add(user); defs.add(age);

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("user", "张三"); ok.put("age", 28);
        assertTrue(ParamValidator.validate(defs, ok).isEmpty());

        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("age", "not-number"); // user 缺失 + age 类型错
        List<String> errs = ParamValidator.validate(defs, bad);
        assertEquals(2, errs.size());
        assertTrue(errs.stream().anyMatch(e -> e.contains("用户名") && e.contains("必填")));
        assertTrue(errs.stream().anyMatch(e -> e.contains("年龄") && e.contains("数字")));
    }

    @Test
    void validatesNestedObjectFields() {
        ParamDef addr = p("addr", "地址", "object");
        ParamDef city = p("city", "城市", "string"); city.setRequired(true);
        addr.setFields(Collections.singletonList(city));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("addr", Collections.singletonMap("city", "")); // 城市必填但空
        List<String> errs = ParamValidator.validate(Collections.singletonList(addr), params);
        assertEquals(1, errs.size());
        assertTrue(errs.get(0).contains("addr.city") || errs.get(0).contains("城市"));
    }

    @Test
    void validatesArrayElements() {
        ParamDef users = p("users", "用户列表", "array");
        ParamDef item = p("item", "用户", "object");
        ParamDef name = p("name", "姓名", "string"); name.setRequired(true);
        item.setFields(Collections.singletonList(name));
        users.setElement(item);

        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Object> u1 = new LinkedHashMap<>(); u1.put("name", "张三");
        Map<String, Object> u2 = new LinkedHashMap<>(); u2.put("name", ""); // 第2个姓名空
        params.put("users", Arrays.asList(u1, u2));
        List<String> errs = ParamValidator.validate(Collections.singletonList(users), params);
        assertEquals(1, errs.size());
        assertTrue(errs.get(0).contains("users[1]"));
    }

    // ---------- ParamBinder ----------
    @Test
    void bindsSimpleValuesByParamName() {
        List<ParamDef> defs = Arrays.asList(p("user", "用户名", "string"), p("age", "年龄", "number"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("user", "张三"); params.put("age", 28);
        Object[] args = ParamBinder.bind(defs, params);
        assertEquals(2, args.length);
        assertEquals("张三", args[0]);
        assertEquals(28, args[1]);
    }

    @Test
    void bindsMissingOptionalAsNull() {
        List<ParamDef> defs = Arrays.asList(p("user", "用户名", "string"), p("age", "年龄", "number"));
        Map<String, Object> params = Collections.singletonMap("user", "张三");
        Object[] args = ParamBinder.bind(defs, params);
        assertEquals("张三", args[0]);
        assertNull(args[1]);
    }
}
