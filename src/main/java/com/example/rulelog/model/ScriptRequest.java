package com.example.rulelog.model;

import java.util.Map;

/** 脚本创建 / 修改请求体 */
public class ScriptRequest {

    private String name;

    private String content;

    private Map<String, Object> defaultParams;

    /** 附加元数据（卡片画布 sourceMap 等），平台只存储不解析 */
    private Map<String, Object> meta;

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
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
        this.defaultParams = defaultParams;
    }
}
