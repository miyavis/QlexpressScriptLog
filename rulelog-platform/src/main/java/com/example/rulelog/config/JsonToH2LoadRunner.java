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
        loadScripts();
        loadExecutions();
        loadCompareRules();
        loadCompareRecords();
        loadCompareSchedules();
    }

    private <T> List<T> read(String file, TypeReference<List<T>> type) {
        Path f = dataDir.resolve(file);
        if (!Files.exists(f)) return List.of();
        try {
            String json = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
            return JsonUtils.mapper().readValue(json, type);
        } catch (Exception e) {
            log.warn("读取 {} 失败，H2 镜像将为空: {}", f.toAbsolutePath(), e.getMessage());
            return List.of();
        }
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

    private void loadScripts() {
        // 启动时先清镜像，避免 JSON 里已删除的记录残留
        jdbc.update("DELETE FROM QL_SCRIPT");
        for (Script s : read("scripts.json", new TypeReference<List<Script>>() {})) {
            if (s.getId() == null) continue;
            upsert("QL_SCRIPT", s.getId(), s.getName(), null, s.getUpdatedAt(), s);
        }
        log.info("H2 镜像 QL_SCRIPT: {} 条", count("QL_SCRIPT"));
    }

    private void loadExecutions() {
        jdbc.update("DELETE FROM QL_EXECUTION");
        for (RuleExecutionRecord r : read("executions.json", new TypeReference<List<RuleExecutionRecord>>() {})) {
            if (r.getTraceId() == null) continue;
            upsert("QL_EXECUTION", r.getTraceId(), null, r.getScriptId(), r.getStartTime(), r);
        }
        log.info("H2 镜像 QL_EXECUTION: {} 条", count("QL_EXECUTION"));
    }

    private void loadCompareRules() {
        jdbc.update("DELETE FROM QL_COMPARE_RULE");
        for (CompareRule r : read("compare-rules.json", new TypeReference<List<CompareRule>>() {})) {
            if (r.getId() == null) continue;
            upsert("QL_COMPARE_RULE", r.getId(), r.getName(), null, r.getUpdatedAt(), r);
        }
        log.info("H2 镜像 QL_COMPARE_RULE: {} 条", count("QL_COMPARE_RULE"));
    }

    private void loadCompareRecords() {
        jdbc.update("DELETE FROM QL_COMPARE_RECORD");
        for (CompareRecord r : read("compare-records.json", new TypeReference<List<CompareRecord>>() {})) {
            if (r.getId() == null) continue;
            upsert("QL_COMPARE_RECORD", r.getId(), r.getRuleName(), r.getRuleId(), r.getSavedAt(), r);
        }
        log.info("H2 镜像 QL_COMPARE_RECORD: {} 条", count("QL_COMPARE_RECORD"));
    }

    private void loadCompareSchedules() {
        jdbc.update("DELETE FROM QL_COMPARE_SCHEDULE");
        for (CompareSchedule s : read("compare-schedules.json", new TypeReference<List<CompareSchedule>>() {})) {
            if (s.getId() == null) continue;
            upsert("QL_COMPARE_SCHEDULE", s.getId(), s.getName(), s.getRuleId(), s.getCreatedAt(), s);
        }
        log.info("H2 镜像 QL_COMPARE_SCHEDULE: {} 条", count("QL_COMPARE_SCHEDULE"));
    }
}
