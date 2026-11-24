package com.example.rulelog.compare;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 取数节点：调用一个已注册函数或脚本规则拿数据。
 * - sourceType: FUNCTION（系统函数）/ SCRIPT（脚本规则）
 * - sourceName: 函数名或规则 ID
 * - paramMapping: 参数名 → ${路径} 表达式（从运行页上下文取值）
 * - resultKey: 该节点产出数据存入上下文的 key（后续比对节点引用）
 */
public class FetchNode {

    private String id;
    private String name;              // 节点显示名，如"取数A"
    private String sourceType = "FUNCTION";
    private String sourceName;
    private Map<String, String> paramMapping = new LinkedHashMap<>();
    private String resultKey;         // 产出数据存的 key
    /** 显式依赖的其他取数节点 ID（可选； paramMapping 引用了某结果 Key 时会自动推断依赖） */
    private java.util.List<String> dependsOn = new java.util.ArrayList<>();

    public FetchNode() {
    }

    // ---------- getters / setters ----------
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public Map<String, String> getParamMapping() { return paramMapping; }
    public void setParamMapping(Map<String, String> paramMapping) {
        this.paramMapping = paramMapping == null ? new LinkedHashMap<>() : paramMapping;
    }
    public String getResultKey() { return resultKey; }
    public void setResultKey(String resultKey) { this.resultKey = resultKey; }
    public java.util.List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(java.util.List<String> dependsOn) {
        this.dependsOn = dependsOn == null ? new java.util.ArrayList<>() : dependsOn;
    }
}
