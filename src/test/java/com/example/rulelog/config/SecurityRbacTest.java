package com.example.rulelog.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * 公开 Demo 的权限测试：读公开、执行公开、写需登录、审计仅管理员。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRbacTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void getsArePublic() throws Exception {
        mvc.perform(get("/api/scripts")).andExpect(status().isOk());
        mvc.perform(get("/api/executions")).andExpect(status().isOk());
    }

    @Test
    void executionEndpointsArePublic() throws Exception {
        // 静态检查（POST）无需登录
        mvc.perform(post("/api/executions/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"返回 1;\",\"paramKeys\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    void writesRequireAuthentication() throws Exception {
        // 匿名写 → 401
        mvc.perform(post("/api/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"content\":\"返回 1;\"}"))
                .andExpect(status().isUnauthorized());
        // viewer 写 → 允许（防匿名，已登录即可）
        mvc.perform(post("/api/scripts")
                        .with(httpBasic("viewer", "viewer123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"content\":\"返回 1;\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void auditIsAdminOnly() throws Exception {
        mvc.perform(get("/api/audit")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/audit").with(httpBasic("viewer", "viewer123")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorizedResponseHasNoWwwAuthenticateHeader() throws Exception {
        // 关键：401 绝不能带 WWW-Authenticate，否则浏览器弹原生登录框（绕过自定义登录 UI）
        mvc.perform(post("/api/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"content\":\"返回 1;\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }
}
