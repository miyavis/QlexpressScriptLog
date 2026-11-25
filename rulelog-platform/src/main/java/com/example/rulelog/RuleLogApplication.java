package com.example.rulelog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

/** 应用启动入口：Spring Boot 主类，扫描 com.example.rulelog 下全部组件。 */
@SpringBootApplication
public class RuleLogApplication {

    private static final Logger log = LoggerFactory.getLogger(RuleLogApplication.class);

    /** 启动应用，加载 Spring 上下文并监听配置端口。 */
    public static void main(String[] args) {
        var ctx = SpringApplication.run(RuleLogApplication.class, args);
        warnOnDemoDefaults(ctx.getEnvironment());
    }

    /** Demo 默认值不适合公网部署：启动时显式提醒，避免静默裸奔。 */
    private static void warnOnDemoDefaults(Environment env) {
        String admin = env.getProperty("app.security.admin-password", "");
        String viewer = env.getProperty("app.security.viewer-password", "");
        if ("admin123".equals(admin) || "viewer123".equals(viewer)) {
            log.warn("安全警告：仍在使用内置演示密码（admin123/viewer123），公网部署请通过 "
                    + "APP_SECURITY_ADMINPASSWORD / APP_SECURITY_VIEWERPASSWORD 修改");
        }
        String token = env.getProperty("app.api-token", "");
        if (token == null || token.isBlank()) {
            log.warn("安全提示：app.api-token 为空，/api/* 无 token 鉴权；公网部署建议设置 APP_API_TOKEN");
        }
    }
}
