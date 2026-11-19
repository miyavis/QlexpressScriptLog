package com.example.rulelog.controller;

import com.example.rulelog.store.AuditStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 审计日志查询：最近的操作记录（谁/何时/动作/对象）。 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditStore auditStore;

    public AuditController(AuditStore auditStore) {
        this.auditStore = auditStore;
    }

    @GetMapping
    public List<Map<String, Object>> recent(@RequestParam(required = false, defaultValue = "50") int limit) {
        return auditStore.recent(limit);
    }
}
