package com.example.rulelog.function;

import com.example.rulelog.annotation.RuleFunction;
import com.example.rulelog.annotation.RuleParam;
import com.example.rulelog.param.ParamDef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 注解扫描器（无 Spring，可单测）：扫描对象/类上的 @RuleFunction 方法，
 * 解析 @RuleParam（含 schema JSON）生成递归 ParamDef 树。
 *
 * 平台侧与 SDK 侧共用——加注解即自动发现中文函数及其参数定义。
 */
public final class FunctionScanner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FunctionScanner() {
    }

    /** 扫描给定对象（实例）的所有 @RuleFunction 方法 */
    public static List<FunctionMeta> scan(Object bean) {
        if (bean == null) {
            return new ArrayList<>();
        }
        return scan(bean.getClass());
    }

    /** 扫描给定类的 @RuleFunction 方法 */
    public static List<FunctionMeta> scan(Class<?> clazz) {
        List<FunctionMeta> out = new ArrayList<>();
        if (clazz == null) {
            return out;
        }
        for (Method method : clazz.getMethods()) {
            RuleFunction ann = method.getAnnotation(RuleFunction.class);
            if (ann == null) {
                continue;
            }
            FunctionMeta meta = new FunctionMeta(ann.name(), ann.desc());
            meta.setMethodName(method.getName());
            meta.setServiceName(clazz.getSimpleName());
            List<ParamDef> params = new ArrayList<>();
            for (RuleParam p : ann.params()) {
                params.add(toParamDef(p));
            }
            meta.setParams(params);
            out.add(meta);
        }
        return out;
    }

    /** 把 @RuleParam 转成 ParamDef；schema JSON 递归解析成 fields/element */
    private static ParamDef toParamDef(RuleParam p) {
        ParamDef def = new ParamDef(p.name(), p.label(), p.type());
        def.setDesc(p.desc());
        def.setRequired(p.required());
        String schema = p.schema();
        if (schema != null && !schema.trim().isEmpty()) {
            try {
                JsonNode node = MAPPER.readTree(schema);
                if ("array".equals(def.getType())) {
                    // array 的 schema 是单个元素定义对象
                    ParamDef element = fromJson(node);
                    def.setElement(element);
                } else {
                    // object 的 schema 是子字段数组
                    if (node.isArray()) {
                        List<ParamDef> children = new ArrayList<>();
                        for (JsonNode child : node) {
                            children.add(fromJson(child));
                        }
                        def.setFields(children);
                    }
                }
            } catch (Exception e) {
                // schema 解析失败不抛，参数退化为简单类型（容错）
            }
        }
        return def;
    }

    /** 从 JSON 节点递归构建 ParamDef */
    private static ParamDef fromJson(JsonNode node) {
        ParamDef def = new ParamDef(
                text(node, "name"), text(node, "label"),
                node.has("type") ? node.get("type").asText() : "string");
        def.setDesc(text(node, "desc"));
        if (node.has("required")) {
            def.setRequired(node.get("required").asBoolean());
        }
        // 递归子字段
        if (node.has("fields") && node.get("fields").isArray()) {
            List<ParamDef> children = new ArrayList<>();
            for (JsonNode child : node.get("fields")) {
                children.add(fromJson(child));
            }
            def.setFields(children);
        }
        // 递归数组元素
        if (node.has("element") && node.get("element").isObject()) {
            def.setElement(fromJson(node.get("element")));
        }
        return def;
    }

    private static String text(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : "";
    }
}
