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
    void corruptFileIsBackedUpAndDefaultsLoaded() throws Exception {
        Path file = tempDir.resolve("scripts.json");
        Files.write(file, "{坏掉的 json".getBytes(StandardCharsets.UTF_8));

        ScriptStore store = new ScriptStore(tempDir.toString());
        // 损坏时回退到内置示例脚本，不崩溃
        assertEquals(3, store.list().size());

        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            List<Path> backups = files.filter(p -> p.getFileName().toString().startsWith("scripts.json.bak-"))
                    .collect(Collectors.toList());
            assertEquals(1, backups.size(), "损坏文件应被备份");
            assertEquals("{坏掉的 json", new String(Files.readAllBytes(backups.get(0)), StandardCharsets.UTF_8));
        }
    }
}
