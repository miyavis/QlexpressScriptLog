package com.example.rulelog.store;

import com.example.rulelog.compare.CompareSchedule;
import com.example.rulelog.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 比对定时任务持久化：data/compare-schedules.json。 */
@Component
public class CompareScheduleStore {

    private static final Logger log = LoggerFactory.getLogger(CompareScheduleStore.class);
    private final Path storeFile;
    private final Map<String, CompareSchedule> schedules = new LinkedHashMap<>();

    public CompareScheduleStore(@Value("${app.data-dir:data}") String dataDir) {
        this.storeFile = resolve(dataDir).resolve("compare-schedules.json");
        load();
    }

    private Path resolve(String dataDir) {
        Path configured = Paths.get(dataDir);
        if (configured.isAbsolute()) return configured;
        try {
            org.springframework.boot.system.ApplicationHome home =
                    new org.springframework.boot.system.ApplicationHome(CompareScheduleStore.class);
            Path dir = home.getDir() == null ? null : home.getDir().toPath();
            if (dir != null) return dir.resolve(configured).normalize();
        } catch (Exception e) { /* ignore */ }
        return Paths.get("").toAbsolutePath().resolve(configured).normalize();
    }

    private void load() {
        try {
            if (Files.exists(storeFile)) {
                String json = new String(Files.readAllBytes(storeFile), StandardCharsets.UTF_8);
                List<CompareSchedule> list = JsonUtils.mapper().readValue(json, new TypeReference<List<CompareSchedule>>() {});
                for (CompareSchedule s : list) schedules.put(s.getId(), s);
                log.info("加载比对定时任务 {} 条", schedules.size());
            }
        } catch (Exception e) {
            log.warn("读取定时任务失败，使用空列表: {}", e.getMessage());
        }
    }

    public List<CompareSchedule> list() {
        return new ArrayList<>(schedules.values());
    }

    public List<CompareSchedule> listByRule(String ruleId) {
        List<CompareSchedule> out = new ArrayList<>();
        for (CompareSchedule s : schedules.values()) {
            if (ruleId.equals(s.getRuleId())) out.add(s);
        }
        return out;
    }

    public CompareSchedule get(String id) { return schedules.get(id); }

    public CompareSchedule put(CompareSchedule s) {
        if (s.getId() == null || s.getId().trim().isEmpty()) {
            s.setId("sched_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        if (s.getCreatedAt() == 0) s.setCreatedAt(System.currentTimeMillis());
        schedules.put(s.getId(), s);
        persist();
        return s;
    }

    public boolean remove(String id) {
        boolean removed = schedules.remove(id) != null;
        if (removed) persist();
        return removed;
    }

    private void persist() {
        try {
            Files.createDirectories(storeFile.getParent());
            Path tmp = Files.createTempFile(storeFile.getParent(), "sched-", ".tmp");
            try {
                Files.write(tmp, JsonUtils.mapper().writeValueAsBytes(new ArrayList<>(schedules.values())));
                try {
                    Files.move(tmp, storeFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    Files.move(tmp, storeFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("保存定时任务失败: " + storeFile.toAbsolutePath(), e);
        }
    }
}
