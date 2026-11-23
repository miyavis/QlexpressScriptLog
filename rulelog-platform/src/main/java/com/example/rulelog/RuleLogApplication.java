package com.example.rulelog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 应用启动入口：Spring Boot 主类，扫描 com.example.rulelog 下全部组件。 */
@SpringBootApplication
public class RuleLogApplication {

    /** 启动应用，加载 Spring 上下文并监听配置端口。 */
    public static void main(String[] args) {
        SpringApplication.run(RuleLogApplication.class, args);
    }
}
