package com.example.rulelog.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC 权限测试：开启 Security 后 viewer 只能读（GET），不能写（POST/PUT/DELETE）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.security.enabled=true")
class SecurityRbacTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void viewerCanReadButCannotWrite() throws Exception {
        // viewer 读脚本列表 → 200
        mvc.perform(get("/api/scripts").with(httpBasic("viewer", "viewer123")))
                .andExpect(status().isOk());
        // viewer 写（新建脚本）→ 403
        mvc.perform(post("/api/scripts")
                        .with(httpBasic("viewer", "viewer123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"content\":\"返回 1;\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanWrite() throws Exception {
        mvc.perform(post("/api/scripts")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"content\":\"返回 1;\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/scripts")).andExpect(status().isUnauthorized());
    }

}
