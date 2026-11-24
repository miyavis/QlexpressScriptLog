package com.example.rulelog.config;

import com.example.rulelog.compare.CompareRecord;
import com.example.rulelog.compare.CompareRule;
import com.example.rulelog.compare.CompareSchedule;
import com.example.rulelog.model.RuleExecutionRecord;
import com.example.rulelog.model.Script;
import com.example.rulelog.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 启动数据加载：把 data/*.json（主存储）全量灌入 H2 镜像表。
 *
 * 设计：JSON 仍是唯一事实源（写操作照旧落 JSON）；H2 每次启动重建一致性快照，
 * 供统一查询 / SDK / 对外 API 使用。这样换机器只拷 data 目录即可，H2 不需要持久化。
 */
@Component
@Order(1)
public class JsonToH2LoadRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JsonToH2LoadRunner.class);
    private final JdbcTemplate jdbc;
    private final Path dataDir;

    public JsonToH2LoadRunner(JdbcTemplate jdbc, @Value("${app.data-dir:data}") String dataDir) {
        this.jdbc = jdbc;
        this.dataDir = resolve(dataDir);
    }

    private Path resolve(String dir) {
        Path p = Paths.get(dir);
        if (p.isAbsolute()) return p;
        try {
            org.springframework.boot.system.ApplicationHome home =
                    new org.springframework.boot.system.ApplicationHome(JsonToH2LoadRunner.class);
            Path base = home.getDir() == null ? null : home.getDir().toPath();
            if (base != null) return base.resolve(p).normalize();
        } catch (Exception e) { /* ignore */ }
        return Paths.get("").toAbsolutePath().resolve(p).normalize();
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("H2 镜像数据目录: {}", dataDir.toAbsolutePath());
        reload("scripts.json", "QL_SCRIPT", Script::getId, Script::getName, s -> null,
                Script::getUpdatedAt, new TypeReference<List<Script>>() {});
        reload("executions.json", "QL_EXECUTION", RuleExecutionRecord::getTraceId, r -> null,
                RuleExecutionRecord::getScriptId, RuleExecutionRecord::getStartTime,
                new TypeReference<List<RuleExecutionRecord>>() {});
        reload("compare-rules.json", "QL_COMPARE_RULE", CompareRule::getId, CompareRule::getName,
                r -> null, CompareRule::getUpdatedAt, new TypeReference<List<CompareRule>>() {});
        reload("compare-records.json", "QL_COMPARE_RECORD", CompareRecord::getId, CompareRecord::getRuleName,
                CompareRecord::getRuleId, CompareRecord::getSavedAt, new TypeReference<List<CompareRecord>>() {});
        reload("compare-schedules.json", "QL_COMPARE_SCHEDULE", CompareSchedule::getId, CompareSchedule::getName,
                CompareSchedule::getRuleId, CompareSchedule::getCreatedAt, new TypeReference<List<CompareSchedule>>() {});
    }

    /** JSON 文件不存在时返回 null，表示"不覆盖 H2 现有数据"；存在但解析失败返回空列表 */
    private <T> List<T> read(String file, TypeReference<List<T>> type) {
        Path f = dataDir.resolve(file);
        if (!Files.exists(f)) return null;
        try {
            String json = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
            return JsonUtils.mapper().readValue(json, type);
        } catch (Exception e) {
            log.warn("读取 {} 失败，H2 镜像将为空: {}", f.toAbsolutePath(), e.getMessage());
            return List.of();
        }
    }

    /** 文件存在才用 JSON 覆盖 H2；不存在则保留 Store 预置的种子数据 */
    private <T> void reload(String file, String table, java.util.function.Function<T, String> idOf,
                            java.util.function.Function<T, String> nameOf,
                            java.util.function.Function<T, String> ruleIdOf,
                            java.util.function.ToLongFunction<T> sortOf,
                            TypeReference<List<T>> type) {
        List<T> items = read(file, type);
        if (items == null) {
            log.info("{} 不存在，保留 H2 {} 现有 {} 条", file, table, count(table));
            return;
        }
        jdbc.update("DELETE FROM " + table);
        for (T item : items) {
            String id = idOf.apply(item);
            if (id == null) continue;
            upsert(table, id, nameOf.apply(item), ruleIdOf.apply(item), sortOf.applyAsLong(item), item);
        }
        log.info("H2 镜像 {}: {} 条（来自 {}）", table, count(table), file);
    }

    private void upsert(String table, String id, String name, String ruleId, long sortTime, Object data) {
        try {
            jdbc.update("MERGE INTO " + table + "(ID,NAME,RULE_ID,SORT_TIME,DATA) KEY(ID) VALUES(?,?,?,?,?)",
                    id, name, ruleId, sortTime, JsonUtils.mapper().writeValueAsString(data));
        } catch (Exception e) {
            log.warn("加载数据到 {} 失败 [{}]: {}", table, id, e.getMessage());
        }
    }

    private int count(String table) {
        try {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return -1;
        }
    }

}

