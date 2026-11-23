package com.example.rulelog.store;

import com.example.rulelog.compare.CompareNode;
import com.example.rulelog.compare.CompareRule;
import com.example.rulelog.compare.FetchNode;
import com.example.rulelog.compare.FieldCompare;
import com.example.rulelog.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 比对规则持久化：JSON 文件（data/compare-rules.json），模式与 ScriptStore 一致。 */
@Component
public class CompareRuleStore {

    private static final Logger log = LoggerFactory.getLogger(CompareRuleStore.class);
    private final Path storeFile;
    private final Map<String, CompareRule> rules = new LinkedHashMap<>();

    public CompareRuleStore(@Value("${app.data-dir:data}") String dataDir) {
        this.storeFile = resolve(dataDir).resolve("compare-rules.json");
        loadOrCreate();
    }

    private Path resolve(String dataDir) {
        Path configured = Paths.get(dataDir);
        if (configured.isAbsolute()) return configured;
        try {
            org.springframework.boot.system.ApplicationHome home =
                    new org.springframework.boot.system.ApplicationHome(CompareRuleStore.class);
            Path dir = home.getDir() == null ? null : home.getDir().toPath();
            if (dir != null) return dir.resolve(configured).normalize();
        } catch (Exception e) {
            // ignore
        }
        return Paths.get("").toAbsolutePath().resolve(configured).normalize();
    }

    private void loadOrCreate() {
        try {
            if (Files.exists(storeFile)) {
                String json = new String(Files.readAllBytes(storeFile), StandardCharsets.UTF_8);
                List<CompareRule> list = JsonUtils.mapper().readValue(json, new TypeReference<List<CompareRule>>() {});
                for (CompareRule r : list) rules.put(r.getId(), r);
                log.info("加载比对规则 {} 条: {}", rules.size(), storeFile.toAbsolutePath());
                return;
            }
        } catch (Exception e) {
            log.warn("读取比对规则文件失败，使用空列表: {}", e.getMessage());
        }
        rules.put(sample().getId(), sample());
        persist();
    }

    /** 内置示例：两段脚本取数 → 按订单号对齐比对金额/等级 */
    private CompareRule sample() {
        CompareRule rule = new CompareRule();
        rule.setId("cmp_sample");
        rule.setName("双源订单对账（示例）");
        rule.setDescription("示例：内置两份订单数据（取数A/取数B），按订单号对齐比对金额与等级；金额容差 2");
        long now = System.currentTimeMillis();
        rule.setCreatedAt(now); rule.setUpdatedAt(now);

        FetchNode f1 = new FetchNode();
        f1.setId("f1"); f1.setName("取数A"); f1.setSourceType("CONST");
        f1.setSourceName("[{\"订单号\":\"O001\",\"金额\":100,\"等级\":\"VIP\"},{\"订单号\":\"O002\",\"金额\":200,\"等级\":\"普通\"}]");
        f1.setResultKey("A");

        FetchNode f2 = new FetchNode();
        f2.setId("f2"); f2.setName("取数B"); f2.setSourceType("CONST");
        f2.setSourceName("[{\"订单号\":\"O001\",\"金额\":103,\"等级\":\"VIP\"},{\"订单号\":\"O002\",\"金额\":200,\"等级\":\"普通\"}]");
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

    public List<CompareRule> list() {
        return new ArrayList<>(rules.values());
    }

    public CompareRule get(String id) {
        return rules.get(id);
    }

    public CompareRule put(CompareRule rule) {
        if (rule.getId() == null || rule.getId().trim().isEmpty()) {
            rule.setId("cmp_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        rule.setUpdatedAt(System.currentTimeMillis());
        if (rule.getCreatedAt() == 0) rule.setCreatedAt(rule.getUpdatedAt());
        rules.put(rule.getId(), rule);
        persist();
        return rule;
    }

    public boolean remove(String id) {
        boolean removed = rules.remove(id) != null;
        if (removed) persist();
        return removed;
    }

    private void persist() {
        try {
            Files.createDirectories(storeFile.getParent());
            Path tmp = Files.createTempFile(storeFile.getParent(), "compare-", ".tmp");
            try {
                Files.write(tmp, JsonUtils.mapper().writeValueAsBytes(new ArrayList<>(rules.values())));
                try {
                    Files.move(tmp, storeFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    Files.move(tmp, storeFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("保存比对规则失败: " + storeFile.toAbsolutePath(), e);
        }
    }
}
