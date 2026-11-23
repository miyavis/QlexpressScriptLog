package com.example.rulelog.util;

import com.alibaba.qlexpress4.exception.QLSyntaxException;
import com.example.rulelog.express.Express4Engine;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 保存前静态校验：在不执行脚本的前提下，提前发现语法错误与未定义变量。
 *
 * - 语法：QLExpress check()
 * - 变量：脚本引用的外部变量（getOutVarNames）必须在入参里；
 *   脚本内已赋值（getAssignVarNames）的变量不算未定义。
 */
public final class ScriptValidator {

    private ScriptValidator() {
    }

    /** 校验结果：valid + 面向用户的消息（record，Jackson 可直接序列化） */
    public record Result(boolean valid, String message) {
        public static Result ok() {
            return new Result(true, "语法与变量检查通过");
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    /**
     * 校验脚本。
     *
     * @param engine 规则引擎（提供 check / outVar / assignVar 能力）
     * @param script 脚本文本
     * @param providedParams 执行时可用的入参键集合
     * @return 校验结果
     */
    public static Result validate(Express4Engine engine, String script, Collection<String> providedParams) {
        if (script == null || script.trim().isEmpty()) {
            return Result.fail("脚本内容为空");
        }
        try {
            engine.check(script);
        } catch (QLSyntaxException e) {
            return Result.fail("语法错误：" + rawMessage(e));
        }

        Set<String> provided = providedParams == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(providedParams);
        // 脚本内部已赋值的变量也视为已定义
        try {
            provided.addAll(engine.getAssignedVarNames(script));
        } catch (Exception e) {
            // 静态分析失败不阻塞保存，仅跳过变量检查
            return Result.ok();
        }

        Set<String> outVars;
        try {
            outVars = engine.getOutVarNames(script);
        } catch (Exception e) {
            return Result.ok();
        }
        if (outVars != null) {
            Set<String> undefined = new LinkedHashSet<>();
            for (String v : outVars) {
                if (!provided.contains(v)) {
                    undefined.add(v);
                }
            }
            if (!undefined.isEmpty()) {
                return Result.fail("未定义变量：" + String.join("、", undefined)
                        + "（请在默认参数中补充，或先用赋值语句定义）");
            }
        }
        return Result.ok();
    }

    private static String rawMessage(Throwable e) {
        String msg = e.getMessage();
        return msg == null || msg.trim().isEmpty() ? e.toString() : msg.trim();
    }
}
