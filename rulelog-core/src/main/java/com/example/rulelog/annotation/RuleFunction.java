package com.example.rulelog.annotation;

import java.lang.annotation.*;

/**
 * 标记一个 Service 方法为「中文规则函数」。
 *
 * 平台侧：Spring 启动扫描带此注解的 Bean 方法 → 自动注册到 QLExpress + 画布下拉 + 日志中文名。
 * SDK 侧：RuleEngine.scanFunctions(beans) 反射扫描 → 自动注册，无需 Spring。
 *
 * 用法：
 * <pre>{@code
 * @RuleFunction(name = "查询用户等级", desc = "查询用户会员等级",
 *     params = @RuleParam(name="user", label="用户名", type="string", required=true))
 * public String queryLevel(String user) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RuleFunction {
    /** 中文函数名（脚本里调用的名字，也是界面显示名） */
    String name();

    /** 函数说明（画布/帮助页展示） */
    String desc() default "";

    /** 参数定义列表 */
    RuleParam[] params() default {};
}
