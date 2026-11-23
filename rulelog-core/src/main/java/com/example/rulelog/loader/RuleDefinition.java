package com.example.rulelog.loader;

import com.example.rulelog.param.ParamDef;

import java.util.ArrayList;
import java.util.List;

/** 加载到的规则定义：脚本文本 + 元信息（对应 QL_RULE 表一行 / 一个规则文件） */
public class RuleDefinition {

    private String id;
    private String name;
    private String content;       // 脚本文本
    private String description;
    private int version;
    private boolean enabled = true;
    private List<ParamDef> params = new ArrayList<>();

    public RuleDefinition() {
    }

    // ---------- getters / setters ----------
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<ParamDef> getParams() { return params; }
    public void setParams(List<ParamDef> params) { this.params = params == null ? new ArrayList<>() : params; }
}
