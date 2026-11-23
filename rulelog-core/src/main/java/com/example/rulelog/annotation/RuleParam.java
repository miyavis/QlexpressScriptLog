package com.example.rulelog.annotation;

import java.lang.annotation.*;

/**
 * 规则函数的参数定义（配合 @RuleFunction 使用）。
 *
 * 注意：Java 注解元素不能递归引用自身（不能有 RuleParam[] fields），
 * 因此复杂类型（object/array）的子结构用 schemaJson（JSON 字符串）描述，
 * 由 FunctionScanner 解析成递归的 ParamDef 树。
 */
@Target({}) // 只作为 @RuleFunction.params() 的元素
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RuleParam {
    /** 参数名（脚本里用的标识，如 user、order） */
    String name();

    /** 中文名（界面展示，如"用户名"） */
    String label();

    /** 类型：string / number / boolean / object / array */
    String type() default "string";

    /** 含义说明 */
    String desc() default "";

    /** 是否必填 */
    boolean required() default false;

    /**
     * 复杂类型（object/array）的子结构，用 JSON 描述，由 FunctionScanner 解析成 ParamDef 树。
     * object 示例：[{"name":"city","label":"城市","type":"string","required":true}]
     * array 示例：{"name":"item","label":"用户","type":"object","fields":[...]}
     * 简单类型留空即可。
     */
    String schema() default "";
}
