package com.example.rulelog.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 可选 API Token 鉴权（公网 Demo 的安全兜底）：
 * - 未配置 app.api-token（默认空）→ 全部放行，保持本地/演示零配置可用；
 * - 配置后 → /api/* 请求必须带 X-Api-Token 头且值匹配，否则 401。
 *
 * 这样既不在默认 Demo 上挡住访问，又给生产部署留了开关（配置一个 token 即启用）。
 */
@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    private final String configuredToken;

    /** 运行时由 Spring 注入配置；测试用构造函数直接传值 */
    public ApiTokenFilter(@Value("${app.api-token:}") String configuredToken) {
        this.configuredToken = configuredToken == null ? "" : configuredToken.trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (configuredToken.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        // 仅保护 API；前端静态资源放行（否则页面打不开）
        if (path != null && path.startsWith("/api/")) {
            String provided = request.getHeader("X-Api-Token");
            if (!configuredToken.equals(provided)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"未授权：请提供有效的 X-Api-Token\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
