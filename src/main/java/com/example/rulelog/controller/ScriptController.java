package com.example.rulelog.controller;

import com.example.rulelog.exception.NotFoundException;
import com.example.rulelog.model.Script;
import com.example.rulelog.model.ScriptRequest;
import com.example.rulelog.model.ScriptVersion;
import com.example.rulelog.service.ScriptVersionService;
import com.example.rulelog.store.ScriptStore;
import com.example.rulelog.util.RequestValidator;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    /** 新增脚本（id 用 UUID，避免同毫秒连续创建碰撞覆盖；持久化到 JSON 文件） */
    @PostMapping
    public Script create(@RequestBody ScriptRequest request) {
        validate(request);
        Script script = new Script();
        script.setId("script_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        applyRequest(script, request);
        long now = System.currentTimeMillis();
        script.setCreatedAt(now);
        script.setUpdatedAt(now);
        scriptStore.put(script);
        return script;
    }

    /** 修改脚本（修改前自动快照"改之前的版本"，供历史回滚） */
    @PutMapping("/{id}")
    public Script update(@PathVariable String id, @RequestBody ScriptRequest request) {
        validate(request);
        Script existing = scriptStore.get(id);
        if (existing == null) {
            throw new NotFoundException("脚本不存在: " + id);
        }
        // 版本历史：先记录"改之前"的完整快照（含画布 meta），新快照在最前，最多保留 10 个
        List<ScriptVersion> versions = ScriptVersionService.record(existing.getVersions(), existing);
        if (versions != null && !versions.isEmpty() && request.getVersionMessage() != null) {
            versions.get(0).setMessage(request.getVersionMessage());
        }
        existing.setVersions(versions);
        applyRequest(existing, request);
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

    /** 将某个历史版本标记为"已发布"（versionNo 不存在时报错） */
    @PostMapping("/{id}/publish")
    public Script publish(@PathVariable String id, @RequestParam int versionNo) {
        Script script = scriptStore.get(id);
        if (script == null) {
            throw new NotFoundException("脚本不存在: " + id);
        }
        boolean exists = script.getVersions().stream().anyMatch(v -> v.getVersionNo() == versionNo);
        if (!exists) {
            throw new com.example.rulelog.exception.BadRequestException("版本 " + versionNo + " 不存在");
        }
        script.setPublishedVersion(versionNo);
        scriptStore.put(script);
        return script;
    }

    private void validate(ScriptRequest request) {
        if (request == null) {
            throw new com.example.rulelog.exception.BadRequestException("请求体不能为空");
        }
        RequestValidator.validateScript(request.getName(), request.getContent());
    }

    private void applyRequest(Script script, ScriptRequest request) {
        script.setName(request.getName());
        script.setContent(request.getContent());
        script.setDefaultParams(request.getDefaultParams());
        script.setMeta(request.getMeta());
    }
}
