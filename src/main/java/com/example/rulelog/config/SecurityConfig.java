package com.example.rulelog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security：公开 Demo 采用「读公开、写需登录」模式。
 *
 * - 默认 app.security.enabled=true：
 *   · GET 全部放行（任何人都能看脚本、执行记录、帮助）
 *   · 执行类 POST（run/rerun/check）放行——不修改持久数据，是 Demo 核心体验
 *   · 真正的写操作（脚本增删改、评审、发布、分支、清空记录）需认证
 *   · /api/audit 仅 ADMIN（含敏感操作记录）
 * - 本地开发可设 APP_SECURITY_ENABLED=false 完全免登录。
 *
 * 内置 admin / viewer 两个用户（密码可配）；生产应改默认密码并接外部用户源。
 */
@Configuration
public class SecurityConfig {

    @Value("${app.security.enabled:true}")
    private boolean securityEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        if (!securityEnabled) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
        http.authorizeHttpRequests(auth -> auth
                        // 审计仅管理员
                        .requestMatchers("/api/audit").hasRole("ADMIN")
                        // 执行类接口公开（不修改持久数据，Demo 核心体验）
                        .requestMatchers(HttpMethod.POST,
                                "/api/executions/run", "/api/executions/rerun", "/api/executions/check").permitAll()
                        // 其余写操作需认证（admin 或 viewer 均可，防匿名用户乱写）
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole("ADMIN", "VIEWER")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyRole("ADMIN", "VIEWER")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole("ADMIN", "VIEWER")
                        // 读操作全部公开
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .anyRequest().permitAll())
                .httpBasic(basic -> { });
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.security.admin-password:admin123}") String adminPwd,
            @Value("${app.security.viewer-password:viewer123}") String viewerPwd) {
        return new InMemoryUserDetailsManager(
                User.withUsername("admin").password("{noop}" + adminPwd).roles("ADMIN").build(),
                User.withUsername("viewer").password("{noop}" + viewerPwd).roles("VIEWER").build()
        );
    }
}
