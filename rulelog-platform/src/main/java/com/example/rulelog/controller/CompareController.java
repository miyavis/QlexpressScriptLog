package com.example.rulelog.controller;

import com.example.rulelog.compare.CompareRecord;
import com.example.rulelog.compare.CompareResult;
import com.example.rulelog.compare.CompareRule;
import com.example.rulelog.exception.NotFoundException;
import com.example.rulelog.service.CompareService;
import com.example.rulelog.store.CompareRecordStore;
import com.example.rulelog.store.CompareRuleStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 比对画布 REST API：规则 CRUD + 执行 + 取数源列表。 */
@RestController
@RequestMapping("/api/compare-rules")
public class CompareController {

    private final CompareRuleStore store;
    private final CompareService compareService;
    private final CompareRecordStore recordStore;

    public CompareController(CompareRuleStore store, CompareService compareService, CompareRecordStore recordStore) {
        this.store = store;
        this.compareService = compareService;
        this.recordStore = recordStore;
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

        CompareResult result = compareService.run(rule, context);

        // 落盘历史（上下文快照 + 结果 + traceId 关联）
        CompareRecord rec = new CompareRecord();
        rec.setId("cmprec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        rec.setRuleId(rule.getId());
        rec.setRuleName(rule.getName());
        rec.setSavedAt(System.currentTimeMillis());
        rec.setContext(context);
        rec.setResult(result);
        rec.setSummary(result.getTotalRows() + " 行 / " + result.getInconsistentRows() + " 处不一致");
        recordStore.append(rec);

        return result;
    }

    /** 比对执行历史（可按规则过滤） */
    @GetMapping("/records")
    public List<CompareRecord> records(@RequestParam(required = false) String ruleId,
                                       @RequestParam(defaultValue = "50") int limit) {
        return recordStore.list(ruleId, Math.min(Math.max(limit, 1), 200));
    }

    /** 可作为取数来源的脚本（前端下拉） */
    @GetMapping("/script-sources")
    public List<Map<String, String>> scriptSources() {
        return compareService.listScriptSources();
    }
}
