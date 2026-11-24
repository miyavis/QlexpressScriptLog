package com.example.rulelog.store;

import com.example.rulelog.model.Script;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本持久化：运行时读写 H2 的 QL_SCRIPT 表。
 *
 * 启动初始化由 JsonToH2LoadRunner 完成（data/scripts.json → H2）；
 * 若 H2 为空（首次启动 / 单测），这里写入内置示例脚本。
 */
@Component
public class ScriptStore {

    private static final Logger log = LoggerFactory.getLogger(ScriptStore.class);
    private static final String DDL = "CREATE TABLE IF NOT EXISTS QL_SCRIPT ("
            + "ID VARCHAR(64) PRIMARY KEY, NAME VARCHAR(128), RULE_ID VARCHAR(64), "
            + "SORT_TIME BIGINT, DATA CLOB)";

    private final H2JsonTable table;

    @Autowired
    public ScriptStore(JdbcTemplate jdbc) {
        this.table = new H2JsonTable(jdbc, "QL_SCRIPT", DDL);
        if (table.count() == 0) {
            for (Script s : defaultScripts()) {
                table.upsert(s.getId(), s.getName(), null, s.getUpdatedAt(), s);
            }
            log.info("H2 脚本表为空，已写入 {} 条内置示例", defaultScripts().size());
        }
    }

    /** 测试构造：独立内存 H2 实例 */
    public ScriptStore(String ignoredDataDir) {
        this(testJdbc("scriptstore"));
    }

    static JdbcTemplate testJdbc(String prefix) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + prefix + "_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        return new JdbcTemplate(ds);
    }

    public List<Script> list() {
        return table.all(Script.class, 10000);
    }

    public Script get(String id) {
        return table.get(id, Script.class);
    }

    public void put(Script script) {
        if (script.getId() == null || script.getId().trim().isEmpty()) {
            script.setId("script_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }
        if (script.getCreatedAt() == 0) script.setCreatedAt(System.currentTimeMillis());
        script.setUpdatedAt(System.currentTimeMillis());
        table.upsert(script.getId(), script.getName(), null, script.getUpdatedAt(), script);
    }

    public boolean remove(String id) {
        return table.delete(id);
    }

    // ---------------- 内置示例 ----------------

    private List<Script> defaultScripts() {
        long now = System.currentTimeMillis();
        List<Script> seeds = new ArrayList<>();

        Script vip = new Script();
        vip.setId("vip_discount");
        vip.setName("VIP 折扣判定");
        vip.setContent("等级 = 查询用户等级(user);\n"
                + "记录日志(\"等级查询完成\", \"等级=\" + 等级);\n\n"
                + "如果 (等级 等于 \"VIP\") 则 {\n"
                + "    记录日志(\"进入VIP分支\", \"准备调用折扣服务\");\n"
                + "    返回 计算折扣(user);\n"
                + "} 否则 {\n"
                + "    记录日志(\"进入普通分支\", \"非VIP用户不打折\");\n"
                + "    返回 0;\n"
                + "}");
        vip.getDefaultParams().put("user", "张三");
        vip.setCreatedAt(now); vip.setUpdatedAt(now);
        seeds.add(vip);

        Script points = new Script();
        points.setId("points_double");
        points.setName("积分翻倍");
        points.setContent("积分 = 查询用户积分(user);\n"
                + "记录日志(\"积分查询完成\", \"积分=\" + 积分);\n\n"
                + "如果 (积分 大于 100) 则 {\n"
                + "    打折积分 = 积分 * 2;\n"
                + "} 否则 {\n"
                + "    打折积分 = 积分;\n"
                + "}\n\n"
                + "返回 打折积分;");
        points.getDefaultParams().put("user", "张三");
        points.setCreatedAt(now); points.setUpdatedAt(now);
        seeds.add(points);

        Script prepCall = new Script();
        prepCall.setId("prepcall_demo");
        prepCall.setName("带参取数演示（一卡两行）");
        prepCall.setContent("查询用户 = user;\n"
                + "积分 = 查询用户积分(查询用户);\n"
                + "记录日志(\"积分查询完成\", \"积分=\" + 积分);\n\n"
                + "如果 (积分 大于 100) 则 {\n"
                + "    折扣依据 = user;\n"
                + "    折扣 = 计算折扣(折扣依据);\n"
                + "    返回 积分 * 折扣;\n"
                + "} 否则 {\n"
                + "    记录日志(\"未达门槛\", \"积分不足100\");\n"
                + "    返回 积分;\n"
                + "}");
        prepCall.getDefaultParams().put("user", "张三");
        prepCall.setCreatedAt(now); prepCall.setUpdatedAt(now);
        seeds.add(prepCall);

        Script decision = new Script();
        decision.setId("decision_table_demo");
        decision.setName("决策表演示（会员折扣）");
        String decisionScript = "积分 = 查询用户积分(user);\n"
                + "等级 = 查询用户等级(user);\n"
                + "如果 (积分 > 100 && 等级 == \"VIP\") 则 {\n"
                + "    折扣 = 0.7;\n"
                + "} 否则 {\n"
                + "    如果 (积分 > 50) 则 {\n"
                + "        折扣 = 0.85;\n"
                + "    } 否则 {\n"
                + "        折扣 = 1;\n"
                + "    }\n"
                + "}\n"
                + "返回 折扣;";
        decision.setContent(decisionScript);
        decision.setDefaultParams(new java.util.LinkedHashMap<>(java.util.Collections.singletonMap("user", "张三")));
        decision.setMeta(buildDemoDecisionMeta(decisionScript));
        decision.setCreatedAt(now); decision.setUpdatedAt(now);
        seeds.add(decision);

        // 取数卡产出变量 积分，比对卡直接消费它；比对卡自己生成最后一行脚本
        String compareScript = "积分 = 查询用户积分(user);\n返回 \"比对完成\";";
        Script compare = new Script();
        compare.setId("compare_demo");
        compare.setName("取数 + 比对演示（双卡）");
        compare.setContent(compareScript);
        compare.setDefaultParams(new java.util.LinkedHashMap<>(java.util.Collections.singletonMap("user", "张三")));
        compare.setMeta(buildDemoCompareMeta(compareScript));
        compare.setCreatedAt(now); compare.setUpdatedAt(now);
        seeds.add(compare);

        return seeds;
    }

    private Map<String, Object> buildDemoDecisionMeta(String script) {
        java.util.List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(card("c1", "call", fields("varName", "积分", "func", "查询用户积分", "param", "user")));
        cards.add(card("c2", "call", fields("varName", "等级", "func", "查询用户等级", "param", "user")));
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("cells", java.util.Arrays.asList("> 100", "== \"VIP\""));
        row1.put("result", "0.7");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("cells", java.util.Arrays.asList("> 50", ""));
        row2.put("result", "0.85");
        Map<String, Object> dtFields = new LinkedHashMap<>();
        dtFields.put("strategy", "first");
        dtFields.put("resultVar", "折扣");
        dtFields.put("conditions", java.util.Arrays.asList("积分", "等级"));
        dtFields.put("rows", java.util.Arrays.asList(row1, row2));
        dtFields.put("defaultValue", "1");
        cards.add(card("c3", "decisiontable", dtFields));
        cards.add(card("c4", "return", fields("expr", "折扣")));

        java.util.List<Map<String, Object>> sourceMap = new ArrayList<>();
        sourceMap.add(span("c1", "call", 1, 1));
        sourceMap.add(span("c2", "call", 2, 2));
        sourceMap.add(span("c3", "decisiontable", 3, 11));
        sourceMap.add(span("c4", "return", 12, 12));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cards", cards);
        meta.put("cardSourceMap", sourceMap);
        meta.put("contentHash", com.example.rulelog.util.ContentHash.hashContent(script));
        return meta;
    }

    private Map<String, Object> buildDemoCompareMeta(String script) {
        java.util.List<Map<String, Object>> cards = new ArrayList<>();
        // 取数卡：产出变量 积分，作为比对卡的输入
        cards.add(card("cc1", "call", fields("varName", "积分", "func", "查询用户积分", "param", "user")));

        // 取数A：直接引用上游取数卡的结果变量
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", "fa"); a.put("name", "取数A·上游积分"); a.put("sourceType", "CONTEXT");
        a.put("sourceName", "积分");
        a.put("paramMapping", new LinkedHashMap<>());
        a.put("resultKey", "A"); a.put("dependsOn", new ArrayList<>());

        // 取数B：调用业务函数拿折扣
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("id", "fb"); b.put("name", "取数B·折扣"); b.put("sourceType", "FUNCTION");
        b.put("sourceName", "计算折扣");
        b.put("paramMapping", fields("user", "${user}"));
        b.put("resultKey", "B"); b.put("dependsOn", new ArrayList<>());

        // 加工：A 和 B 相乘得到应付金额（DAG 依赖由 ${A}/${B} 自动推断）
        Map<String, Object> ab = new LinkedHashMap<>();
        ab.put("id", "fab"); ab.put("name", "加工·应付金额"); ab.put("sourceType", "DERIVE");
        ab.put("sourceName", "返回 a * b;");
        ab.put("paramMapping", fields("a", "${A}", "b", "${B}"));
        ab.put("resultKey", "AB"); ab.put("dependsOn", new ArrayList<>());

        // 取数C：期望值
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", "fc"); c.put("name", "取数C·期望"); c.put("sourceType", "CONST");
        c.put("sourceName", "334.4");
        c.put("paramMapping", new LinkedHashMap<>()); c.put("resultKey", "C");
        c.put("dependsOn", new ArrayList<>());

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("field", "value"); value.put("label", "应付金额");
        value.put("strategy", "numericTolerance"); value.put("tolerance", 0.01);
        Map<String, Object> cmp = new LinkedHashMap<>();
        cmp.put("keyField", "value");
        cmp.put("sourceKeys", java.util.Arrays.asList("AB", "C"));
        cmp.put("fields", java.util.Arrays.asList(value));
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", "积分×折扣 对账（A+B加工 → 对比C）");
        rule.put("fetchNodes", java.util.Arrays.asList(a, b, ab, c));
        rule.put("compareNode", cmp);
        Map<String, Object> cf = new LinkedHashMap<>();
        cf.put("contextJson", "{\"user\":\"张三\"}");
        cf.put("compareRule", rule);
        cards.add(card("cc2", "compare", cf));

        java.util.List<Map<String, Object>> sourceMap = new ArrayList<>();
        sourceMap.add(span("cc1", "call", 1, 1));
        sourceMap.add(span("cc2", "compare", 2, 2));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cards", cards);
        meta.put("cardSourceMap", sourceMap);
        meta.put("contentHash", com.example.rulelog.util.ContentHash.hashContent(script));
        return meta;
    }

    private Map<String, Object> card(String id, String type, Map<String, Object> fields) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id); c.put("type", type); c.putAll(fields);
        return c;
    }

    private Map<String, Object> fields(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private Map<String, Object> span(String cardId, String type, int start, int end) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cardId", cardId); m.put("type", type);
        m.put("startLine", start); m.put("endLine", end); m.put("empty", false);
        return m;
    }
}
