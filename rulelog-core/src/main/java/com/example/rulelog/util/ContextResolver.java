package com.example.rulelog.util;

import java.util.Map;

/**
 * 上下文路径解析器（纯逻辑）：解析 ${路径} 占位符，支持嵌套与数组下标。
 *
 * - ${user}          → context["user"]
 * - ${user.name}     → context["user"]["name"]
 * - ${users[0].name} → context["users"][0]["name"]
 * - 整个值就是 ${路径} 时返回原对象（保持类型）；混在文本中才 toString。
 */
public final class ContextResolver {

    private ContextResolver() {
    }

    /** 解析单个路径表达式，如 "user.name" 或 "users[0].name" */
    public static Object resolvePath(Map<String, Object> context, String path) {
        if (context == null || path == null || path.trim().isEmpty()) return null;
        Object cur = context;
        // 把 users[0].name 拆成 users / 0 / name
        String[] parts = path.trim().replace("[", ".").replace("]", "").split("\\.");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (cur instanceof Map) {
                cur = ((Map<?, ?>) cur).get(part);
            } else if (cur instanceof java.util.List) {
                try {
                    int idx = Integer.parseInt(part);
                    java.util.List<?> list = (java.util.List<?>) cur;
                    cur = (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
            if (cur == null) return null;
        }
        return cur;
    }

    /** 解析一个值：整个是 ${路径} 返回原对象；否则替换文本中的占位符 */
    public static Object resolveValue(Object value, Map<String, Object> context) {
        if (value == null) return null;
        if (value instanceof String) {
            String s = ((String) value).trim();
            // 整个值就是单个占位符 → 返回原对象（保持类型）
            if (s.startsWith("${") && s.endsWith("}") && s.indexOf("}") == s.length() - 1) {
                String path = s.substring(2, s.length() - 1);
                return resolvePath(context, path);
            }
            // 混合文本 → 逐个替换占位符为字符串
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}").matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                Object v = resolvePath(context, m.group(1));
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(v == null ? "" : String.valueOf(v)));
            }
            m.appendTail(sb);
            return sb.toString();
        }
        return value; // 非字符串原样
    }

    /** 解析整个参数映射：每个值经 resolveValue 处理 */
    public static Map<String, Object> resolveParams(Map<String, String> mapping, Map<String, Object> context) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (mapping == null) return out;
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            out.put(e.getKey(), resolveValue(e.getValue(), context));
        }
        return out;
    }
}
