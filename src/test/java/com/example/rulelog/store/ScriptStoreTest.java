package com.example.rulelog.store;

import com.example.rulelog.model.Script;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脚本持久化测试（P0 数据可靠性）：
 * 1. 正常读写 round-trip
 * 2. 原子写：落盘后无临时文件残留
 * 3. 文件损坏：备份坏文件、用示例启动、不覆盖备份
 */
class ScriptStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void getReturnsDeepCopyIsolatedFromStore() {
        ScriptStore store = new ScriptStore(tempDir.toString());
        Script s = new Script();
        s.setId("id1");
        s.setName("原名");
        s.setContent("返回 1;");
        store.put(s);

        Script fetched = store.get("id1");
        fetched.setName("外部改动"); // 改外部副本
        fetched.getDefaultParams().put("x", 1);

        Script again = store.get("id1");
        assertEquals("原名", again.getName(), "外部改动不能污染存储");
        assertFalse(again.getDefaultParams().containsKey("x"));
    }

    @Test
    void readWriteRoundTrip() throws Exception {
        ScriptStore store = new ScriptStore(tempDir.toString());
        Script s = new Script();
        s.setId("id1");
        s.setName("测试脚本");
        s.setContent("返回 1;");
        store.put(s);

        Script loaded = new ScriptStore(tempDir.toString()).get("id1");
        assertNotNull(loaded);
        assertEquals("测试脚本", loaded.getName());
        assertEquals("返回 1;", loaded.getContent());
    }

    @Test
    void atomicWriteLeavesNoTempFile() throws Exception {
        ScriptStore store = new ScriptStore(tempDir.toString());
        Script s = new Script();
        s.setId("id1");
        s.setName("测试");
        s.setContent("返回 1;");
        store.put(s);

        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            List<Path> leftovers = files.filter(p -> !p.getFileName().toString().equals("scripts.json"))
                    .collect(Collectors.toList());
            assertTrue(leftovers.isEmpty(), "不应残留临时文件: " + leftovers);
        }
    }

    @Test
    void corruptFileIsBackedUpAndDefaultsLoaded() throws Exception {        Path file = tempDir.resolve("scripts.json");
        Files.write(file, "{坏掉的 json".getBytes(StandardCharsets.UTF_8));

        ScriptStore store = new ScriptStore(tempDir.toString());
        // 损坏时回退到内置示例脚本，不崩溃（4 条：VIP/积分/带参/决策表）
        assertEquals(4, store.list().size());

        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            List<Path> backups = files.filter(p -> p.getFileName().toString().startsWith("scripts.json.bak-"))
                    .collect(Collectors.toList());
            assertEquals(1, backups.size(), "损坏文件应被备份");
            assertEquals("{坏掉的 json", new String(Files.readAllBytes(backups.get(0)), StandardCharsets.UTF_8));
        }
    }

    @Test
    void decisionTableDemoHasValidMeta() {        ScriptStore store = new ScriptStore(tempDir.toString());
        Script demo = store.get("decision_table_demo");
        assertNotNull(demo, "应有决策表演示脚本");
        java.util.Map<String, Object> meta = demo.getMeta();
        assertNotNull(meta);
        // meta 含 cards / contentHash / cardSourceMap
        assertEquals(4, ((java.util.List<?>) meta.get("cards")).size());
        assertEquals(com.example.rulelog.util.ContentHash.hashContent(demo.getContent()), meta.get("contentHash"),
                "contentHash 必须匹配脚本文本，否则前端会把决策表卡重新解析成普通卡");
        // 第三张卡是决策表
        java.util.Map<?, ?> dtCard = (java.util.Map<?, ?>) ((java.util.List<?>) meta.get("cards")).get(2);
        assertEquals("decisiontable", dtCard.get("type"));
        assertEquals("first", dtCard.get("strategy"));
    }
}
