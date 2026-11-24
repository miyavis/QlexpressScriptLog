package com.example.rulelog.store;

import com.example.rulelog.compare.CompareNode;
import com.example.rulelog.compare.CompareRule;
import com.example.rulelog.compare.FetchNode;
import com.example.rulelog.compare.FieldCompare;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** 比对规则持久化：运行时读写 H2 的 QL_COMPARE_RULE 表。 */
@Component
public class CompareRuleStore {

    private static final Logger log = LoggerFactory.getLogger(CompareRuleStore.class);
    private static final String DDL = "CREATE TABLE IF NOT EXISTS QL_COMPARE_RULE ("
            + "ID VARCHAR(64) PRIMARY KEY, NAME VARCHAR(128), RULE_ID VARCHAR(64), "
            + "SORT_TIME BIGINT, DATA CLOB)";

    private final H2JsonTable table;

    @Autowired
    public CompareRuleStore(JdbcTemplate jdbc) {
        this.table = new H2JsonTable(jdbc, "QL_COMPARE_RULE", DDL);
        if (table.count() == 0) {
            CompareRule s = sample();
            table.upsert(s.getId(), s.getName(), null, s.getUpdatedAt(), s);
            log.info("H2 比对规则表为空，已写入内置示例");
        }
    }

    public CompareRuleStore(String ignoredDataDir) {
        this(ScriptStore.testJdbc("comparerule"));
    }

    public List<CompareRule> list() {
        return table.all(CompareRule.class, 10000);
    }

    public CompareRule get(String id) {
        return table.get(id, CompareRule.class);
    }

    public CompareRule put(CompareRule rule) {
        if (rule.getId() == null || rule.getId().trim().isEmpty()) {
            rule.setId("cmp_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        rule.setUpdatedAt(System.currentTimeMillis());
        if (rule.getCreatedAt() == 0) rule.setCreatedAt(rule.getUpdatedAt());
        table.upsert(rule.getId(), rule.getName(), null, rule.getUpdatedAt(), rule);
        return rule;
    }

    public boolean remove(String id) {
        return table.delete(id);
    }

    private CompareRule sample() {
        CompareRule rule = new CompareRule();
        rule.setId("cmp_sample");
        rule.setName("双源订单对账（示例）");
        rule.setDescription("示例：内置两份订单数据（取数A/取数B），按订单号对齐比对金额与等级；金额容差 2");
        long now = System.currentTimeMillis();
        rule.setCreatedAt(now); rule.setUpdatedAt(now);

        FetchNode f1 = new FetchNode();
        f1.setId("f1"); f1.setName("取数A"); f1.setSourceType("CONST");
        f1.setSourceName("[{\"订单号\":\"O001\",\"金额\":100,\"等级\":\"VIP\"},"
                + "{\"订单号\":\"O002\",\"金额\":200,\"等级\":\"普通\"}]");
        f1.setResultKey("A");
        FetchNode f2 = new FetchNode();
        f2.setId("f2"); f2.setName("取数B"); f2.setSourceType("CONST");
        f2.setSourceName("[{\"订单号\":\"O001\",\"金额\":103,\"等级\":\"VIP\"},"
                + "{\"订单号\":\"O002\",\"金额\":200,\"等级\":\"普通\"}]");
        f2.setResultKey("B");
        rule.setFetchNodes(Arrays.asList(f1, f2));

        CompareNode cmp = new CompareNode();
        cmp.setId("c1"); cmp.setName("订单比对");
        cmp.setKeyField("订单号");
        cmp.setSourceKeys(Arrays.asList("A", "B"));
        FieldCompare amount = new FieldCompare("金额", "金额", "numericTolerance");
        amount.setTolerance(2);
        FieldCompare level = new FieldCompare("等级", "等级", "equal");
        cmp.setFields(Arrays.asList(amount, level));
        rule.setCompareNode(cmp);
        return rule;
    }
}
