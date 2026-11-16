package com.example.rulelog.model;

import java.util.LinkedHashMap;
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
}
