package com.example.rulelog.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** H2 镜像表行数（用于验证「启动时 JSON → H2」加载是否生效） */
@RestController
@RequestMapping("/api/public/data-stats")
public class DataStatsController {

    private final JdbcTemplate jdbc;

    public DataStatsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("QL_RULE", count("QL_RULE"));
        out.put("QL_SCRIPT", count("QL_SCRIPT"));
        out.put("QL_EXECUTION", count("QL_EXECUTION"));
        out.put("QL_COMPARE_RULE", count("QL_COMPARE_RULE"));
        out.put("QL_COMPARE_RECORD", count("QL_COMPARE_RECORD"));
        out.put("QL_COMPARE_SCHEDULE", count("QL_COMPARE_SCHEDULE"));
        out.put("audit_log", count("audit_log"));
        return out;
    }

    private int count(String table) {
        try {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return -1;
        }
    }
}
