package com.example.rulelog.service;

import com.alibaba.qlexpress4.exception.QLSyntaxException;
import com.example.rulelog.biz.DiscountService;
import com.example.rulelog.biz.UserService;
import com.example.rulelog.express.Express4Engine;
import com.example.rulelog.util.ScriptValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 保存前静态校验测试：语法错误前移 + 变量完整性（用到的变量是否在入参或脚本内赋值）。
 */
class ScriptValidationTest {

    private Express4Engine engine;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService();
        engine = new Express4Engine(userService, new DiscountService(userService));
    }

    @Test
    void syntaxErrorIsDetected() {
        ScriptValidator.Result r = ScriptValidator.validate(engine, "返回 1 +;", Collections.singleton("user"));
        assertFalse(r.valid());
        assertNotNull(r.message());
        assertTrue(r.message().contains("语法"));
    }

    @Test
    void validScriptPasses() {
        ScriptValidator.Result r = ScriptValidator.validate(
                engine, "等级 = 查询用户等级(user);\n返回 等级;", Collections.singleton("user"));
        assertTrue(r.valid(), r.message());
    }

    @Test
    void undefinedVariableIsDetected() {
        // 脚本用到 unknownVar，既不在入参里，脚本内也没赋值
        ScriptValidator.Result r = ScriptValidator.validate(
                engine, "返回 unknownVar + 1;", Collections.singleton("user"));
        assertFalse(r.valid());
        assertTrue(r.message().contains("unknownVar"));
    }

    @Test
    void assignedVariableIsNotFlagged() {
        // 变量在脚本内先赋值再用，不应报错
        ScriptValidator.Result r = ScriptValidator.validate(
                engine, "x = 1;\n返回 x + 1;", Collections.emptySet());
        assertTrue(r.valid(), r.message());
    }

    @Test
    void engineExposesAssignedVarNames() {
        // 4.1.4 新 API：提取脚本内被赋值的变量
        assertTrue(engine.getAssignedVarNames("x = 1; y = x + 1;").containsAll(
                new HashSet<>(Arrays.asList("x", "y"))));
    }
}
