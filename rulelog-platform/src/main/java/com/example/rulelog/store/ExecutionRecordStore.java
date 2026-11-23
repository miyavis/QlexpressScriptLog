package com.example.rulelog.store;

import com.example.rulelog.model.RuleExecutionRecord;
import com.example.rulelog.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * 执行记录库：内存 LinkedHashMap + 落盘 JSON（data/executions.json）。
 *
 * 最小持久化方案：重启后记录不丢，但不引入 DB / RBAC / 环境隔离（超出 Demo 定位）。
 * LinkedHashMap 维护插入顺序，供"按时间倒序的执行记录列表"使用。
 */
@Component
public class ExecutionRecordStore {

    private static final Logger log = LoggerFactory.getLogger(ExecutionRecordStore.class);

    private final int maxRecords;
    private final Path storeFile;
    private final boolean persistEnabled;

    private final Map<String, RuleExecutionRecord> records = new LinkedHashMap<>();

    /** Spring 构造：数据目录来自配置，相对路径锚定应用所在目录；测试可直接传绝对路径 */
    @Autowired
    public ExecutionRecordStore(@Value("${app.max-execution-records:200}") int maxRecords,
                                @Value("${app.data-dir:data}") String dataDir) {
        this(maxRecords, resolveDataDir(dataDir).toString(), true);
    }

    /** 测试构造（内存模式，不落盘） */
    public ExecutionRecordStore(int maxRecords) {
        this(maxRecords, "", false);
    }

    private ExecutionRecordStore(int maxRecords, String dir, boolean persistEnabled) {
        this.maxRecords = maxRecords;
        this.persistEnabled = persistEnabled;
        this.storeFile = dir == null || dir.isEmpty() ? null : Paths.get(dir).resolve("executions.json");
        if (persistEnabled) load();
    }

    public void save(RuleExecutionRecord record) {
        synchronized (records) {
            records.put(record.getTraceId(), record);
            while (records.size() > maxRecords) {
                String oldestKey = records.keySet().iterator().next();
                records.remove(oldestKey);
            }
            persist();
        }
    }

    /** 清空所有执行记录（含落盘文件） */
    public void clear() {
        synchronized (records) {
            records.clear();
            persist();
        }
    }

    /** 最近优先的列表 */
    public List<RuleExecutionRecord> listRecent(int limit) {
        synchronized (records) {
            List<RuleExecutionRecord> all = new ArrayList<>(records.values());
            List<RuleExecutionRecord> result = new ArrayList<>();
            for (int i = all.size() - 1; i >= 0 && result.size() < limit; i--) {
                result.add(all.get(i));
            }
            return result;
        }
    }

    public RuleExecutionRecord get(String traceId) {
        synchronized (records) {
            return records.get(traceId);
        }
    }

    /** 查询某脚本的所有执行记录（最近优先）；循环外只取一次快照 */
    public List<RuleExecutionRecord> listByScript(String scriptId, int limit) {
        synchronized (records) {
            List<RuleExecutionRecord> all = new ArrayList<>(records.values());
            List<RuleExecutionRecord> result = new ArrayList<>();
            for (int i = all.size() - 1; i >= 0 && result.size() < limit; i--) {
                RuleExecutionRecord r = all.get(i);
                if (scriptId.equals(r.getScriptId())) {
                    result.add(r);
                }
            }
            return result;
        }
    }

    /** 从磁盘加载；文件不存在或损坏时以空库启动（不让启动崩溃） */
    private void load() {
        if (!persistEnabled || storeFile == null || !Files.exists(storeFile)) return;
        try {
            List<RuleExecutionRecord> loaded = JsonUtils.mapper().readValue(
                    storeFile.toFile(), new TypeReference<List<RuleExecutionRecord>>() {
                    });
            synchronized (records) {
                records.clear();
                for (RuleExecutionRecord r : loaded) {
                    if (r != null && r.getTraceId() != null) {
                        records.put(r.getTraceId(), r);
                    }
                }
                // 兼容容量配置变化：超限时丢弃最早记录
                while (records.size() > maxRecords) {
                    records.remove(records.keySet().iterator().next());
                }
            }
            log.info("已加载 {} 条执行记录: {}", records.size(), storeFile.toAbsolutePath());
        } catch (Exception e) {
            log.warn("执行记录文件读取失败，以空库启动: {} ({})", storeFile.toAbsolutePath(), e.getMessage());
        }
    }

    /** 原子写：临时文件 + ATOMIC_MOVE，避免写一半崩溃留下半截 JSON */
    private void persist() {
        if (!persistEnabled || storeFile == null) return;
        try {
            Files.createDirectories(storeFile.getParent());
            Path tmp = Files.createTempFile(storeFile.getParent(), "executions-", ".tmp");
            try {
                String json = JsonUtils.pretty(new ArrayList<>(records.values()));
                Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(tmp, storeFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tmp, storeFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("保存执行记录失败: " + storeFile.toAbsolutePath(), e);
        }
    }

    private static Path resolveDataDir(String dataDir) {
        Path configured = Paths.get(dataDir);
        if (configured.isAbsolute()) return configured;
        try {
            org.springframework.boot.system.ApplicationHome home =
                    new org.springframework.boot.system.ApplicationHome(ExecutionRecordStore.class);
            Path dir = home.getDir() == null ? null : home.getDir().toPath();
            if (dir != null) return dir.resolve(configured).normalize();
        } catch (Exception e) {
            // 降级到当前工作目录
        }
        return Paths.get(dataDir).toAbsolutePath().normalize();
    }
}
