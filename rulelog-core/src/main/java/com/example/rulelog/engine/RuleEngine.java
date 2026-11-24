package com.example.rulelog.engine;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.api.QLFunctionalVarargs;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import com.alibaba.qlexpress4.security.QLSecurityStrategy;
import com.example.rulelog.function.FunctionInvoker;
import com.example.rulelog.function.FunctionMeta;
import com.example.rulelog.function.FunctionScanner;
import com.example.rulelog.loader.RuleDefinition;
import com.example.rulelog.loader.RuleLoader;
import com.example.rulelog.param.ParamDef;
import com.example.rulelog.param.ParamValidator;
import com.example.rulelog.trace.TraceParent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 规则执行引擎内核（无 Spring，SDK 主体）。
 *
 * 职责：
 * - QLExpress 引擎封装（中文别名 + 安全白名单 + 表达式轨迹）
 * - 注册业务函数（CustomFunction / QLFunctionalVarargs / 注解扫描）
 * - 执行 / 语法检查 / 变量分析
 *
 * Spring 平台层（Express4Engine）和 SDK 用户共用同一个内核。
 */
public class RuleEngine {

    /** 中文别名表（脚本中文关键字 → QLExpress 关键字） */
    public static final String[][] CHINESE_ALIASES = {
            {"如果", "if"}, {"则", "then"}, {"否则", "else"}, {"返回", "return"},
            {"等于", "=="}, {"不等于", "!="}, {"大于", ">"}, {"小于", "<"},
            {"大于等于", ">="}, {"小于等于", "<="}, {"并且", "&&"}, {"或者", "||"}
    };

    private final Express4Runner runner;
    private final long defaultTimeoutMillis;
    private final Map<String, FunctionMeta> functions = new LinkedHashMap<>();
    private RuleLoader loader;   // 可选：规则来源（SDK 场景用）

    /** 默认构造：安全白名单 + 中文别名 + 表达式轨迹 */
    public RuleEngine() {
        this(5000L);
    }

    public RuleEngine(long defaultTimeoutMillis) {
        this.defaultTimeoutMillis = defaultTimeoutMillis;
        this.runner = new Express4Runner(InitOptions.builder()
                .securityStrategy(QLSecurityStrategy.whiteList(Collections.emptySet())) // 空白名单杜绝 RCE
                .debug(false)
                .traceExpression(true)
                .build());
        for (String[] alias : CHINESE_ALIASES) {
            runner.addAlias(alias[0], alias[1]);
        }
    }

    // ---------- 函数注册 ----------
    /** 注册带 QContext/Parameters 的函数 */
    public RuleEngine register(String name, CustomFunction fn) {
        runner.addFunction(name, fn);
        return this;
    }

    /** 注册可变参数函数（如"记录日志"） */
    public RuleEngine registerVarArgs(String name, QLFunctionalVarargs fn) {
        runner.addVarArgsFunction(name, fn);
        return this;
    }

    /** 注册单参数函数（Java Function） */
    public <T, R> RuleEngine register(String name, java.util.function.Function<T, R> fn) {
        runner.addFunction(name, fn);
        return this;
    }

    /** 记录函数元信息（供契约下发，与实际执行注册分离，便于解耦） */
    public RuleEngine registerMeta(FunctionMeta meta) {
        if (meta != null && meta.getName() != null) {
            functions.put(meta.getName(), meta);
        }
        return this;
    }

    public Map<String, FunctionMeta> getFunctions() {
        return Collections.unmodifiableMap(functions);
    }

    // ---------- 执行 / 校验 ----------
    /** 执行脚本；context 的键即脚本变量名 */
    public QLResult execute(String script, Map<String, Object> context) {
        return execute(script, context, defaultTimeoutMillis);
    }

    public QLResult execute(String script, Map<String, Object> context, long timeoutMillis) {
        QLOptions opts = QLOptions.builder()
                .timeoutMillis(timeoutMillis)
                .traceExpression(true)
                .build();
        return runner.execute(script, context, opts);
    }

    /** 语法检查（不执行） */
    public void check(String script) {
        runner.check(script);
    }

    /** 脚本内被赋值/更新的变量名 */
    public Set<String> getAssignedVarNames(String script) {
        return runner.getAssignVarNames(script);
    }

    /** 脚本引用的外部变量名 */
    public Set<String> getOutVarNames(String script) {
        return runner.getOutVarNames(script);
    }

    public long getDefaultTimeoutMillis() {
        return defaultTimeoutMillis;
    }

    /** 设置规则来源（loader） */
    public RuleEngine loader(RuleLoader loader) {
        this.loader = loader;
        return this;
    }

    /**
     * 扫描对象上的 @RuleFunction 注解，自动注册函数（SDK 侧，无需 Spring）。
     * 注册内容包含两部分：可执行的 QLExpress 函数（反射调用）+ 参数元信息（契约下发）。
     */
    public RuleEngine scanFunctions(Object... beans) {
        if (beans == null) return this;
        for (Object bean : beans) {
            for (FunctionMeta meta : FunctionScanner.scan(bean)) {
                final FunctionMeta m = meta;
                if (meta.getTargetMethod() != null && meta.getTargetBean() != null) {
                    // 真正注册可执行函数：位置参数 → ParamBinder → 反射调用
                    register(meta.getName(), (ctx, parameters) -> {
                        java.util.List<Object> args = new java.util.ArrayList<>();
                        for (int i = 0; i < parameters.size(); i++) {
                            args.add(parameters.get(i).get());
                        }
                        try {
                            return FunctionInvoker.invoke(m, args);
                        } catch (Exception e) {
                            throw new RuntimeException("函数 [" + m.getName() + "] 调用失败: " + e.getMessage(), e);
                        }
                    });
                }
                functions.put(meta.getName(), meta);
            }
        }
        return this;
    }

    /** 按规则 ID 执行（从 loader 加载脚本 + 参数契约校验） */
    public Result runById(String ruleId, Map<String, Object> params) {
        if (loader == null) {
            return Result.fail("未配置规则来源（loader）", null);
        }
        RuleDefinition def = loader.load(ruleId);
        if (def == null) {
            return Result.fail("规则不存在: " + ruleId, null);
        }
        // 参数契约校验
        List<String> errors = ParamValidator.validate(def.getParams(), params);
        if (!errors.isEmpty()) {
            return Result.fail("参数校验失败: " + String.join("; ", errors), null);
        }
        // 生成 traceId（与平台一致：16 位大写 hex）
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        try {
            QLResult qr = execute(def.getContent(), params == null ? Collections.emptyMap() : params);
            return Result.ok(qr.getResult(), traceId);
        } catch (Exception e) {
            return Result.fail(e.getMessage() == null ? e.toString() : e.getMessage(), traceId);
        }
    }

    /** builder（SDK 主入口） */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long timeout = 5000L;
        private RuleLoader loader;
        private List<Object> scanBeans = new ArrayList<>();

        public Builder timeout(long ms) { this.timeout = ms; return this; }
        public Builder loader(RuleLoader loader) { this.loader = loader; return this; }
        public Builder scanFunctions(Object... beans) {
            if (beans != null) scanBeans.addAll(Arrays.asList(beans));
            return this;
        }

        public RuleEngine build() {
            RuleEngine engine = new RuleEngine(timeout);
            if (loader != null) engine.loader(loader);
            if (!scanBeans.isEmpty()) engine.scanFunctions(scanBeans.toArray());
            return engine;
        }
    }
}
