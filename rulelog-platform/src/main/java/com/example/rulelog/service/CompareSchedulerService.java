package com.example.rulelog.service;

import com.example.rulelog.compare.CompareRecord;
import com.example.rulelog.compare.CompareResult;
import com.example.rulelog.compare.CompareRule;
import com.example.rulelog.compare.CompareSchedule;
import com.example.rulelog.store.CompareRecordStore;
import com.example.rulelog.store.CompareRuleStore;
import com.example.rulelog.store.CompareScheduleStore;
import com.example.rulelog.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 比对定时任务调度：按固定间隔自动执行比对规则并落历史记录。
 * 启动时恢复所有启用的任务；配置变更后立即重排。
 */
@Service
@Order(3)
public class CompareSchedulerService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompareSchedulerService.class);
    private final TaskScheduler scheduler;
    private final CompareScheduleStore scheduleStore;
    private final CompareRuleStore ruleStore;
    private final CompareService compareService;
    private final CompareRecordStore recordStore;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public CompareSchedulerService(TaskScheduler scheduler, CompareScheduleStore scheduleStore,
                                   CompareRuleStore ruleStore, CompareService compareService,
                                   CompareRecordStore recordStore) {
        this.scheduler = scheduler;
        this.scheduleStore = scheduleStore;
        this.ruleStore = ruleStore;
        this.compareService = compareService;
        this.recordStore = recordStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        scheduleAll();
    }

    /** 启动时恢复 */
    public void scheduleAll() {
        for (CompareSchedule s : scheduleStore.list()) reschedule(s);
        log.info("比对定时任务调度已恢复：{} 条", scheduleStore.list().size());
    }

    /** 新增/更新任务并立即重排 */
    public CompareSchedule save(CompareSchedule s) {
        scheduleStore.put(s);
        reschedule(s);
        return s;
    }

    /** 删除任务 */
    public void delete(String id) {
        cancel(id);
        scheduleStore.remove(id);
    }

    /** 立刻执行一次（供前端"立即运行"） */
    public void runNow(String id) {
        execute(id);
    }

    private void reschedule(CompareSchedule s) {
        cancel(s.getId());
        if (!s.isEnabled() || s.getIntervalMinutes() <= 0) return;
        ScheduledFuture<?> f;
        try {
            f = scheduler.scheduleAtFixedRate(() -> execute(s.getId()),
                    Duration.ofMinutes(s.getIntervalMinutes()));
        } catch (Exception e) {
            log.warn("定时任务调度失败 [{}]: {}", s.getId(), e.getMessage());
            return;
        }
        futures.put(s.getId(), f);
        s.setNextRunAt(System.currentTimeMillis() + s.getIntervalMinutes() * 60_000L);
        scheduleStore.put(s);
        log.info("定时任务已调度: {} 每 {} 分钟", s.getName(), s.getIntervalMinutes());
    }

    private void cancel(String id) {
        ScheduledFuture<?> f = futures.remove(id);
        if (f != null) f.cancel(false);
    }

    private void execute(String id) {
        CompareSchedule s = scheduleStore.get(id);
        if (s == null || !s.isEnabled()) return;
        CompareRule rule = ruleStore.get(s.getRuleId());
        if (rule == null) {
            log.warn("定时任务 [{}] 的规则 [{}] 不存在，跳过", s.getName(), s.getRuleId());
            return;
        }
        Map<String, Object> context;
        try {
            context = JsonUtils.mapper().readValue(s.getContextJson() == null ? "{}" : s.getContextJson(), Map.class);
        } catch (Exception e) {
            log.warn("定时任务 [{}] 上下文 JSON 非法，按空上下文执行: {}", s.getName(), e.getMessage());
            context = new java.util.LinkedHashMap<>();
        }
        long now = System.currentTimeMillis();
        try {
            CompareResult result = compareService.run(rule, context);
            CompareRecord rec = new CompareRecord();
            rec.setId("cmprec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            rec.setRuleId(rule.getId());
            rec.setRuleName(rule.getName());
            rec.setSavedAt(now);
            rec.setContext(context);
            rec.setResult(result);
            rec.setSummary("定时 / " + result.getTotalRows() + " 行 / " + result.getInconsistentRows() + " 处不一致");
            recordStore.append(rec);
            s.setLastRunAt(now);
        } catch (Exception e) {
            log.warn("定时任务 [{}] 执行失败: {}", s.getName(), e.getMessage());
        }
        s.setNextRunAt(now + s.getIntervalMinutes() * 60_000L);
        scheduleStore.put(s);
    }
}
