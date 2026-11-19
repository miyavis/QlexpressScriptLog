package com.example.rulelog.controller;

import com.example.rulelog.exception.NotFoundException;
import com.example.rulelog.model.RuleExecutionRecord;
import com.example.rulelog.service.RuleExecutionService;
import com.example.rulelog.store.ExecutionRecordStore;
import com.example.rulelog.util.RequestValidator;
import com.example.rulelog.util.ScriptValidator;
import com.example.rulelog.express.Express4Engine;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 执行记录：执行入口 / 记录列表 / 单条记录详情。
 */
@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private final RuleExecutionService ruleExecutionService;
    private final ExecutionRecordStore recordStore;
    private final Express4Engine engine;

    public ExecutionController(RuleExecutionService ruleExecutionService,
                               ExecutionRecordStore recordStore,
                               Express4Engine engine) {
        this.ruleExecutionService = ruleExecutionService;
        this.recordStore = recordStore;
        this.engine = engine;
    }

    /** 执行脚本：POST /api/executions/run?scriptId=xxx （Body 为参数 JSON） */
    @PostMapping("/run")
    public RuleExecutionRecord run(@RequestParam String scriptId,
                                   @RequestBody(required = false) java.util.Map<String, Object> params)
            throws Exception {
        // 执行错误已被 RuleExecutionService 转成 record 内的 errorMessage 展示；
        // 这里不再包一层 RuntimeException（会把内部异常信息经 GlobalExceptionHandler 泄漏）
        return ruleExecutionService.run(scriptId, params);
    }

    /** 执行记录列表（最近优先，limit 默认 50，归一到 1..200） */
    @GetMapping
    public List<RuleExecutionRecord> list(@RequestParam(required = false, defaultValue = "50") int limit) {
        return recordStore.listRecent(RequestValidator.normalizeLimit(limit));
    }

    /** 单条执行记录详情（含日志、调用链） */
    @GetMapping("/{traceId}")
    public RuleExecutionRecord get(@PathVariable String traceId) {
        RuleExecutionRecord record = recordStore.get(traceId);
        if (record == null) {
            throw new NotFoundException("执行记录不存在: " + traceId);
        }
        return record;
    }

    /** 用历史记录的参数快照重新执行（用当前保存的脚本，生成新 traceId 的新记录） */
    @PostMapping("/rerun")
    public RuleExecutionRecord rerun(@RequestParam String traceId) throws Exception {
        RuleExecutionRecord old = recordStore.get(traceId);
        if (old == null) {
            throw new NotFoundException("执行记录不存在: " + traceId);
        }
        return ruleExecutionService.run(old.getScriptId(), old.getParams());
    }

    /** 查询某脚本的所有执行记录（最近优先，供脚本页右侧展示日志一一对应） */
    @GetMapping("/script/{scriptId}")
    public List<RuleExecutionRecord> listByScript(@PathVariable String scriptId,
                                                  @RequestParam(required = false, defaultValue = "50") int limit) {
        return recordStore.listByScript(scriptId, RequestValidator.normalizeLimit(limit));
    }

    /** 保存前静态校验：检查语法与未定义变量（不执行脚本） */
    @PostMapping(value = "/check", produces = "application/json;charset=UTF-8")
    public ScriptValidator.Result check(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return ScriptValidator.Result.fail("请求体不能为空");
        }
        String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
        @SuppressWarnings("unchecked")
        java.util.Collection<String> paramKeys = body.get("paramKeys") instanceof java.util.Collection
                ? (java.util.Collection<String>) body.get("paramKeys") : java.util.Collections.emptyList();
        return ScriptValidator.validate(engine, content, paramKeys);
    }
}
