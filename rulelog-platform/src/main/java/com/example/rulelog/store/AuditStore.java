package com.example.rulelog.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 审计日志（H2 嵌入式数据库）：记录每次关键操作——谁、何时、什么动作、涉及哪个脚本。
 *
 * 与脚本/执行记录的 JSON 存储解耦：DB 只承担审计（追加写、只增不改），
 * 不替换现有持久化，避免破坏既有数据结构。生产可换成外部 DB。
 */
@Component
public class AuditStore {

    private static final Logger log = LoggerFactory.getLogger(AuditStore.class);
    private final JdbcTemplate jdbc;

    public AuditStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        init();
    }

    private void init() {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS audit_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "ts BIGINT NOT NULL, "
                    + "actor VARCHAR(64) NOT NULL, "
                    + "action VARCHAR(64) NOT NULL, "
                    + "target VARCHAR(128), "
                    + "detail VARCHAR(512))");
        } catch (Exception e) {
            log.warn("审计表初始化失败（审计功能降级）: {}", e.getMessage());
        }
    }

    /** 记录一条审计事件 */
    public void record(String actor, String action, String target, String detail) {
        try {
            jdbc.update("INSERT INTO audit_log(ts, actor, action, target, detail) VALUES (?,?,?,?,?)",
                    System.currentTimeMillis(),
                    actor == null ? "anonymous" : actor,
                    action == null ? "unknown" : action,
                    target, detail);
        } catch (Exception e) {
            log.warn("审计记录失败: {}", e.getMessage());
        }
    }

    /** 最近 N 条审计（新→旧） */
    public List<Map<String, Object>> recent(int limit) {
        try {
            return jdbc.queryForList("SELECT * FROM audit_log ORDER BY id DESC LIMIT " + Math.max(1, Math.min(limit, 500)));
        } catch (Exception e) {
            log.warn("审计查询失败: {}", e.getMessage());
            return List.of();
        }
    }
}
