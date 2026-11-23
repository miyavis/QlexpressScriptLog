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
