package com.example.rulelog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security（RBAC，可选启用）：
 * - 默认 app.security.enabled=false → 全部放行（保持 Demo 零配置可用）；
 * - 开启后：/api/audit 仅 ADMIN，其余 API 需认证；内置 admin / viewer 两个用户。
 *
 * 生产应替换内存用户为 DB/LDAP，并修改默认密码。
 */
@Configuration
public class SecurityConfig {

    @Value("${app.security.enabled:false}")
    private boolean securityEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            // 默认：全部放行，关闭 CSRF（纯 API Demo）
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/audit").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "VIEWER")
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
