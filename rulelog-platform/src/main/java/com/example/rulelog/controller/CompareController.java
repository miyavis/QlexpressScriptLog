package com.example.rulelog.controller;

import com.example.rulelog.compare.CompareResult;
import com.example.rulelog.compare.CompareRule;
import com.example.rulelog.exception.NotFoundException;
import com.example.rulelog.service.CompareService;
import com.example.rulelog.store.CompareRuleStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 比对画布 REST API：规则 CRUD + 执行 + 取数源列表。 */
@RestController
@RequestMapping("/api/compare-rules")
public class CompareController {

    private final CompareRuleStore store;
    private final CompareService compareService;

    public CompareController(CompareRuleStore store, CompareService compareService) {
        this.store = store;
        this.compareService = compareService;
    }

    /** 规则列表 */
    @GetMapping
    public List<CompareRule> list() {
        return store.list();
    }

    /** 单条规则 */
    @GetMapping("/{id}")
    public CompareRule get(@PathVariable String id) {
        CompareRule rule = store.get(id);
        if (rule == null) throw new NotFoundException("比对规则不存在: " + id);
        return rule;
    }

    /** 新建 / 更新 */
    @PostMapping
    public CompareRule save(@RequestBody CompareRule rule) {
        return store.put(rule);
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable String id) {
        if (!store.remove(id)) throw new NotFoundException("比对规则不存在: " + id);
        return java.util.Collections.singletonMap("ok", true);
    }

    /** 执行比对：Body = {ruleId, context:{...}} */
    @PostMapping("/run")
    public CompareResult run(@RequestBody Map<String, Object> body) {
        String ruleId = body.get("ruleId") == null ? null : String.valueOf(body.get("ruleId"));
        if (ruleId == null || ruleId.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少 ruleId");
        }
        CompareRule rule = store.get(ruleId.trim());
        if (rule == null) throw new NotFoundException("比对规则不存在: " + ruleId);
        @SuppressWarnings("unchecked")
        Map<String, Object> context = body.get("context") instanceof Map
                ? (Map<String, Object>) body.get("context") : java.util.Collections.emptyMap();
        return compareService.run(rule, context);
    }

    /** 可作为取数来源的脚本（前端下拉） */
    @GetMapping("/script-sources")
    public List<Map<String, String>> scriptSources() {
        return compareService.listScriptSources();
    }
}
