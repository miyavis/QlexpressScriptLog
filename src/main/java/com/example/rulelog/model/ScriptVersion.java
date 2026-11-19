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

    /** 递增版本号（每次修改前 +1） */
    private int versionNo;

    /** 保存备注（可选，业务人员填写"为什么改"） */
    private String message;

    /** 保存人（默认 "user"，预留接入鉴权后替换） */
    private String author;

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

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
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
