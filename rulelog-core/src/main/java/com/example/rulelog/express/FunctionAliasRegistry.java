package com.example.rulelog.express;

import com.example.rulelog.function.FunctionMeta;
import com.example.rulelog.param.ParamDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 中文函数别名注册表。
 *
 * 记录 QLExpress 脚本中使用的中文函数名 ↔ Java 方法签名映射，
 * 并携带参数契约（ParamDef），供画布下拉、帮助页、日志中文名、前端参数渲染共用。
 */
public class FunctionAliasRegistry {

    /** 一条函数注册项 */
    private static final class Entry {
        final String chineseName;
        final String serviceName;
        final String methodName;
        final String paramHint;
        final String desc;
        final List<ParamDef> params;

        Entry(String chineseName, String serviceName, String methodName,
              String paramHint, String desc, List<ParamDef> params) {
            this.chineseName = chineseName;
            this.serviceName = serviceName;
            this.methodName = methodName;
            this.paramHint = paramHint == null ? "" : paramHint;
            this.desc = desc == null ? "" : desc;
            this.params = params == null ? new ArrayList<>() : params;
        }
    }

    /** key: "ServiceName.methodName" */
    private final Map<String, Entry> javaMethodToChinese = new LinkedHashMap<>();

    public void register(String chineseName, String serviceName, String methodName) {
        register(chineseName, serviceName, methodName, null, null, null);
    }

    public void register(String chineseName, String serviceName, String methodName, String paramHint) {
        register(chineseName, serviceName, methodName, paramHint, null, null);
    }

    /** 完整注册（含说明与参数契约） */
    public void register(String chineseName, String serviceName, String methodName,
                         String paramHint, String desc, List<ParamDef> params) {
        javaMethodToChinese.put(serviceName + "." + methodName,
                new Entry(chineseName, serviceName, methodName, paramHint, desc, params));
    }

    /** 用注解扫描的元信息注册 */
    public void registerMeta(FunctionMeta meta, String serviceName, String paramHint) {
        if (meta == null) return;
        register(meta.getName(), serviceName,
                meta.getMethodName() == null ? "" : meta.getMethodName(),
                paramHint, meta.getDesc(), meta.getParams());
    }

    public String getChineseName(String serviceName, String methodName) {
        Entry e = javaMethodToChinese.get(serviceName + "." + methodName);
        return e == null ? null : e.chineseName;
    }

    public Set<String> getAllChineseNames() {
        Set<String> names = new java.util.HashSet<>();
        for (Entry e : javaMethodToChinese.values()) names.add(e.chineseName);
        return names;
    }

    /** 全部注册项（按注册顺序），供 /api/functions 下发 */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Entry e : javaMethodToChinese.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chineseName", e.chineseName);
            item.put("serviceName", e.serviceName);
            item.put("methodName", e.methodName);
            item.put("paramHint", e.paramHint);
            item.put("desc", e.desc);
            // params 结构化参数契约（前端据此渲染中文参数框）
            List<Map<String, Object>> paramList = new ArrayList<>();
            for (ParamDef p : e.params) {
                paramList.add(paramToMap(p));
            }
            item.put("params", paramList);
            out.add(item);
        }
        return out;
    }

    /** ParamDef 递归转 Map（供 JSON 下发） */
    private Map<String, Object> paramToMap(ParamDef p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("label", p.getLabel());
        m.put("type", p.getType());
        m.put("desc", p.getDesc());
        m.put("required", p.isRequired());
        if (p.getFields() != null && !p.getFields().isEmpty()) {
            List<Map<String, Object>> fields = new ArrayList<>();
            for (ParamDef f : p.getFields()) fields.add(paramToMap(f));
            m.put("fields", fields);
        }
        if (p.getElement() != null) {
            m.put("element", paramToMap(p.getElement()));
        }
        return m;
    }
}
