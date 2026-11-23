package com.example.rulelog.model;

import java.util.Map;

/** 脚本创建 / 修改请求体 */
public class ScriptRequest {

    private String name;

    private String content;

    private Map<String, Object> defaultParams;

    /** 附加元数据（卡片画布 sourceMap 等），平台只存储不解析 */
    private Map<String, Object> meta;

    /** 本次修改的备注（记录到版本快照，说明"为什么改"） */
    private String versionMessage;

    /** 部署环境：dev / staging / prod */
    private String environment;

    public String getVersionMessage() {
        return versionMessage;
    }

    public void setVersionMessage(String versionMessage) {
        this.versionMessage = versionMessage;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

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
