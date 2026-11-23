package com.example.rulelog.service;

import com.example.rulelog.model.Script;
import com.example.rulelog.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 规则同步服务：把平台的 Script 同步到 QL_RULE 标准表，
 * 使 SDK（JdbcRuleLoader）和 HTTP API 能读到画布配好的规则。
 *
 * 同步失败不影响主流程（脚本仍存 JSON），仅记日志——QL_RULE 是消费副本。
 */
@Service
public class RuleSyncService {

    private static final Logger log = LoggerFactory.getLogger(RuleSyncService.class);
    private final JdbcTemplate jdbc;

    public RuleSyncService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 保存/更新规则到 QL_RULE（upsert） */
    public void sync(Script script) {
        if (script == null || script.getId() == null) return;
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM QL_RULE WHERE RULE_ID = ?", Integer.class, script.getId());
            String paramDef = extractParamDef(script);
            if (count != null && count > 0) {
                jdbc.update("UPDATE QL_RULE SET RULE_NAME=?, RULE_CONTENT=?, PARAM_DEF=?, VERSION=VERSION+1, "
                                + "ENABLED=?, DESCRIPTION=?, UPDATED_AT=? WHERE RULE_ID=?",
                        script.getName(), script.getContent(), paramDef,
                        true, script.getName(), System.currentTimeMillis(), script.getId());
            } else {
                jdbc.update("INSERT INTO QL_RULE(RULE_ID,RULE_NAME,RULE_CONTENT,PARAM_DEF,VERSION,ENABLED,"
                                + "DESCRIPTION,CREATED_AT,UPDATED_AT) VALUES(?,?,?,?,?,?,?,?,?)",
                        script.getId(), script.getName(), script.getContent(), paramDef,
                        1, true, script.getName(), System.currentTimeMillis(), System.currentTimeMillis());
            }
        } catch (Exception e) {
            log.warn("同步规则到 QL_RULE 失败 [{}]: {}", script.getId(), e.getMessage());
        }
    }

    /** 删除规则 */
    public void delete(String ruleId) {
        try {
            jdbc.update("DELETE FROM QL_RULE WHERE RULE_ID=?", ruleId);
        } catch (Exception e) {
            log.warn("删除 QL_RULE 失败 [{}]: {}", ruleId, e.getMessage());
        }
    }

    /**
     * 从 Script.meta 提取参数契约 JSON。
     * 优先 meta.params（未来画布入参定义），否则用 defaultParams 的键生成简单 ParamDef。
     */
    @SuppressWarnings("unchecked")
    private String extractParamDef(Script script) {
        try {
            Map<String, Object> meta = script.getMeta();
            if (meta != null && meta.get("params") instanceof List) {
                return JsonUtils.mapper().writeValueAsString(meta.get("params"));
            }
        } catch (Exception e) {
            // ignore
        }
        // 兜底：用默认参数键生成简单字符串参数定义
        try {
            List<Map<String, Object>> defs = new java.util.ArrayList<>();
            if (script.getDefaultParams() != null) {
                for (String key : script.getDefaultParams().keySet()) {
                    Map<String, Object> d = new java.util.LinkedHashMap<>();
                    d.put("name", key);
                    d.put("label", key);
                    d.put("type", "string");
                    d.put("required", false);
                    defs.add(d);
                }
            }
            return JsonUtils.mapper().writeValueAsString(defs);
        } catch (Exception e) {
            return "[]";
        }
    }
}
