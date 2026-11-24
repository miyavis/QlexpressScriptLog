package com.example.rulelog.store;

import com.example.rulelog.model.Script;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脚本存储测试（运行时 H2，JSON 仅启动种子）：
 * 读写 round-trip、深拷贝隔离、删除、内置示例 meta 完整性。
 */
class ScriptStoreTest {

    private ScriptStore store() {
        return new ScriptStore("ignored"); // 独立内存 H2，首次构造写入内置示例
    }

    @Test
    void compareDemoSeedHasFetchAndCompareCards() {
        Script s = store().get("compare_demo");
        assertNotNull(s, "应内置取数+比对双卡演示");
        assertNotNull(s.getMeta());
        java.util.List<?> cards = (java.util.List<?>) s.getMeta().get("cards");
        assertEquals(2, cards.size(), "取数卡 + 比对卡");
        assertEquals("call", ((java.util.Map<?, ?>) cards.get(0)).get("type"));
        assertEquals("compare", ((java.util.Map<?, ?>) cards.get(1)).get("type"));
        java.util.Map<?, ?> cmp = (java.util.Map<?, ?>) cards.get(1);
        java.util.Map<?, ?> rule = (java.util.Map<?, ?>) cmp.get("compareRule");
        assertEquals(4, ((java.util.List<?>) rule.get("fetchNodes")).size(),
                "应为 取数A/取数B/加工/取数C 四个节点");
    }

    @Test
    void getReturnsDeepCopyIsolatedFromStore() {
        ScriptStore store = store();
        Script s = new Script();
        s.setId("id1");
        s.setName("原名");
        s.setContent("返回 1;");
        store.put(s);

        Script fetched = store.get("id1");
        fetched.setName("外部改动");
        fetched.getDefaultParams().put("x", 1);

        Script again = store.get("id1");
        assertEquals("原名", again.getName(), "外部改动不能污染存储");
        assertFalse(again.getDefaultParams().containsKey("x"));
    }

    @Test
    void readWriteRoundTripAndRemove() {
        ScriptStore store = store();
        Script s = new Script();
        s.setId("id1");
        s.setName("测试脚本");
        s.setContent("返回 1;");
        store.put(s);

        Script loaded = store.get("id1");
        assertNotNull(loaded);
        assertEquals("测试脚本", loaded.getName());
        assertEquals("返回 1;", loaded.getContent());

        assertTrue(store.remove("id1"));
        assertNull(store.get("id1"));
    }

    @Test
    void seedsFiveScripts() {
        assertEquals(5, store().list().size());
    }

    @Test
    void decisionTableDemoHasValidMeta() {
        Script demo = store().get("decision_table_demo");
        assertNotNull(demo, "应有决策表演示脚本");
        java.util.Map<String, Object> meta = demo.getMeta();
        assertNotNull(meta);
        assertEquals(4, ((java.util.List<?>) meta.get("cards")).size());
        assertEquals(com.example.rulelog.util.ContentHash.hashContent(demo.getContent()), meta.get("contentHash"),
                "contentHash 必须匹配脚本文本，否则前端会把决策表卡重新解析成普通卡");
        java.util.Map<?, ?> dtCard = (java.util.Map<?, ?>) ((java.util.List<?>) meta.get("cards")).get(2);
        assertEquals("decisiontable", dtCard.get("type"));
        assertEquals("first", dtCard.get("strategy"));
    }
}
