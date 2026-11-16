package com.example.rulelog.controller;

import com.example.rulelog.exception.NotFoundException;
import com.example.rulelog.model.Script;
import com.example.rulelog.model.ScriptRequest;
import com.example.rulelog.service.ScriptVersionService;
import com.example.rulelog.store.ScriptStore;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 脚本管理：新增、修改、删除、列出脚本。
 * 脚本持久化为 data/scripts.json（由 ScriptStore 负责）。
 * 脚本执行统一走 {@link com.example.rulelog.controller.ExecutionController#run}。
 */
@RestController
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ScriptStore scriptStore;

    public ScriptController(ScriptStore scriptStore) {
        this.scriptStore = scriptStore;
    }

    /** 列出所有脚本（按更新时间倒序） */
    @GetMapping
    public List<Script> list() {
        return scriptStore.list();
    }

    /** 获取某脚本详情 */
    @GetMapping("/{id}")
    public Script get(@PathVariable String id) {
        Script script = scriptStore.get(id);
        if (script == null) {
            throw new NotFoundException("脚本不存在: " + id);
        }
        return script;
    }

    /** 新增脚本（id 由后端生成，持久化到 JSON 文件） */
    @PostMapping
    public Script create(@RequestBody ScriptRequest request) {
        String id = "script_" + System.currentTimeMillis();
        Script script = new Script();
        script.setId(id);
        script.setName(request.getName());
        script.setContent(request.getContent());
        script.setDefaultParams(request.getDefaultParams());
        script.setMeta(request.getMeta());
        long now = System.currentTimeMillis();
        script.setCreatedAt(now);
        script.setUpdatedAt(now);
        scriptStore.put(script);
        return script;
    }

    /** 修改脚本（修改前自动快照"改之前的版本"，供历史回滚） */
    @PutMapping("/{id}")
    public Script update(@PathVariable String id, @RequestBody ScriptRequest request) {
        Script existing = scriptStore.get(id);
        if (existing == null) {
            throw new NotFoundException("脚本不存在: " + id);
        }
        // 版本历史：先记录"改之前"的完整快照（含画布 meta），新快照在最前，最多保留 10 个
        existing.setVersions(ScriptVersionService.record(existing.getVersions(), existing));
        existing.setName(request.getName());
        existing.setContent(request.getContent());
        existing.setDefaultParams(request.getDefaultParams());
        existing.setMeta(request.getMeta());
        existing.setUpdatedAt(System.currentTimeMillis());
        scriptStore.put(existing);
        return existing;
    }

    /** 删除脚本 */
    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable String id) {
        boolean removed = scriptStore.remove(id);
        if (!removed) {
            throw new NotFoundException("脚本不存在: " + id);
        }
        return Collections.singletonMap("ok", true);
    }
}
