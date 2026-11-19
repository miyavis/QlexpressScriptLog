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

    /** 应用所在目录：开发时=项目根，打包 jar 时=jar 所在目录；解析失败退回到当前工作目录 */
    private Path applicationHome() {
        try {
            Path codeLocation = Paths.get(
                    ScriptStore.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(codeLocation)) {
                // 开发环境（IDE / mvn spring-boot:run）：…/target/classes → 回退到项目根
                Path dir = codeLocation;
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if (name.equals("classes") || name.endsWith(".classes")) {
                    Path target = dir.getParent(); // …/target
                    if (target != null && target.getFileName() != null
                            && target.getFileName().toString().equals("target")) {
                        Path projectRoot = target.getParent();
                        if (projectRoot != null) {
                            return projectRoot;
                        }
                    }
                }
                return dir;
            }
            // 打包为 jar：文件所在目录
            return codeLocation.getParent() != null ? codeLocation.getParent() : Paths.get("").toAbsolutePath();
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
            synchronized (scripts) {
                scripts.clear();
                for (Script s : defaultScripts()) {
                    scripts.put(s.getId(), s);
                }
                save();
            }
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
            // 解析失败不让后端崩溃：备份坏文件 → 用内置示例脚本启动，日志给出明确指引
            Path backup = file.resolveSibling("scripts.json.bak-" + System.currentTimeMillis());
            try {
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveEx) {
                log.warn("备份损坏的脚本文件失败: {}", file.toAbsolutePath(), moveEx);
            }
            log.error("scripts.json 解析失败（可能是文件损坏/编码问题/手工编辑出错），"
                            + "坏文件已备份到: {} ；本次启动改用内置示例脚本。"
                            + "请检查文件后重命名回 scripts.json 再重启即可恢复。原文件: {}",
                    backup.toAbsolutePath(), file.toAbsolutePath(), e);
            synchronized (scripts) {
                scripts.clear();
                for (Script s : defaultScripts()) {
                    scripts.put(s.getId(), s);
                }
                save();
            }
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

        return seeds;
    }
}
