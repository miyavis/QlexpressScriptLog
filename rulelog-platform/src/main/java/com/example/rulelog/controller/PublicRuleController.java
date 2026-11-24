package com.example.rulelog.controller;

import com.example.rulelog.engine.Result;
import com.example.rulelog.engine.RuleEngine;
import com.example.rulelog.loader.JdbcRuleLoader;
import com.example.rulelog.loader.RuleDefinition;
import com.example.rulelog.model.RuleExecutionRecord;
import com.example.rulelog.param.ParamDef;
import com.example.rulelog.param.ParamValidator;
import com.example.rulelog.service.RuleExecutionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.*;

/**
 * 对外公开 API（供外部系统简单集成）：
 * - GET  /api/public/rules                 规则列表
 * - GET  /api/public/rules/{id}/contract   参数契约（含中文名/嵌套）
 * - POST /api/public/run                   执行规则 → {success, result, traceId}
 *
 * 基于 QL_RULE 标准表 + core 引擎（JdbcRuleLoader），与平台画布配的规则互通。
 */
@RestController
@RequestMapping("/api/public")
public class PublicRuleController {

    private final JdbcRuleLoader loader;
    private final RuleEngine engine;
    private final JdbcTemplate jdbc;
    private final RuleExecutionService executionService;

    public PublicRuleController(DataSource dataSource, JdbcTemplate jdbc, RuleExecutionService executionService) {
        this.loader = JdbcRuleLoader.builder().dataSource(dataSource).build();
        this.engine = RuleEngine.builder().loader(loader).build();
        this.jdbc = jdbc;
        // 执行走平台执行服务：自动带上注解注册的中文业务函数、StepLogger 调用链与 traceId
        this.executionService = executionService;
    }

    /** 规则列表 */
    @GetMapping("/rules")
    public List<Map<String, Object>> listRules() {
        return jdbc.queryForList(
                "SELECT RULE_ID, RULE_NAME, VERSION, DESCRIPTION FROM QL_RULE WHERE ENABLED=TRUE ORDER BY RULE_ID")
                .stream().map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", row.get("RULE_ID"));
                    m.put("name", row.get("RULE_NAME"));
                    m.put("version", row.get("VERSION"));
                    m.put("description", row.get("DESCRIPTION"));
                    return m;
                }).toList();
    }

    /** 参数契约 */
    @GetMapping("/rules/{id}/contract")
    public Map<String, Object> contract(@PathVariable String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        RuleDefinition def = loader.load(id);
        if (def == null) {
            out.put("error", "规则不存在: " + id);
            return out;
        }
        out.put("rule", def.getId());
        out.put("name", def.getName());
        out.put("params", def.getParams());
        return out;
    }

    /** 执行规则 */
    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        String ruleId = body.get("rule") == null ? null : String.valueOf(body.get("rule"));
        if (ruleId == null || ruleId.trim().isEmpty()) {
            out.put("success", false);
            out.put("error", "缺少 rule 参数");
            return out;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.get("params") instanceof Map
                ? (Map<String, Object>) body.get("params") : Collections.emptyMap();

        // 从 QL_RULE 加载规则定义（含参数契约）
        RuleDefinition def = loader.load(ruleId.trim());
        if (def == null) {
            out.put("success", false);
            out.put("error", "规则不存在: " + ruleId);
            return out;
        }
        List<String> errors = ParamValidator.validate(def.getParams(), params);
        if (!errors.isEmpty()) {
            out.put("success", false);
            out.put("error", "参数校验失败: " + String.join("; ", errors));
            return out;
        }
        // 用平台执行服务执行：业务函数已由 @RuleFunction 注解注册，并生成可下钻的执行记录
        try {
            RuleExecutionRecord rec = executionService.runByContent(def.getId(), def.getContent(), params);
            out.put("success", rec.isSuccess());
            out.put("result", rec.getResult());
            out.put("traceId", rec.getTraceId());
            if (!rec.isSuccess()) out.put("error", rec.getErrorMessage());
        } catch (Exception e) {
            out.put("success", false);
            out.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
        }
        return out;
    }
}
