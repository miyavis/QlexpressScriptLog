package com.example.rulelog.controller;

import com.example.rulelog.compare.CompareSchedule;
import com.example.rulelog.exception.NotFoundException;
import com.example.rulelog.service.CompareSchedulerService;
import com.example.rulelog.store.CompareScheduleStore;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 比对定时任务 REST API（面向业务人员：填间隔分钟，无需 cron） */
@RestController
@RequestMapping("/api/compare-schedules")
public class CompareScheduleController {

    private final CompareScheduleStore store;
    private final CompareSchedulerService scheduler;

    public CompareScheduleController(CompareScheduleStore store, CompareSchedulerService scheduler) {
        this.store = store;
        this.scheduler = scheduler;
    }

    @GetMapping
    public List<CompareSchedule> list(@RequestParam(required = false) String ruleId) {
        return ruleId == null ? store.list() : store.listByRule(ruleId);
    }

    @GetMapping("/{id}")
    public CompareSchedule get(@PathVariable String id) {
        CompareSchedule s = store.get(id);
        if (s == null) throw new NotFoundException("定时任务不存在: " + id);
        return s;
    }

    @PostMapping
    public CompareSchedule save(@RequestBody CompareSchedule schedule) {
        return scheduler.save(schedule);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable String id) {
        scheduler.delete(id);
        return Collections.singletonMap("ok", true);
    }

    /** 立即运行一次 */
    @PostMapping("/{id}/run")
    public Map<String, Boolean> runNow(@PathVariable String id) {
        scheduler.runNow(id);
        return Collections.singletonMap("ok", true);
    }
}
