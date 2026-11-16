package com.example.rulelog.service;

import com.example.rulelog.model.Script;
import com.example.rulelog.model.ScriptVersion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脚本版本历史单元测试：业务人员改坏脚本要能反悔——
 * 每次修改前自动快照，保留最近 N 个版本，随脚本一起持久化。
 */
class ScriptVersionServiceTest {

    private Script script(String name, String content) {
        Script s = new Script();
        s.setId("script_1");
        s.setName(name);
        s.setContent(content);
        return s;
    }

    @Test
    void 快照记录脚本的名称内容与默认参数() {
        Script s = script("打折规则", "返回 1");
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("user", "张三");
        s.setDefaultParams(params);

        ScriptVersion v = ScriptVersionService.snapshot(s);
        assertEquals("打折规则", v.getName());
        assertEquals("返回 1", v.getContent());
        assertNotNull(v.getSavedAt());
        assertEquals("张三", v.getDefaultParams().get("user"));
    }

    @Test
    void 记录时新版本排在最前() {
        List<ScriptVersion> existing = new ArrayList<>();
        existing.add(ScriptVersionService.snapshot(script("v1旧", "c1")));

        List<ScriptVersion> out = ScriptVersionService.record(existing, script("当前", "c2"));
        assertEquals(2, out.size());
        assertEquals("当前", out.get(0).getName());
        assertEquals("v1旧", out.get(1).getName());
    }

    @Test
    void 版本数量超过上限时丢弃最旧的() {
        List<ScriptVersion> existing = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            existing = ScriptVersionService.record(existing, script("v" + i, "c" + i));
        }
        assertEquals(10, existing.size());
        assertEquals("v11", existing.get(0).getName());
        assertEquals("v2", existing.get(existing.size() - 1).getName());
    }

    @Test
    void 已有版本列表为空或null时也能记录() {
        assertEquals(1, ScriptVersionService.record(null, script("a", "c")).size());
        assertEquals(1, ScriptVersionService.record(new ArrayList<>(), script("a", "c")).size());
    }

    @Test
    void 快照携带meta供画布原样回滚() {
        Script s = script("a", "返回 1");
        s.getMeta().put("cards", new ArrayList<>());
        ScriptVersion v = ScriptVersionService.snapshot(s);
        assertTrue(v.getMeta().containsKey("cards"));
    }

    @Test
    void 当前脚本为null时返回原列表() {
        List<ScriptVersion> existing = new ArrayList<>();
        existing.add(ScriptVersionService.snapshot(script("a", "c")));
        assertEquals(1, ScriptVersionService.record(existing, null).size());
    }
}
