package com.example.rulelog.controller;

import com.example.rulelog.store.ScriptStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 对外 API 集成测试：平台保存 → QL_RULE 同步 → 公开 API 列表/契约/执行。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicRuleApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Autowired private com.example.rulelog.store.ScriptStore scriptStore;

    @Test
    void executionCapturesVariablesForDownstreamCompare() throws Exception {
        // 取数卡产出变量，比对卡消费它（同一条执行记录）
        com.example.rulelog.model.Script script = new com.example.rulelog.model.Script();
        script.setId("var_compare_test");
        script.setName("变量传递测试");
        script.setContent("积分 = 查询用户积分(user);\n返回 \"完成\";");
        java.util.Map<String, Object> compareNode = new java.util.LinkedHashMap<>();
        compareNode.put("keyField", "value");
        compareNode.put("sourceKeys", java.util.Arrays.asList("A", "B"));
        java.util.Map<String, Object> field = new java.util.LinkedHashMap<>();
        field.put("field", "value"); field.put("label", "值"); field.put("strategy", "equal");
        compareNode.put("fields", java.util.Collections.singletonList(field));
        java.util.Map<String, Object> rule = new java.util.LinkedHashMap<>();
        java.util.Map<String, Object> a = new java.util.LinkedHashMap<>();
        a.put("id", "a"); a.put("name", "A"); a.put("sourceType", "CONTEXT");
        a.put("sourceName", "积分"); a.put("resultKey", "A");
        java.util.Map<String, Object> b = new java.util.LinkedHashMap<>();
        b.put("id", "b"); b.put("name", "B"); b.put("sourceType", "CONST");
        b.put("sourceName", "380"); b.put("resultKey", "B");
        rule.put("fetchNodes", java.util.Arrays.asList(a, b));
        rule.put("compareNode", compareNode);
        java.util.Map<String, Object> card = new java.util.LinkedHashMap<>();
        card.put("id", "c1"); card.put("type", "compare"); card.put("compareRule", rule);
        java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("cards", java.util.Collections.singletonList(card));
        java.util.Map<String, Object> span = new java.util.LinkedHashMap<>();
        span.put("cardId", "c1"); span.put("type", "compare");
        span.put("startLine", 2); span.put("endLine", 2);
        meta.put("cardSourceMap", java.util.Collections.singletonList(span));
        script.setMeta(meta);
        scriptStore.put(script);

        mvc.perform(post("/api/executions/run?scriptId=var_compare_test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\":\"张三\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variables.积分").value(380))
                .andExpect(jsonPath("$.rootSteps[0].serviceName").value("比对引擎"))
                .andExpect(jsonPath("$.lineExecutions['2'].steps[0].serviceName").value("比对引擎"))
                .andExpect(jsonPath("$.lineExecutions['2'].steps[1].stepName").value("比对明细"))
                .andExpect(jsonPath("$.lineExecutions['2'].steps[1].detail").value(org.hamcrest.Matchers.containsString("A={value=")));
    }

    @Test
    void publicRunSupportsBusinessFunctions() throws Exception {
        // 公开 API 执行带中文业务函数的规则（此前会因函数未注册而失败）
        mvc.perform(post("/api/public/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rule\":\"points_double\",\"params\":{\"user\":\"张三\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result").value(760))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void allJsonDataIsLoadedIntoH2OnStartup() throws Exception {
        mvc.perform(get("/api/public/data-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.QL_RULE").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.QL_SCRIPT").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.QL_COMPARE_RULE").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.QL_COMPARE_RECORD").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.QL_COMPARE_SCHEDULE").exists());
    }

    @Test
    void seedScriptsAreMigratedOnStartup() throws Exception {
        // 启动迁移：内置示例脚本（4 条）开箱即可从公开 API 读到
        mvc.perform(get("/api/public/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$[?(@.id == 'vip_discount')]").exists());
    }

    @Test
    void saveSyncsToPublicApiAndCanRun() throws Exception {
        String ruleId = "pub_test_" + System.nanoTime();
        // 1. 平台保存脚本（admin 认证）
        mvc.perform(post("/api/scripts")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"公开测试\",\"content\":\"返回 1;\",\"defaultParams\":{\"user\":\"张三\"}}"))
                .andExpect(status().isOk());

        // 2. 直接插入一条已知 ID 的规则到 QL_RULE，验证公开 API 读取（避免依赖 create 生成的随机 ID）
        jdbc.update("MERGE INTO QL_RULE(RULE_ID,RULE_NAME,RULE_CONTENT,PARAM_DEF,VERSION,ENABLED) "
                        + "KEY(RULE_ID) VALUES(?,?,?,?,1,TRUE)",
                ruleId, "公开规则", "返回 42;",
                "[{\"name\":\"user\",\"label\":\"用户\",\"type\":\"string\"}]");

        // 3. 公开列表（无认证）
        mvc.perform(get("/api/public/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + ruleId + "')]").exists());

        // 4. 契约（无认证，含中文参数名）
        mvc.perform(get("/api/public/rules/{id}/contract", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.params[0].label").value("用户"));

        // 5. 公开执行（无认证）
        mvc.perform(post("/api/public/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rule\":\"" + ruleId + "\",\"params\":{\"user\":\"张三\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result").value(42))
                .andExpect(jsonPath("$.traceId").exists());
    }
}
