package com.example.rulelog.service;

import com.example.rulelog.compare.CompareEngine;
import com.example.rulelog.compare.CompareResult;
import com.example.rulelog.compare.CompareRule;
import com.example.rulelog.express.Express4Engine;
import com.example.rulelog.model.Script;
import com.example.rulelog.store.ScriptStore;
import com.example.rulelog.util.JsonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 比对服务：把 core 的 CompareEngine 接到平台的真实取数能力。
 *
 * 取数节点 sourceType：
 * - CONST     sourceName 直接是 JSON 字面量（内置样例/手工填数据）
 * - SCRIPT    sourceName 是脚本 ID，执行脚本取返回值（Map / List&lt;Map&gt;）
 * - FUNCTION  sourceName 是已注册中文函数名，执行 `返回 函数名(...)` 取返回值
 */
@Service
public class CompareService {

    private final ScriptStore scriptStore;
    private final Express4Engine engine;
    private final long timeoutMillis;
    private final CompareEngine compareEngine;

    public CompareService(ScriptStore scriptStore, Express4Engine engine,
                          @Value("${app.script-timeout-millis:5000}") long timeoutMillis) {
        this.scriptStore = scriptStore;
        this.engine = engine;
        this.timeoutMillis = timeoutMillis;
        this.compareEngine = new CompareEngine(this::fetch);
    }

    /** 执行比对规则 */
    public CompareResult run(CompareRule rule, Map<String, Object> context) {
        return compareEngine.run(rule, context);
    }

    /** 取数回调 */
    private Object fetch(String sourceType, String sourceName, Map<String, Object> params) {
        if (sourceName == null) return null;
        switch (sourceType == null ? "CONST" : sourceType.toUpperCase()) {
            case "SCRIPT":
                Script script = scriptStore.get(sourceName);
                if (script == null) throw new IllegalArgumentException("取数脚本不存在: " + sourceName);
                return engine.execute(script.getContent(), params, timeoutMillis).getResult();
            case "FUNCTION":
                return invokeFunction(sourceName, params);
            case "CONST":
            default:
                try {
                    return JsonUtils.mapper().readValue(sourceName, Object.class);
                } catch (Exception e) {
                    // 非 JSON 按纯字符串返回
                    return sourceName;
                }
        }
    }

    /** 通过临时脚本调用已注册中文函数：返回 函数名(__arg0, __arg1...); */
    private Object invokeFunction(String functionName, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder("返回 ").append(functionName).append("(");
        Map<String, Object> ctx = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        int i = 0;
        for (Object v : params.values()) {
            String arg = "__arg" + i++;
            names.add(arg);
            ctx.put(arg, v);
        }
        sb.append(String.join(", ", names)).append(");");
        return engine.execute(sb.toString(), ctx, timeoutMillis).getResult();
    }

    /** 列出可作为取数来源的脚本（供前端下拉） */
    public List<Map<String, String>> listScriptSources() {
        List<Map<String, String>> out = new ArrayList<>();
        for (Script s : scriptStore.list()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            out.add(m);
        }
        return out;
    }
}
