package com.example.rulelog.compare;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CompareEngineTest {

    private CompareRule rule() {
        CompareRule rule = new CompareRule();
        rule.setId("r1");
        rule.setName("双源对账");

        FetchNode f1 = new FetchNode();
        f1.setId("f1"); f1.setName("取数A"); f1.setSourceName("srcA");
        f1.setResultKey("A");
        Map<String, String> m1 = new LinkedHashMap<>();
        m1.put("user", "${user}");
        f1.setParamMapping(m1);

        FetchNode f2 = new FetchNode();
        f2.setId("f2"); f2.setName("取数B"); f2.setSourceName("srcB");
        f2.setResultKey("B");
        Map<String, String> m2 = new LinkedHashMap<>();
        m2.put("user", "${user}");
        f2.setParamMapping(m2);

        rule.setFetchNodes(Arrays.asList(f1, f2));

        CompareNode cmp = new CompareNode();
        cmp.setKeyField("订单号");
        cmp.setSourceKeys(Arrays.asList("A", "B"));
        FieldCompare fc1 = new FieldCompare("金额", "金额", "numericTolerance");
        fc1.setTolerance(2);
        FieldCompare fc2 = new FieldCompare("等级", "等级", "equal");
        cmp.setFields(Arrays.asList(fc1, fc2));
        rule.setCompareNode(cmp);
        return rule;
    }

    @Test
    void alignsByKeyAndCompares() {
        CompareEngine engine = new CompareEngine((type, name, params) -> {
            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("订单号", "O001"); row1.put("金额", 100); row1.put("等级", "VIP");
            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("订单号", "O002"); row2.put("金额", 200); row2.put("等级", "普通");
            if ("srcA".equals(name)) {
                assertEquals("张三", params.get("user"), "取数参数应解析上下文");
                return Arrays.asList(row1, row2);
            }
            // srcB：O001 差 3（超过容差2） → 不一致；O002 完全一致
            Map<String, Object> row1b = new LinkedHashMap<>(row1);
            row1b.put("金额", 103);
            Map<String, Object> row2b = new LinkedHashMap<>(row2);
            return Arrays.asList(row1b, row2b);
        });

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("user", "张三");
        CompareResult r = engine.run(rule(), ctx);

        assertEquals(2, r.getTotalRows());
        assertEquals(1, r.getConsistentRows());
        assertEquals(1, r.getInconsistentRows());
        assertEquals(4, r.getTotalCells());
        assertEquals(1, r.getInconsistentCells(), "仅 O001 的金额超过容差");

        CompareRow o1 = r.getRows().stream().filter(x -> "O001".equals(x.getKey())).findFirst().get();
        assertFalse(o1.isConsistent());
        assertFalse(o1.getCells().get("金额").isConsistent());
        assertTrue(o1.getCells().get("等级").isConsistent());
        assertEquals(100, o1.getSourceValues().get("A").get("金额"));
        assertEquals(103, o1.getSourceValues().get("B").get("金额"));

        CompareRow o2 = r.getRows().stream().filter(x -> "O002".equals(x.getKey())).findFirst().get();
        assertTrue(o2.isConsistent());
    }

    @Test
    void handlesSingleMapAndMissingKeys() {
        CompareEngine engine = new CompareEngine((type, name, params) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("订单号", "O001"); row.put("金额", 100); row.put("等级", "VIP");
            return "srcA".equals(name) ? row : Collections.singletonList(row);
        });
        CompareResult r = engine.run(rule(), Collections.emptyMap());
        assertEquals(1, r.getTotalRows());
        assertTrue(r.getRows().get(0).isConsistent());
    }

    @Test
    void emptyRuleReturnsEmptyResult() {
        CompareEngine engine = new CompareEngine((t, n, p) -> null);
        CompareResult r = engine.run(new CompareRule(), null);
        assertEquals(0, r.getTotalRows());
    }
}
