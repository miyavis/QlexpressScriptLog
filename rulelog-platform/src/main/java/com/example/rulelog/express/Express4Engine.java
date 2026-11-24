package com.example.rulelog.express;

import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.exception.QLSyntaxException;
import com.example.rulelog.biz.DiscountService;
import com.example.rulelog.biz.UserService;
import com.example.rulelog.engine.RuleEngine;
import com.example.rulelog.function.FunctionMeta;
import com.example.rulelog.function.FunctionScanner;
import com.example.rulelog.trace.StepLogger;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * QLExpress4 规则引擎封装（Spring 平台层）。
 *
 * 执行内核已下沉到 core 模块（RuleEngine，无 Spring）；本类负责：
 * - 作为 Spring Bean 装配，注入业务 Service
 * - 把业务函数注册到内核
 * - 暴露别名注册表 Bean（供 AOP / 前端 / 日志中文名使用）
 */
@Component
public class Express4Engine {

    private final RuleEngine core;

    /** 中文函数别名注册表（作为 Spring Bean 暴露，供 ServiceCallAspect 查找中文显示名） */
    private final FunctionAliasRegistry aliasRegistry = new FunctionAliasRegistry();

    public Express4Engine(UserService userService, DiscountService discountService) {
        this.core = new RuleEngine(5000L);
        registerBusinessFunctions(userService, discountService);
    }

    /** 暴露别名注册表为 Spring Bean */
    @Bean
    public FunctionAliasRegistry functionAliasRegistry() {
        return aliasRegistry;
    }

    private void registerBusinessFunctions(UserService userService, DiscountService discountService) {
        // 注解即注册：反射调用 + 参数契约一次完成，删除了原来手写的 3 段 lambda
        core.scanFunctions(userService, discountService);

        // 别名注册表：中文名/说明/嵌套参数定义来自 @RuleFunction / @RuleParam
        for (Object bean : new Object[]{userService, discountService}) {
            for (FunctionMeta meta : FunctionScanner.scan(bean)) {
                String paramHint = meta.getParams().isEmpty() ? ""
                        : meta.getParams().stream().map(p -> p.getName()).reduce((a, b) -> a + ", " + b).orElse("");
                aliasRegistry.registerMeta(meta, simpleServiceName(bean), paramHint);
            }
        }

        // 记录日志不是业务 Service 方法（脚本语句），保留手写可变参数注册
        core.registerVarArgs("记录日志", args -> {
            String stepName = args.length > 0 ? String.valueOf(args[0]) : "未命名步骤";
            String detail = args.length > 1 ? String.valueOf(args[1]) : "";
            StepLogger.log(stepName, detail);
            return "ok";
        });
        aliasRegistry.register("记录日志", "StepLogger", "log", "步骤名, 内容");
    }

    /** AOP 代理类名可能带 $$，归一化成 UserService / DiscountService */
    private String simpleServiceName(Object bean) {
        String n = bean.getClass().getSimpleName();
        int i = n.indexOf("$$");
        return i > 0 ? n.substring(0, i) : n;
    }

    /** 获取 core 内核（供 RuleExecutionService 等复用） */
    public RuleEngine core() {
        return core;
    }

    // ---------- 委托到 core（保持原有 API 不变） ----------
    public QLResult execute(String script, Map<String, Object> context, long timeoutMillis) {
        return core.execute(script, context, timeoutMillis);
    }

    public void check(String script) throws QLSyntaxException {
        core.check(script);
    }

    public Set<String> getAssignedVarNames(String script) {
        return core.getAssignedVarNames(script);
    }

    public Set<String> getOutVarNames(String script) {
        return core.getOutVarNames(script);
    }
}
