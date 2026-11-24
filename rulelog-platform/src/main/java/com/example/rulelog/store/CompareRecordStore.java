package com.example.rulelog.store;

import com.example.rulelog.compare.CompareRecord;
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
import java.util.Collections;
import java.util.List;

/** 比对执行历史：JSON 文件（data/compare-records.json），最多保留 200 条。 */
@Component
public class CompareRecordStore {

    private static final Logger log = LoggerFactory.getLogger(CompareRecordStore.class);
    private static final int MAX = 200;
    private final Path storeFile;
    private final List<CompareRecord> records = new ArrayList<>();

    public CompareRecordStore(@Value("${app.data-dir:data}") String dataDir) {
        this.storeFile = resolve(dataDir).resolve("compare-records.json");
        load();
    }

    private Path resolve(String dataDir) {
        Path configured = Paths.get(dataDir);
        if (configured.isAbsolute()) return configured;
        try {
            org.springframework.boot.system.ApplicationHome home =
                    new org.springframework.boot.system.ApplicationHome(CompareRecordStore.class);
            Path dir = home.getDir() == null ? null : home.getDir().toPath();
            if (dir != null) return dir.resolve(configured).normalize();
        } catch (Exception e) {
            // ignore
        }
        return Paths.get("").toAbsolutePath().resolve(configured).normalize();
    }

    private void load() {
        try {
            if (Files.exists(storeFile)) {
                String json = new String(Files.readAllBytes(storeFile), StandardCharsets.UTF_8);
                List<CompareRecord> list = JsonUtils.mapper().readValue(json, new TypeReference<List<CompareRecord>>() {});
                records.addAll(list);
                log.info("加载比对历史 {} 条: {}", records.size(), storeFile.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("读取比对历史失败，使用空列表: {}", e.getMessage());
        }
    }

    /** 追加一条记录（最新在前，超出上限截断） */
    public void append(CompareRecord record) {
        synchronized (records) {
            records.add(0, record);
            while (records.size() > MAX) records.remove(records.size() - 1);
            persist();
        }
    }

    /** 历史列表：可按 ruleId + 时间范围（epoch millis，含端点）过滤，按时间倒序 */
    public List<CompareRecord> list(String ruleId, int limit, Long from, Long to) {
        synchronized (records) {
            List<CompareRecord> out = new ArrayList<>();
            for (CompareRecord r : records) {
                if (ruleId != null && !ruleId.equals(r.getRuleId())) continue;
                if (from != null && r.getSavedAt() < from) continue;
                if (to != null && r.getSavedAt() > to) continue;
                out.add(r);
                if (out.size() >= limit) break;
            }
            return out;
        }
    }

    private void persist() {
        try {
            Files.createDirectories(storeFile.getParent());
            Path tmp = Files.createTempFile(storeFile.getParent(), "compare-rec-", ".tmp");
            try {
                Files.write(tmp, JsonUtils.mapper().writeValueAsBytes(records));
                try {
                    Files.move(tmp, storeFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    Files.move(tmp, storeFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("保存比对历史失败: " + storeFile.toAbsolutePath(), e);
        }
    }
}
