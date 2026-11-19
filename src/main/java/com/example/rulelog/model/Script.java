package com.example.rulelog.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则脚本：id 为英文短横线编号（用于 URL，业务人员不用记，用名称展示），
 * name / content / defaultParams 面向业务人员。
 */
public class Script {

    private String id;

    private String name;

    /** QLExpress4 脚本内容（支持中文关键字） */
    private String content;

    /** 默认执行参数，如 {"user": "张三"} */
    private Map<String, Object> defaultParams = new LinkedHashMap<>();

    /** 附加元数据（如卡片画布的 cardSourceMap / 卡片定义），平台只存储不解析 */
    private Map<String, Object> meta = new LinkedHashMap<>();

    private long createdAt;

    private long updatedAt;

    /** 历史版本（新→旧，最多 10 条）：每次修改前自动快照，回滚用 */
    private List<ScriptVersion> versions = new ArrayList<>();

    /** 已发布的版本号（对应 ScriptVersion.versionNo，-1 表示未发布） */
    private int publishedVersion = -1;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getDefaultParams() {
        return defaultParams;
    }

    public void setDefaultParams(Map<String, Object> defaultParams) {
        this.defaultParams = defaultParams == null ? new LinkedHashMap<>() : defaultParams;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta == null ? new LinkedHashMap<>() : meta;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ScriptVersion> getVersions() {
        return versions;
    }

    public void setVersions(List<ScriptVersion> versions) {
        this.versions = versions == null ? new ArrayList<>() : versions;
    }

    public int getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(int publishedVersion) {
        this.publishedVersion = publishedVersion;
    }
}
