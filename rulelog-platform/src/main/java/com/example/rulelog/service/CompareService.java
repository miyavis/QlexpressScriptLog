package com.example.rulelog.service;

import com.example.rulelog.compare.CompareEngine;
import com.example.rulelog.compare.CompareResult;
import com.example.rulelog.compare.CompareRule;
import com.example.rulelog.model.RuleExecutionRecord;
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
 * - SCRIPT    sourceName 是脚本 ID，通过 RuleExecutionService 执行（产生可下钻的执行记录 + traceId）
 * - FUNCTION  sourceName 是已注册中文函数名，执行 `返回 函数名(...)` 取返回值
 */
@Service
public class CompareService {

    private final ScriptStore scriptStore;
    private final RuleExecutionService executionService;
    private final long timeoutMillis;

    public CompareService(ScriptStore scriptStore, RuleExecutionService executionService,
                          @Value("${app.script-timeout-millis:5000}") long timeoutMillis) {
        this.scriptStore = scriptStore;
        this.executionService = executionService;
        this.timeoutMillis = timeoutMillis;
    }

    /** 执行比对规则（每次新建 CompareEngine + 取数上下文，线程安全） */
    public CompareResult run(CompareRule rule, Map<String, Object> context) {
        List<String> traceIds = new ArrayList<>();
        Map<String, String> scriptTraceIds = new LinkedHashMap<>();
        CompareEngine engine = new CompareEngine((type, name, params) -> fetch(type, name, params, traceIds, scriptTraceIds));
        CompareResult result = engine.run(rule, context);
        result.getTraceIds().addAll(traceIds);
        result.getScriptTraceIds().putAll(scriptTraceIds);
        return result;
    }

    /** 取数回调 */
    private Object fetch(String sourceType, String sourceName, Map<String, Object> params,
                         List<String> traceIds, Map<String, String> scriptTraceIds) {
        if (sourceName == null) return null;
        switch (sourceType == null ? "CONST" : sourceType.toUpperCase()) {
            case "SCRIPT":
                try {
                    RuleExecutionRecord rec = executionService.run(sourceName, params);
                    if (rec.getTraceId() != null) {
                        traceIds.add(rec.getTraceId());
                        scriptTraceIds.put(rec.getScriptId(), rec.getTraceId());
                    }
                    return rec.getResult();
                } catch (Exception e) {
                    throw new RuntimeException("取数脚本执行失败 [" + sourceName + "]: " + e.getMessage(), e);
                }
            case "FUNCTION":
                return invokeFunction(sourceName, params, scriptTraceIds);
            case "CONST":
            default:
                try {
                    return JsonUtils.mapper().readValue(sourceName, Object.class);
                } catch (Exception e) {
                    return sourceName;
                }
        }
    }

    /** 通过临时脚本调用已注册中文函数：返回 函数名(__arg0, __arg1...); */
    private Object invokeFunction(String functionName, Map<String, Object> params, Map<String, String> scriptTraceIds) {
        // 函数来源同样走执行服务，留下可下钻记录（函数名作为虚拟脚本 ID）
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
        try {
            RuleExecutionRecord rec = executionService.runByContent("@函数调用:" + functionName, sb.toString(), ctx);
            if (rec.getTraceId() != null) {
                scriptTraceIds.put(functionName, rec.getTraceId());
            }
            return rec.getResult();
        } catch (Exception e) {
            throw new RuntimeException("函数调用失败 [" + functionName + "]: " + e.getMessage(), e);
        }
    }

    /** 可作为取数来源的脚本（前端下拉） */
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
