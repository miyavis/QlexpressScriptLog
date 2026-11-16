package com.example.rulelog.express;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.security.QLSecurityStrategy;
import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.exception.QLSyntaxException;
import com.example.rulelog.biz.DiscountService;
import com.example.rulelog.biz.UserService;
import com.example.rulelog.trace.StepLogger;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * QLExpress4 (4.1.3) 规则引擎封装。
 *
 * 1. 中文脚本：addAlias 把中文关键字/比较符映射到 QLExpress4 关键字与操作符
 * 2. 业务能力：addFunction 把 Spring Service 方法注册成中文函数
 * 3. 安全策略：默认 isolation 隔离策略禁止脚本访问 Java 类与对象成员，本项目设置为 open
 * 4. 中文别名注册表：记录 脚本中文函数名 ↔ Java 方法签名 的映射，用于执行日志显示中文
 */
@Component
public class Express4Engine {

    /** 注册到引擎的中文别名一览，前端帮助文案与之一致 */
    public static final String[][] CHINESE_ALIASES = {
            {"如果", "if"},
            {"则", "then"},
            {"否则", "else"},
            {"返回", "return"},
            {"等于", "=="},
            {"不等于", "!="},
            {"大于", ">"},
            {"小于", "<"},
            {"大于等于", ">="},
            {"小于等于", "<="},
            {"并且", "&&"},
            {"或者", "||"}
    };

    private final Express4Runner runner;

    /** 中文函数别名注册表（作为 Spring Bean 暴露，供 ServiceCallAspect 查找中文显示名） */
    private final FunctionAliasRegistry aliasRegistry = new FunctionAliasRegistry();

    public Express4Engine(UserService userService, DiscountService discountService) {
        this.runner = new Express4Runner(InitOptions.builder()
                .securityStrategy(QLSecurityStrategy.open())
                .debug(false)
                .traceExpression(true) // 开启表达式轨迹，用于按行号归属日志
                .build());

        registerChineseAliases();
        registerBusinessFunctions(userService, discountService);
    }

    /** 暴露别名注册表为 Spring Bean，供 ServiceCallAspect 注入使用 */
    @Bean
    public FunctionAliasRegistry functionAliasRegistry() {
        return aliasRegistry;
    }

    private void registerChineseAliases() {
        for (String[] alias : CHINESE_ALIASES) {
            runner.addAlias(alias[0], alias[1]);
        }
    }

    private void registerBusinessFunctions(UserService userService, DiscountService discountService) {
        // 查询用户等级(user) -> String
        runner.addFunction("查询用户等级", (QContext qContext, Parameters parameters) ->
                userService.queryLevel((String) parameters.get(0).get()));
        aliasRegistry.register("查询用户等级", "UserService", "queryLevel", "user");

        // 查询用户积分(user) -> int
        runner.addFunction("查询用户积分", (QContext qContext, Parameters parameters) ->
                userService.queryPoints((String) parameters.get(0).get()));
        aliasRegistry.register("查询用户积分", "UserService", "queryPoints", "user");

        // 计算折扣(user) -> double
        runner.addFunction("计算折扣", (QContext qContext, Parameters parameters) ->
                discountService.calcDiscount((String) parameters.get(0).get()));
        aliasRegistry.register("计算折扣", "DiscountService", "calcDiscount", "user");

        // 记录日志(步骤名 [, 详情])：业务人员在脚本里打点
        runner.addVarArgsFunction("记录日志", (Object[] args) -> {
            String stepName = args.length > 0 ? String.valueOf(args[0]) : "未命名步骤";
            String detail = args.length > 1 ? String.valueOf(args[1]) : "";
            StepLogger.log(stepName, detail);
            return "ok";
        });
        aliasRegistry.register("记录日志", "StepLogger", "log", "步骤名, 内容");
    }

    /** 执行脚本；context 的键即脚本变量名 */
    public QLResult execute(String script, Map<String, Object> context, long timeoutMillis)
            throws com.alibaba.qlexpress4.exception.QLException {
        QLOptions qlOptions = QLOptions.builder()
                .timeoutMillis(timeoutMillis)
                .traceExpression(true) // 开启表达式轨迹，用于按行号归属日志
                .build();
        return runner.execute(script, context, qlOptions);
    }

    /** 语法检查（不执行） */
    public void check(String script) throws QLSyntaxException {
        runner.check(script);
    }
}
