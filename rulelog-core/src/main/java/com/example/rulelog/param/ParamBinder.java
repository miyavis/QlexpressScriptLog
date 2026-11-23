package com.example.rulelog.param;

import java.util.List;
import java.util.Map;

/**
 * 参数绑定器（无 Spring）：把参数 Map 按 ParamDef 顺序转成方法实参数组。
 *
 * 这是"注解声明参数 → 实际 Java 方法调用"的桥梁：SDK 拿到嵌套 Map/JSON 后，
 * 按函数参数定义顺序提取值，传给方法引用或反射调用。
 */
public final class ParamBinder {

    private ParamBinder() {
    }

    /** 按 defs 顺序从 params 提取实参；缺失补 null */
    public static Object[] bind(List<ParamDef> defs, Map<String, Object> params) {
        if (defs == null) return new Object[0];
        Object[] args = new Object[defs.size()];
        Map<String, Object> p = params == null ? java.util.Collections.emptyMap() : params;
        for (int i = 0; i < defs.size(); i++) {
            args[i] = convert(p.get(defs.get(i).getName()), defs.get(i));
        }
        return args;
    }

    /** 按目标类型做基础转换（数字字符串 → Number 等） */
    private static Object convert(Object value, ParamDef def) {
        if (value == null) return null;
        String type = def.getType() == null ? "string" : def.getType();
        if ("number".equals(type) && value instanceof String) {
            try {
                String s = ((String) value).trim();
                if (s.contains(".")) return Double.parseDouble(s);
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return value; // 转换失败原样返回，由校验器报错
            }
        }
        if ("boolean".equals(type) && value instanceof String) {
            return Boolean.parseBoolean(((String) value).trim());
        }
        return value; // object/array/string 原样（嵌套结构已是 Map/List）
    }
}
