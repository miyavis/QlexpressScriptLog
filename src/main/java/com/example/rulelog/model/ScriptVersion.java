package com.example.rulelog.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脚本历史版本：每次修改脚本前对"改之前的脚本"自动快照。
 * 保存名称/内容/默认参数/画布 meta——回滚时画布布局可原样恢复。
 */
public class ScriptVersion {

    /** 快照时间（毫秒时间戳） */
    private long savedAt;

    private String name;

    private String content;

    private Map<String, Object> defaultParams = new LinkedHashMap<>();

    private Map<String, Object> meta = new LinkedHashMap<>();

    public long getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(long savedAt) {
        this.savedAt = savedAt;
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
}
