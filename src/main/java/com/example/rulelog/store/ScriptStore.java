package com.example.rulelog.store;

import com.example.rulelog.model.Script;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本持久化：JSON 文件（data/scripts.json）。
 *
 * 每次增删改立即落盘；首次启动无文件时写入内置示例脚本。
 * 注意：data-dir 为相对路径时按“后端启动时的工作目录”解析，
 * 换机器/换目录启动时如希望固定位置，请在 application.yml 配置绝对路径。
 */
@Component
public class ScriptStore {

    private static final Logger log = LoggerFactory.getLogger(ScriptStore.class);

    private final Path dataDir;

    private final Map<String, Script> scripts = new LinkedHashMap<>();

    public ScriptStore(@Value("${app.data-dir:data}") String dataDir) {
        this.dataDir = resolveDataDir(dataDir);
        log.info("脚本数据目录（解析后绝对路径）: {}", getStoreFile().toAbsolutePath());
        loadOrCreate();
    }

    // ---------- 路径解析 ----------

    /**
     * 解析数据目录：
     * - 绝对路径：直接使用；
     * - 相对路径：锚定“应用所在目录”——开发运行时=项目根目录（target 的上一级），
     *   打成 jar 运行时=jar 所在目录。这样项目拷到任何机器/任何目录，data 相对路径都有效，
     *   不再依赖启动命令时的工作目录。
     */
    private Path resolveDataDir(String dataDir) {
        Path configured = Paths.get(dataDir);
        if (configured.isAbsolute()) {
            return configured;
        }
        return applicationHome().resolve(configured).normalize();
    }

    /** 应用所在目录：开发时=项目根，打包 jar 时=jar 所在目录；解析失败退回到当前工作目录。
     *  用 Spring Boot 的 ApplicationHome 定位——它正确处理可执行 jar（含 Boot3 的嵌套 jar/nested URI），
     *  直接用 getProtectionDomain().getCodeSource() 在 Boot3 的 jar 里会返回 zipfs URI 导致 ProviderMismatchException。 */
    private Path applicationHome() {
        try {
            org.springframework.boot.system.ApplicationHome home =
                    new org.springframework.boot.system.ApplicationHome(ScriptStore.class);
            Path dir = home.getDir() == null ? null : home.getDir().toPath();
            if (dir == null) {
                return Paths.get("").toAbsolutePath();
            }
            String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
            // 开发环境（IDE / bootRun）：…/build/classes/java/main（或 target/classes）→ 回退到项目根
            if (name.equals("classes") || name.endsWith(".classes")) {
                Path parent = dir.getParent();
                if (parent != null && parent.getFileName() != null
                        && (parent.getFileName().toString().equals("target")
                            || parent.getFileName().toString().equals("java"))) {
                    Path root = parent.getFileName().toString().equals("java")
                            ? parent.getParent().getParent().getParent() // build/classes/java/main → 项目根
                            : parent.getParent();                          // target/classes → 项目根
                    if (root != null) return root;
                }
            }
            return dir;
        } catch (Exception e) {
            return Paths.get("").toAbsolutePath();
        }
    }

    public List<Script> list() {
        synchronized (scripts) {
            List<Script> all = new ArrayList<>(scripts.values());
            all.sort(Comparator.comparingLong(Script::getUpdatedAt).reversed());
            return all;
        }
    }

    public Script get(String id) {
        synchronized (scripts) {
            return scripts.get(id);
        }
    }

    public void put(Script script) {
        synchronized (scripts) {
            scripts.put(script.getId(), script);
            save();
        }
    }

    public boolean remove(String id) {
        synchronized (scripts) {
            boolean removed = scripts.remove(id) != null;
            if (removed) {
                save();
            }
            return removed;
        }
    }

    // ---------- 内部 ----------

    private void loadOrCreate() {
        Path file = getStoreFile();
        if (!Files.exists(file)) {
            log.info("脚本文件不存在，写入内置示例脚本: {}", file.toAbsolutePath());
            loadDefaults();
            return;
        }
        try {
            List<Script> loaded = JsonUtils.mapper()
                    .readValue(file.toFile(), new TypeReference<List<Script>>() {
                    });
            synchronized (scripts) {
                scripts.clear();
                for (Script s : loaded) {
                    scripts.put(s.getId(), s);
                }
            }
            log.info("已加载 {} 个脚本: {}", loaded.size(), file.toAbsolutePath());
        } catch (Exception e) {
            handleCorruptedFile(file, e);
        }
    }

    /** 文件损坏：备份坏文件并改用内置示例启动，日志给出恢复指引 */
    private void handleCorruptedFile(Path file, Exception cause) {
        Path backup = file.resolveSibling("scripts.json.bak-" + System.currentTimeMillis());
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveEx) {
            log.warn("备份损坏的脚本文件失败: {}", file.toAbsolutePath(), moveEx);
        }
        log.error("scripts.json 解析失败（可能是文件损坏/编码问题/手工编辑出错），"
                        + "坏文件已备份到: {} ；本次启动改用内置示例脚本。"
                        + "请检查文件后重命名回 scripts.json 再重启即可恢复。原文件: {}",
                backup.toAbsolutePath(), file.toAbsolutePath(), cause);
        loadDefaults();
    }

    /** 清空并用内置示例脚本填充内存，随即落盘 */
    private void loadDefaults() {
        synchronized (scripts) {
            scripts.clear();
            for (Script s : defaultScripts()) {
                scripts.put(s.getId(), s);
            }
            save();
        }
    }

    private void save() {
        Path file = getStoreFile();
        try {
            Files.createDirectories(file.getParent());
            List<Script> snapshot;
            synchronized (scripts) {
                snapshot = new ArrayList<>(scripts.values());
            }
            String json = JsonUtils.pretty(snapshot);
            // 原子写：先写同目录临时文件，再 ATOMIC_MOVE 覆盖目标。
            // 避免写到一半崩溃/断电留下半截 JSON，下次启动误判为损坏而丢全量脚本。
            Path tmp = Files.createTempFile(file.getParent(), "scripts-", ".tmp");
            try {
                Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    // 个别文件系统不支持原子移动，降级为普通覆盖（仍比直接写目标安全）
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("保存脚本文件失败: " + file.toAbsolutePath(), e);
        }
    }

    private Path getStoreFile() {
        return dataDir.resolve("scripts.json");
    }

    private List<Script> defaultScripts() {
        long now = System.currentTimeMillis();
        List<Script> seeds = new ArrayList<>();

        Script vip = new Script();
        vip.setId("vip_discount");
        vip.setName("VIP 折扣判定");
        vip.setContent("等级 = 查询用户等级(user);\n"
                + "记录日志(\"等级查询完成\", \"等级=\" + 等级);\n"
                + "\n"
                + "如果 (等级 等于 \"VIP\") 则 {\n"
                + "    记录日志(\"进入VIP分支\", \"准备调用折扣服务\");\n"
                + "    返回 计算折扣(user);\n"
                + "} 否则 {\n"
                + "    记录日志(\"进入普通分支\", \"非VIP用户不打折\");\n"
                + "    返回 0;\n"
                + "}");
        vip.getDefaultParams().put("user", "张三");
        vip.setCreatedAt(now);
        vip.setUpdatedAt(now);
        seeds.add(vip);

        Script points = new Script();
        points.setId("points_double");
        points.setName("积分翻倍");
        points.setContent("积分 = 查询用户积分(user);\n"
                + "记录日志(\"积分查询完成\", \"积分=\" + 积分);\n"
                + "\n"
                + "如果 (积分 大于 100) 则 {\n"
                + "    打折积分 = 积分 * 2;\n"
                + "} 否则 {\n"
                + "    打折积分 = 积分;\n"
                + "}\n"
                + "\n"
                + "返回 打折积分;");
        points.getDefaultParams().put("user", "张三");
        points.setCreatedAt(now);
        points.setUpdatedAt(now);
        seeds.add(points);

        // 多行卡样例：一张「带参取数卡」生成两行（参数定义行 + 服务调用行）
        Script prepCall = new Script();
        prepCall.setId("prepcall_demo");
        prepCall.setName("带参取数演示（一卡两行）");
        prepCall.setContent("查询用户 = user;\n"
                + "积分 = 查询用户积分(查询用户);\n"
                + "记录日志(\"积分查询完成\", \"积分=\" + 积分);\n"
                + "\n"
                + "如果 (积分 大于 100) 则 {\n"
                + "    折扣依据 = user;\n"
                + "    折扣 = 计算折扣(折扣依据);\n"
                + "    返回 积分 * 折扣;\n"
                + "} 否则 {\n"
                + "    记录日志(\"未达门槛\", \"积分不足100\");\n"
                + "    返回 积分;\n"
                + "}");
        prepCall.getDefaultParams().put("user", "张三");
        prepCall.setCreatedAt(now);
        prepCall.setUpdatedAt(now);
        seeds.add(prepCall);

        // 决策表演示：画布模式直接显示一张决策表卡（带完整 meta，contentHash 匹配才不会退化成普通卡）
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
        decision.setCreatedAt(now);
        decision.setUpdatedAt(now);
        seeds.add(decision);

        return seeds;
    }

    /** 构造决策表演示的画布 meta：4 张卡 + 行号映射 + contentHash（与前端一致） */
    private Map<String, Object> buildDemoDecisionMeta(String script) {
        java.util.List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(card("c1", "call", fields("varName", "积分", "func", "查询用户积分", "param", "user")));
        cards.add(card("c2", "call", fields("varName", "等级", "func", "查询用户等级", "param", "user")));
        // 决策表卡：First 策略，两条件两行
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

    private Map<String, Object> card(String id, String type, Map<String, Object> fields) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id);
        c.put("type", type);
        c.putAll(fields);
        return c;
    }

    /** 简易 key-value 构造（值成对传入） */
    private Map<String, Object> fields(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private Map<String, Object> span(String cardId, String type, int start, int end) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cardId", cardId);
        m.put("type", type);
        m.put("startLine", start);
        m.put("endLine", end);
        m.put("empty", false);
        return m;
    }
}
