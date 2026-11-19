package com.example.rulelog.controller;

import com.example.rulelog.exception.BadRequestException;
import com.example.rulelog.util.RequestValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 输入校验测试（P1）：脚本名/内容不能为 blank、不能超长；limit 必须为正数且有上限。
 */
class ScriptControllerValidationTest {

    @Test
    void blankNameOrContentRejected() {
        assertThrows(BadRequestException.class, () -> RequestValidator.validateScript(null, "返回 1;"));
        assertThrows(BadRequestException.class, () -> RequestValidator.validateScript("   ", "返回 1;"));
        assertThrows(BadRequestException.class, () -> RequestValidator.validateScript("脚本", null));
        assertThrows(BadRequestException.class, () -> RequestValidator.validateScript("脚本", "   "));
    }

    @Test
    void oversizedContentRejected() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= RequestValidator.MAX_CONTENT_LENGTH; i++) sb.append('x');
        assertThrows(BadRequestException.class, () -> RequestValidator.validateScript("脚本", sb.toString()));
    }

    @Test
    void validScriptPasses() {
        assertDoesNotThrow(() -> RequestValidator.validateScript("正常脚本", "返回 1;"));
    }

    @Test
    void limitIsBounded() {
        assertEquals(50, RequestValidator.normalizeLimit(50));
        assertEquals(200, RequestValidator.normalizeLimit(0));
        assertEquals(200, RequestValidator.normalizeLimit(-1));
        assertEquals(200, RequestValidator.normalizeLimit(5000));
    }

    @Test
    void executionParamsAreValidated() {
        java.util.Map<String, Object> ok = new java.util.LinkedHashMap<>();
        ok.put("user", "张三");
        assertDoesNotThrow(() -> RequestValidator.validateParams(ok));
        // null 参数合法（用脚本默认值）
        assertDoesNotThrow(() -> RequestValidator.validateParams(null));
        // key 超长
        java.util.Map<String, Object> longKey = new java.util.LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= RequestValidator.MAX_PARAM_KEY_LENGTH; i++) sb.append('k');
        longKey.put(sb.toString(), "v");
        assertThrows(BadRequestException.class, () -> RequestValidator.validateParams(longKey));
    }
}
