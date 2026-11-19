package com.example.rulelog.controller;

import com.example.rulelog.exception.NotFoundException;
import com.example.rulelog.exception.BadRequestException;
import com.example.rulelog.model.Script;
import com.example.rulelog.model.ScriptRequest;
import com.example.rulelog.model.ScriptVersion;
import com.example.rulelog.service.ScriptVersionService;
import com.example.rulelog.store.ScriptStore;
import com.example.rulelog.util.RequestValidator;
import com.example.rulelog.util.ReviewWorkflow;
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
        // 修改已发布/评审中的脚本 → 自动回到草稿（reopen），需要重新走评审流程
        if (!"draft".equals(existing.getReviewStatus())) {
            existing.setReviewStatus("draft");
        }
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

    /** 评审状态流转：submit(草稿→评审中) / approve(评审中→已发布) / reject(评审中→草稿) */
    @PostMapping("/{id}/review")
    public Script review(@PathVariable String id, @RequestParam String action) {
        Script script = scriptStore.get(id);
        if (script == null) {
            throw new NotFoundException("脚本不存在: " + id);
        }
        ReviewWorkflow.Action parsed;
        try {
            parsed = ReviewWorkflow.Action.valueOf(action.toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("未知评审动作: " + action);
        }
        String next = ReviewWorkflow.next(script.getReviewStatus(), parsed);
        script.setReviewStatus(next);
        // 发布时把当前版本标记为已发布版本
        if ("published".equals(next)) {
            script.setPublishedVersion(latestVersionNo(script));
        }
        scriptStore.put(script);
        return script;
    }

    /** 切换分支（分支不存在时创建新分支的标记；简化：仅改当前脚本的分支归属） */
    @PostMapping("/{id}/branch")
    public Script switchBranch(@PathVariable String id, @RequestParam String name) {
        Script script = scriptStore.get(id);
        if (script == null) {
            throw new NotFoundException("脚本不存在: " + id);
        }
        if (name == null || name.trim().isEmpty()) {
            throw new BadRequestException("分支名不能为空");
        }
        script.setBranch(name);
        script.setReviewStatus("draft"); // 切分支即回到草稿
        scriptStore.put(script);
        return script;
    }

    /** 当前最新版本版本号（无历史时返回 0） */
    private int latestVersionNo(Script script) {
        if (script.getVersions() == null) return 0;
        int max = 0;
        for (com.example.rulelog.model.ScriptVersion v : script.getVersions()) {
            if (v != null && v.getVersionNo() > max) max = v.getVersionNo();
        }
        return max;
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
