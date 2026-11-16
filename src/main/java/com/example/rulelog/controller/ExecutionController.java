package com.example.rulelog.controller;

import com.example.rulelog.model.RuleExecutionRecord;
import com.example.rulelog.model.ScriptRequest;
import com.example.rulelog.service.RuleExecutionService;
import com.example.rulelog.store.ExecutionRecordStore;
import com.example.rulelog.store.ScriptStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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
    private final ScriptStore scriptStore;
    private final ObjectMapper objectMapper;

    public ExecutionController(RuleExecutionService ruleExecutionService,
                               ExecutionRecordStore recordStore,
                               ScriptStore scriptStore,
                               ObjectMapper objectMapper) {
        this.ruleExecutionService = ruleExecutionService;
        this.recordStore = recordStore;
        this.scriptStore = scriptStore;
        this.objectMapper = objectMapper;
    }

    /** 执行脚本：POST /api/executions/run?scriptId=xxx （Body 为参数 JSON） */
    @PostMapping("/run")
    public RuleExecutionRecord run(@RequestParam String scriptId,
                                   @RequestBody(required = false) Map<String, Object> params) {
        try {
            return ruleExecutionService.run(scriptId, params);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException("执行失败: " + e.getMessage(), e);
        }
    }

    /** 执行记录列表（最近优先，limit 默认 50） */
    @GetMapping
    public List<RuleExecutionRecord> list(@RequestParam(required = false, defaultValue = "50") int limit) {
        return recordStore.listRecent(limit);
    }

    /** 单条执行记录详情（含日志、调用链） */
    @GetMapping("/{traceId}")
    public RuleExecutionRecord get(@PathVariable String traceId) {
        RuleExecutionRecord record = recordStore.get(traceId);
        if (record == null) {
            throw new com.example.rulelog.exception.NotFoundException("执行记录不存在: " + traceId);
        }
        return record;
    }

    /** 查询某脚本的所有执行记录（最近优先，供脚本页右侧展示日志一一对应） */
    @GetMapping("/script/{scriptId}")
    public List<RuleExecutionRecord> listByScript(@PathVariable String scriptId,
                                                  @RequestParam(required = false, defaultValue = "50") int limit) {
        return recordStore.listByScript(scriptId, limit);
    }
}
