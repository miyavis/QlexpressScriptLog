package com.example.rulelog.service;

import com.example.rulelog.model.Script;
import com.example.rulelog.model.ScriptVersion;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚本版本历史（纯逻辑，可单测）。
 *
 * 约定：脚本每次被修改前，先对"改之前的脚本"拍快照，新快照排在最前，
 * 只保留最近 {@link #MAX_KEEP} 个（防止 scripts.json 无限膨胀）。
 * 回滚 = 前端把某个版本的 name/content/defaultParams/meta 载入编辑器再保存——
 * 保存动作本身也会为"回滚前的那一刻"留快照，因此回滚永远可撤销。
 */
public final class ScriptVersionService {

    /** 每个脚本保留的版本数上限 */
    public static final int MAX_KEEP = 10;

    private ScriptVersionService() {
    }

    /** 对脚本当前内容拍快照 */
    public static ScriptVersion snapshot(Script script) {
        ScriptVersion v = new ScriptVersion();
        v.setSavedAt(System.currentTimeMillis());
        v.setName(script.getName());
        v.setContent(script.getContent());
        v.setDefaultParams(script.getDefaultParams());
        v.setMeta(script.getMeta());
        return v;
    }

    /**
     * 记录一次修改前的快照：新版本插到最前，超出上限丢弃最旧的。
     *
     * @param existing 已有版本列表（可为 null，视为空）
     * @param current  本次修改前的脚本（null 时原样返回，防御性）
     */
    public static List<ScriptVersion> record(List<ScriptVersion> existing, Script current) {
        List<ScriptVersion> out = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        if (current == null) {
            return out;
        }
        out.add(0, snapshot(current));
        if (out.size() > MAX_KEEP) {
            out = new ArrayList<>(out.subList(0, MAX_KEEP));
        }
        return out;
    }
}
