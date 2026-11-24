package com.example.rulelog.store;

import com.example.rulelog.util.JsonUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用 H2 JSON 表访问器：对象整体序列化进 DATA CLOB，
 * 同时保留 ID / NAME / RULE_ID / SORT_TIME 列供过滤与排序。
 *
 * 各业务 Store 用它替换文件持久化，运行时读写都走 H2。
 */
public class H2JsonTable {

    private final JdbcTemplate jdbc;
    private final String table;

    public H2JsonTable(JdbcTemplate jdbc, String table, String ddl) {
        this.jdbc = jdbc;
        this.table = table;
        jdbc.execute(ddl);
    }

    public <T> T get(String id, Class<T> type) {
        if (id == null) return null;
        List<T> rows = jdbc.query("SELECT DATA FROM " + table + " WHERE ID=?",
                (rs, i) -> {
                    try {
                        return JsonUtils.mapper().readValue(rs.getString(1), type);
                    } catch (Exception e) {
                        throw new RuntimeException("反序列化 " + table + " 失败", e);
                    }
                }, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public <T> List<T> all(Class<T> type, int limit) {
        return query("SELECT DATA FROM " + table + " ORDER BY SORT_TIME DESC LIMIT " + Math.max(1, limit), type);
    }

    public <T> List<T> byRule(String ruleId, Class<T> type, int limit) {
        return query("SELECT DATA FROM " + table + " WHERE RULE_ID=? ORDER BY SORT_TIME DESC LIMIT " + Math.max(1, limit),
                type, ruleId);
    }

    public <T> List<T> between(String ruleId, Long from, Long to, Class<T> type, int limit) {
        StringBuilder sql = new StringBuilder("SELECT DATA FROM " + table + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (ruleId != null) { sql.append(" AND RULE_ID=?"); args.add(ruleId); }
        if (from != null) { sql.append(" AND SORT_TIME>=?"); args.add(from); }
        if (to != null) { sql.append(" AND SORT_TIME<=?"); args.add(to); }
        sql.append(" ORDER BY SORT_TIME DESC LIMIT ").append(Math.max(1, limit));
        return query(sql.toString(), type, args.toArray());
    }

    private <T> List<T> query(String sql, Class<T> type, Object... args) {
        return jdbc.query(sql, (rs, i) -> {
            try {
                return JsonUtils.mapper().readValue(rs.getString(1), type);
            } catch (Exception e) {
                throw new RuntimeException("反序列化 " + table + " 失败", e);
            }
        }, args);
    }

    public void upsert(String id, String name, String ruleId, long sortTime, Object obj) {
        try {
            String json = JsonUtils.mapper().writeValueAsString(obj);
            jdbc.update("MERGE INTO " + table + "(ID,NAME,RULE_ID,SORT_TIME,DATA) KEY(ID) VALUES(?,?,?,?,?)",
                    id, name, ruleId, sortTime, json);
        } catch (Exception e) {
            throw new RuntimeException("写入 " + table + " 失败 [" + id + "]", e);
        }
    }

    /** 全部 ID（SORT_TIME 升序，供保留上限裁剪） */
    public List<String> getAllIds() {
        return jdbc.queryForList("SELECT ID FROM " + table + " ORDER BY SORT_TIME ASC", String.class);
    }

    public boolean delete(String id) {
        return jdbc.update("DELETE FROM " + table + " WHERE ID=?", id) > 0;
    }

    public void clear() {
        jdbc.update("DELETE FROM " + table);
    }

    public int count() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
