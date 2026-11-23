package com.example.rulelog.config;

import com.example.rulelog.model.Script;
import com.example.rulelog.service.RuleSyncService;
import com.example.rulelog.store.ScriptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动迁移：把 ScriptStore 里已有的脚本（内置示例 / 历史 JSON / 导入的脚本）
 * 全量同步到 QL_RULE 标准表，使 SDK 与 /api/public/* 开箱即可读到全部规则。
 *
 * 只做新增/更新，不删 QL_RULE 中脚本文件里已不存在的规则（避免误删外部写入）。
 */
@Component
public class RuleMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RuleMigrationRunner.class);
    private final ScriptStore scriptStore;
    private final RuleSyncService ruleSyncService;

    public RuleMigrationRunner(ScriptStore scriptStore, RuleSyncService ruleSyncService) {
        this.scriptStore = scriptStore;
        this.ruleSyncService = ruleSyncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int count = 0;
        for (Script script : scriptStore.list()) {
            ruleSyncService.sync(script);
            count++;
        }
        log.info("启动迁移完成：{} 个脚本已同步到 QL_RULE", count);
    }
}
