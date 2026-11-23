package com.example.rulelog.loader;

import java.util.List;

/**
 * 规则加载器接口（可插拔，像 Quartz 的 JobStore）。
 *
 * 实现类：FileRuleLoader（文件/ClassPath）、JdbcRuleLoader（QL_RULE 表）、
 * RemoteRuleLoader（平台 API，后续）。
 *
 * SDK 通过 RuleEngine.builder().loader(loader) 配置用哪种来源。
 */
public interface RuleLoader {

    /** 按规则逻辑名加载规则定义；不存在返回 null */
    RuleDefinition load(String ruleId);

    /** 列出所有可用规则的逻辑名 */
    List<String> listRuleIds();

    /** 强制刷新某规则的缓存（若有缓存） */
    default void refresh(String ruleId) { }
}
