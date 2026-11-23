package com.example.rulelog.express;

import com.alibaba.qlexpress4.QLResult;
import com.example.rulelog.biz.DiscountService;
import com.example.rulelog.biz.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 引擎安全策略测试：
 * 1. 白名单策略下，注册的业务函数与普通表达式仍正常执行（不误伤业务脚本）
 * 2. 白名单策略下，脚本访问 Java 类/系统资源被拒绝（修复 RCE）
 */
class Express4EngineTest {

    private Express4Engine engine;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService();
        engine = new Express4Engine(userService, new DiscountService(userService));
    }

    /** 业务脚本（含中文函数、字符串拼接、算术）在白名单下仍能执行 */
    @Test
    void businessScriptStillWorksUnderWhitelist() throws Exception {
        Map<String, Object> context = new HashMap<>();
        context.put("user", "张三");
        QLResult r = engine.execute("等级 = 查询用户等级(user);\n返回 等级;", context, 5000);
        assertEquals("VIP", r.getResult());
    }

    /** 字符串拼接（记录日志常见写法）在白名单下不报错 */
    @Test
    void stringConcatWorksUnderWhitelist() throws Exception {
        Map<String, Object> context = new HashMap<>();
        context.put("user", "张三");
        QLResult r = engine.execute("返回 \"等级=\" + 查询用户等级(user);", context, 5000);
        assertEquals("等级=VIP", r.getResult());
    }

    /** 恶意脚本：尝试通过 java.lang.Runtime 执行系统命令，必须被拒绝 */
    @Test
    void javaClassAccessIsBlocked() {
        assertThrows(Exception.class, () ->
                engine.execute("返回 Runtime.getRuntime().exec(\"echo hacked\");",
                        new HashMap<>(), 5000));
    }
}
