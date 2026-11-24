package com.example.rulelog.function;

import com.example.rulelog.param.ParamBinder;
import com.example.rulelog.param.ParamDef;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注解函数的反射调用器（无 Spring）：
 * QLExpress 传入位置参数 → 按 ParamDef 命名/顺序绑定 → 转成目标方法的 Java 类型 → invoke。
 *
 * 这样 @RuleFunction 不只是"元信息"，而是真正可被调用的函数；
 * SDK 的 RuleEngine.scanFunctions(beans) 与平台侧共用这条路径。
 */
public final class FunctionInvoker {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FunctionInvoker() {
    }

    /** 用注解元信息 + QLExpress 位置参数调用目标方法 */
    public static Object invoke(FunctionMeta meta, List<Object> positionalArgs) throws Exception {
        Method method = meta.getTargetMethod();
        Object bean = meta.getTargetBean();
        if (method == null || bean == null) {
            throw new IllegalStateException("函数 [" + meta.getName() + "] 未绑定目标方法/实例，"
                    + "请通过 scanFunctions(bean) 注册");
        }

        // 位置参数 → 按 ParamDef 名字组装 Map（与 @RuleParam 声明顺序一致）
        Map<String, Object> named = new LinkedHashMap<>();
        List<ParamDef> defs = meta.getParams();
        for (int i = 0; i < positionalArgs.size(); i++) {
            String key = i < defs.size() ? defs.get(i).getName() : "arg" + i;
            named.put(key, positionalArgs.get(i));
        }
        Object[] args = ParamBinder.bind(defs, named);

        // 按方法签名做最终类型适配（QLExpress 的数字是 Integer/Long/Double，对象可能是 Map）
        // 注解可能声明在非 public 的内部类/Bean 上，开放访问避免 IllegalAccessException
        if (!method.canAccess(bean)) {
            method.setAccessible(true);
        }
        Class<?>[] types = method.getParameterTypes();
        Object[] finalArgs = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            finalArgs[i] = coerce(i < args.length ? args[i] : null, types[i]);
        }
        return method.invoke(bean, finalArgs);
    }

    /** 把值转成目标 Java 类型 */
    private static Object coerce(Object value, Class<?> target) {
        if (value == null) return null;
        if (target.isInstance(value)) return value;
        // 数字：按目标类型收敛
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (target == int.class || target == Integer.class) return (int) d;
            if (target == long.class || target == Long.class) return (long) d;
            if (target == double.class || target == Double.class) return d;
            if (target == float.class || target == Float.class) return (float) d;
            if (target == short.class || target == Short.class) return (short) d;
            if (target == byte.class || target == Byte.class) return (byte) d;
            if (target == java.math.BigDecimal.class) return java.math.BigDecimal.valueOf(d);
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (target == int.class || target == Integer.class) return Integer.parseInt(s);
            if (target == long.class || target == Long.class) return Long.parseLong(s);
            if (target == double.class || target == Double.class) return Double.parseDouble(s);
            if (target == boolean.class || target == Boolean.class) return Boolean.parseBoolean(s);
        }
        if (value instanceof Boolean && (target == boolean.class || target == Boolean.class)) {
            return value;
        }
        // Map/List → POJO（嵌套对象/数组参数）
        if (value instanceof Map || value instanceof List) {
            return MAPPER.convertValue(value, target);
        }
        return value;
    }
}
