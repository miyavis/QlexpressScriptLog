package com.example.rulelog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * /api/executions/check 接口测试：语法错误/正常/输入边界。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExecutionControllerCheckTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void checkValidScriptReturnsValid() throws Exception {
        mvc.perform(post("/api/executions/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"返回 1;\",\"paramKeys\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void checkSyntaxErrorReturnsInvalid() throws Exception {
        mvc.perform(post("/api/executions/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"返回 1 +;\",\"paramKeys\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void emptyBodyReturnsInvalidNotCrash() throws Exception {
        mvc.perform(post("/api/executions/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }
}
