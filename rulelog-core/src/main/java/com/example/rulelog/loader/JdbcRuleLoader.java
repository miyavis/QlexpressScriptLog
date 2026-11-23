package com.example.rulelog.loader;

import com.example.rulelog.param.ParamDef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 规则加载器：从 QL_RULE 表加载规则（像 Quartz 的 JDBCJobStore）。
 *
 * - 表前缀可配（默认 QL_）
 * - 缓存 + TTL（默认 30s），过期自动重查；refresh(ruleId) 手动清缓存
 * - 仅用 JDK 原生 JDBC（javax.sql.DataSource），不依赖 Spring
 */
public class JdbcRuleLoader implements RuleLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DataSource dataSource;
    private final String tablePrefix;
    private final long cacheTtlMillis;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static final class CacheEntry {
        final RuleDefinition def;
        final long loadedAt;
        CacheEntry(RuleDefinition def, long loadedAt) { this.def = def; this.loadedAt = loadedAt; }
        boolean expired(long ttl) { return System.currentTimeMillis() - loadedAt > ttl; }
    }

    private JdbcRuleLoader(DataSource dataSource, String tablePrefix, long cacheTtlMillis) {
        this.dataSource = dataSource;
        this.tablePrefix = tablePrefix;
        this.cacheTtlMillis = cacheTtlMillis;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private DataSource dataSource;
        private String tablePrefix = "QL_";
        private long cacheTtlMillis = 30_000L;

        public Builder dataSource(DataSource ds) { this.dataSource = ds; return this; }
        public Builder tablePrefix(String prefix) { this.tablePrefix = prefix; return this; }
        public Builder cacheTtlMillis(long ttl) { this.cacheTtlMillis = ttl; return this; }
        public JdbcRuleLoader build() {
            if (dataSource == null) throw new IllegalArgumentException("dataSource 不能为空");
            return new JdbcRuleLoader(dataSource, tablePrefix, cacheTtlMillis);
        }
    }

    @Override
    public RuleDefinition load(String ruleId) {
        CacheEntry cached = cache.get(ruleId);
        if (cached != null && !cached.expired(cacheTtlMillis)) {
            return cached.def;
        }
        RuleDefinition def = loadFromDb(ruleId);
        if (def != null) {
            cache.put(ruleId, new CacheEntry(def, System.currentTimeMillis()));
        } else {
            cache.remove(ruleId);
        }
        return def;
    }

    @Override
    public List<String> listRuleIds() {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT RULE_ID FROM " + tablePrefix + "RULE WHERE ENABLED = TRUE ORDER BY RULE_ID";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) ids.add(rs.getString(1));
        } catch (Exception e) {
            throw new RuntimeException("列出规则失败: " + e.getMessage(), e);
        }
        return ids;
    }

    @Override
    public void refresh(String ruleId) {
        cache.remove(ruleId);
    }

    private RuleDefinition loadFromDb(String ruleId) {
        String sql = "SELECT RULE_ID, RULE_NAME, RULE_CONTENT, PARAM_DEF, VERSION, ENABLED, DESCRIPTION "
                + "FROM " + tablePrefix + "RULE WHERE RULE_ID = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ruleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                RuleDefinition def = new RuleDefinition();
                def.setId(rs.getString("RULE_ID"));
                def.setName(rs.getString("RULE_NAME"));
                def.setContent(rs.getString("RULE_CONTENT"));
                def.setVersion(rs.getInt("VERSION"));
                def.setEnabled(rs.getBoolean("ENABLED"));
                def.setDescription(rs.getString("DESCRIPTION"));
                String paramDef = rs.getString("PARAM_DEF");
                if (paramDef != null && !paramDef.trim().isEmpty()) {
                    def.setParams(parseParams(paramDef));
                }
                return def;
            }
        } catch (Exception e) {
            throw new RuntimeException("加载规则失败 [" + ruleId + "]: " + e.getMessage(), e);
        }
    }

    private List<ParamDef> parseParams(String json) {
        List<ParamDef> out = new ArrayList<>();
        try {
            JsonNode arr = MAPPER.readTree(json);
            if (arr.isArray()) {
                for (JsonNode p : arr) out.add(parseParam(p));
            }
        } catch (Exception e) {
            // 解析失败返回空参数定义
        }
        return out;
    }

    private ParamDef parseParam(JsonNode node) {
        ParamDef def = new ParamDef(
                node.has("name") ? node.get("name").asText() : "",
                node.has("label") ? node.get("label").asText() : "",
                node.has("type") ? node.get("type").asText() : "string");
        if (node.has("desc")) def.setDesc(node.get("desc").asText());
        if (node.has("required")) def.setRequired(node.get("required").asBoolean());
        if (node.has("fields") && node.get("fields").isArray()) {
            List<ParamDef> children = new ArrayList<>();
            for (JsonNode c : node.get("fields")) children.add(parseParam(c));
            def.setFields(children);
        }
        if (node.has("element") && node.get("element").isObject()) {
            def.setElement(parseParam(node.get("element")));
        }
        return def;
    }
}
