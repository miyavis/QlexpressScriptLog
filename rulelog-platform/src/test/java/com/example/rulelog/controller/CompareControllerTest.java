package com.example.rulelog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 比对画布 API 集成测试：内置样例规则可直接运行并产出不一致结论。 */
@SpringBootTest
@AutoConfigureMockMvc
class CompareControllerTest {

    @Autowired private MockMvc mvc;

    @Test
    void sampleRuleRunsAndReportsDifference() throws Exception {
        // 列表含内置示例
        mvc.perform(get("/api/compare-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cmp_sample"));

        // 运行示例（公开接口）：O001 金额 100 vs 103 超容差 → 不一致
        mvc.perform(post("/api/compare-rules/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleId\":\"cmp_sample\",\"context\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.inconsistentRows").value(1))
                .andExpect(jsonPath("$.rows[0].consistent").value(false))
                .andExpect(jsonPath("$.rows[0].cells.金额.consistent").value(false))
                .andExpect(jsonPath("$.rows[1].consistent").value(true));
    }

    @Test
    void crudRoundTrip() throws Exception {
        String body = "{\"name\":\"测试比对\",\"fetchNodes\":[],\"compareNode\":{\"keyField\":\"id\",\"sourceKeys\":[\"A\",\"B\"],\"fields\":[]}}";
        // 新建（需登录）
        String created = mvc.perform(post("/api/compare-rules")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        // 单条
        mvc.perform(get("/api/compare-rules/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("测试比对"));

        // 删除
        mvc.perform(delete("/api/compare-rules/{id}", id)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
}
