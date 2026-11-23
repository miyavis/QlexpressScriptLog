package com.example.rulelog.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 可选 API Token 鉴权测试：
 * - 未配置 token → 放行（保持本地/Demo 零配置可用）
 * - 配置 token → 必须带正确 X-Api-Token，否则 401
 */
class ApiTokenFilterTest {

    @Test
    void noTokenConfiguredAllowsAll() throws Exception {
        ApiTokenFilter filter = new ApiTokenFilter("");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/scripts");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest(), "应放行到控制器");
    }

    @Test
    void configuredTokenRequiresMatchingHeader() throws Exception {
        ApiTokenFilter filter = new ApiTokenFilter("secret123");

        // 不带 token → 401
        MockHttpServletRequest noToken = new MockHttpServletRequest("GET", "/api/scripts");
        MockHttpServletResponse denied = new MockHttpServletResponse();
        filter.doFilter(noToken, denied, new MockFilterChain());
        assertEquals(401, denied.getStatus());

        // token 错误 → 401
        MockHttpServletRequest wrong = new MockHttpServletRequest("GET", "/api/scripts");
        wrong.addHeader("X-Api-Token", "wrong");
        MockHttpServletResponse denied2 = new MockHttpServletResponse();
        filter.doFilter(wrong, denied2, new MockFilterChain());
        assertEquals(401, denied2.getStatus());

        // token 正确 → 放行
        MockHttpServletRequest ok = new MockHttpServletRequest("GET", "/api/scripts");
        ok.addHeader("X-Api-Token", "secret123");
        MockHttpServletResponse passed = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(ok, passed, chain);
        assertEquals(200, passed.getStatus());
        assertNotNull(chain.getRequest());
    }
}
