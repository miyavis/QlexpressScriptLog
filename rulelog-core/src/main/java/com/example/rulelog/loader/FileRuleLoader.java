package com.example.rulelog.loader;

import com.example.rulelog.param.ParamDef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件规则加载器：从文件系统目录或 ClassPath 加载 .rule 文件。
 *
 * 文件格式（纯脚本文本，文件名即规则 ID）：
 *   rules/vip_discount.rule  → 规则 ID "vip_discount"
 * 可选同名 .meta.json 提供 name/desc/params：
 *   rules/vip_discount.meta.json
 */
public class FileRuleLoader implements RuleLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path baseDir;          // 文件系统目录（二选一）
    private final String classPathPrefix; // ClassPath 前缀（二选一）

    private FileRuleLoader(Path baseDir, String classPathPrefix) {
        this.baseDir = baseDir;
        this.classPathPrefix = classPathPrefix;
    }

    /** 从文件系统目录加载 */
    public static FileRuleLoader fromDirectory(String dir) {
        return new FileRuleLoader(Paths.get(dir), null);
    }

    /** 从 ClassPath 加载（如 "rules/"） */
    public static FileRuleLoader fromClassPath(String prefix) {
        return new FileRuleLoader(null, prefix);
    }

    @Override
    public RuleDefinition load(String ruleId) {
        String script = readText(ruleId + ".rule");
        if (script == null) return null;
        RuleDefinition def = new RuleDefinition();
        def.setId(ruleId);
        def.setName(ruleId);
        def.setContent(script);
        // 可选的 meta.json
        String meta = readText(ruleId + ".meta.json");
        if (meta != null) {
            try {
                JsonNode node = MAPPER.readTree(meta);
                if (node.has("name")) def.setName(node.get("name").asText());
                if (node.has("desc")) def.setDescription(node.get("desc").asText());
                if (node.has("version")) def.setVersion(node.get("version").asInt());
                if (node.has("params") && node.get("params").isArray()) {
                    List<ParamDef> params = new ArrayList<>();
                    for (JsonNode p : node.get("params")) {
                        params.add(parseParam(p));
                    }
                    def.setParams(params);
                }
            } catch (Exception e) {
                // meta 解析失败不影响规则加载
            }
        }
        return def;
    }

    @Override
    public List<String> listRuleIds() {
        List<String> ids = new ArrayList<>();
        if (baseDir != null && Files.isDirectory(baseDir)) {
            try (java.util.stream.Stream<Path> files = Files.list(baseDir)) {
                files.filter(p -> p.toString().endsWith(".rule"))
                        .forEach(p -> {
                            String n = p.getFileName().toString();
                            ids.add(n.substring(0, n.length() - 5));
                        });
            } catch (IOException e) {
                // ignore
            }
        }
        // ClassPath 模式无法枚举目录，返回空（调用方按约定 ID 加载）
        return ids;
    }

    private String readText(String fileName) {
        try {
            if (baseDir != null) {
                Path f = baseDir.resolve(fileName);
                return Files.exists(f) ? new String(Files.readAllBytes(f), StandardCharsets.UTF_8) : null;
            }
            if (classPathPrefix != null) {
                String resource = classPathPrefix + (classPathPrefix.endsWith("/") ? "" : "/") + fileName;
                InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
                if (in == null) in = FileRuleLoader.class.getResourceAsStream("/" + resource);
                if (in == null) return null;
                try (InputStream stream = in) {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private ParamDef parseParam(JsonNode node) {
        ParamDef def = new ParamDef(
                node.has("name") ? node.get("name").asText() : "",
                node.has("label") ? node.get("label").asText() : "",
                node.has("type") ? node.get("type").asText() : "string");
        if (node.has("desc")) def.setDesc(node.get("desc").asText());
        if (node.has("required")) def.setRequired(node.get("required").asBoolean());
        if (node.has("fields") && node.get("fields").isArray()) {
            List<ParamDef> children = new ArrayList<>();
            for (JsonNode c : node.get("fields")) children.add(parseParam(c));
            def.setFields(children);
        }
        if (node.has("element") && node.get("element").isObject()) {
            def.setElement(parseParam(node.get("element")));
        }
        return def;
    }
}
