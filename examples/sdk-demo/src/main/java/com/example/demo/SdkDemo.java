package com.example.demo;

import com.example.rulelog.engine.Result;
import com.example.rulelog.engine.RuleEngine;
import com.example.rulelog.loader.JdbcRuleLoader;
import org.h2.jdbcx.JdbcDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

/**
 * 外部 Java 项目接入示例（等价于 JitPack 引入后的用法，本地用 mavenLocal 验证）。
 *
 * 演示：JdbcRuleLoader 连 QL_RULE 表 → RuleEngine 执行规则 → 拿 result + traceId。
 * 运行：./gradlew -p examples/sdk-demo run
 */
public class SdkDemo {

    public static void main(String[] args) throws Exception {
        // 1. 准备数据源（演示用 H2 内存库；生产换成业务库 DataSource 即可）
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:sdk_demo;DB_CLOSE_DELAY=-1");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE QL_RULE (RULE_ID VARCHAR(64) PRIMARY KEY, RULE_NAME VARCHAR(128), "
                    + "RULE_CONTENT CLOB, PARAM_DEF CLOB, VERSION INT, ENABLED BOOLEAN, DESCRIPTION VARCHAR(256), "
                    + "CREATED_AT BIGINT, UPDATED_AT BIGINT)");
            st.execute("INSERT INTO QL_RULE(RULE_ID,RULE_NAME,RULE_CONTENT,PARAM_DEF,VERSION,ENABLED) VALUES("
                    + "'points_double','积分翻倍','结果 = 积分 * 2; 返回 结果;',"
                    + "'[{\"name\":\"积分\",\"label\":\"积分\",\"type\":\"number\",\"required\":true}]',1,TRUE)");
        }

        // 2. 配置 SDK：规则来自数据库
        JdbcRuleLoader loader = JdbcRuleLoader.builder()
                .dataSource(ds)
                .cacheTtlMillis(30_000)
                .build();
        RuleEngine engine = RuleEngine.builder()
                .loader(loader)
                .timeout(5000)
                .build();

        // 3. 执行规则
        Result r = engine.runById("points_double", Map.of("积分", 100));

        // 4. 拿结果 + traceId（traceId 可回平台下钻完整执行日志）
        System.out.println("规则: points_double");
        System.out.println("成功: " + r.isSuccess());
        System.out.println("结果: " + r.getValue());          // 200
        System.out.println("traceId: " + r.getTraceId());

        if (!r.isSuccess() || !Integer.valueOf(200).equals(r.getValue())) {
            throw new AssertionError("SDK demo 验证失败: " + r.getError());
        }
        System.out.println("✅ SDK 外部接入验证通过");
    }
}
