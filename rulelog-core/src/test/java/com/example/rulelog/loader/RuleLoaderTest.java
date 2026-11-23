package com.example.rulelog.loader;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleLoaderTest {

    @Test
    void fileLoaderLoadsScriptAndMeta() throws Exception {
        Path dir = Files.createTempDirectory("rules");
        Files.writeString(dir.resolve("vip.rule"), "返回 查询用户等级(user);");
        Files.writeString(dir.resolve("vip.meta.json"),
                "{\"name\":\"VIP判定\",\"desc\":\"折扣判定\",\"params\":[{\"name\":\"user\",\"label\":\"用户名\",\"type\":\"string\",\"required\":true}]}");

        FileRuleLoader loader = FileRuleLoader.fromDirectory(dir.toString());
        RuleDefinition def = loader.load("vip");
        assertNotNull(def);
        assertEquals("VIP判定", def.getName());
        assertEquals("返回 查询用户等级(user);", def.getContent());
        assertEquals(1, def.getParams().size());
        assertEquals("用户名", def.getParams().get(0).getLabel());
        assertTrue(loader.listRuleIds().contains("vip"));
    }

    private JdbcDataSource ds() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:rules_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE QL_RULE (RULE_ID VARCHAR(64) PRIMARY KEY, RULE_NAME VARCHAR(128), "
                    + "RULE_CONTENT CLOB, PARAM_DEF CLOB, VERSION INT, ENABLED BOOLEAN, "
                    + "DESCRIPTION VARCHAR(256), CREATED_AT BIGINT, UPDATED_AT BIGINT)");
        }
        return ds;
    }

    @Test
    void jdbcLoaderLoadsFromTableAndCaches() throws Exception {
        JdbcDataSource ds = ds();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO QL_RULE(RULE_ID,RULE_NAME,RULE_CONTENT,VERSION,ENABLED) "
                    + "VALUES('r1','规则1','返回 1;',1,TRUE)");
        }
        JdbcRuleLoader loader = JdbcRuleLoader.builder().dataSource(ds).build();
        RuleDefinition def = loader.load("r1");
        assertNotNull(def);
        assertEquals("规则1", def.getName());
        assertEquals("返回 1;", def.getContent());

        // 缓存：改库后不 refresh 仍读旧值
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("UPDATE QL_RULE SET RULE_CONTENT='返回 2;' WHERE RULE_ID='r1'");
        }
        assertEquals("返回 1;", loader.load("r1").getContent(), "未 refresh 应命中缓存");
        loader.refresh("r1");
        assertEquals("返回 2;", loader.load("r1").getContent(), "refresh 后应读新值");

        assertEquals(1, loader.listRuleIds().size());
    }
}
