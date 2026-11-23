package com.example.rulelog.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误摘要单元测试：业务人员看不懂 QLExpress 的英文堆栈，
 * 平台要把执行错误翻成"第几行 + 出了什么错 + 出错行内容"，同时保留原始信息供开发排查。
 */
class ErrorSummarizerTest {

    private static final List<String> LINES = Arrays.asList(
            "user = '张三'",          // line 1
            "积分 = 查询用户积分(user)", // line 2
            "打折积分 = 积分 * 2",      // line 3
            "返回 打折积分"             // line 4
    );

    @Test
    void 空指针翻译为空值错误() {
        ErrorSummarizer.Summary s = ErrorSummarizer.summarize(
                new NullPointerException("Cannot invoke String.length() because ..."), LINES);
        assertEquals("空值错误", s.category);
    }

    @Test
    void 类型转换翻译为类型不匹配() {
        ErrorSummarizer.Summary s = ErrorSummarizer.summarize(
                new ClassCastException("java.lang.String cannot be cast to java.lang.Integer"), LINES);
        assertEquals("类型不匹配", s.category);
    }

    @Test
    void 变量或函数未定义() {
        ErrorSummarizer.Summary s = ErrorSummarizer.summarize(
                new RuntimeException("Function '查询积分' not found"), LINES);
        assertEquals("变量或函数未定义", s.category);
    }

    @Test
    void 未知异常翻译为规则运行错误() {
        ErrorSummarizer.Summary s = ErrorSummarizer.summarize(
                new RuntimeException("boom"), LINES);
        assertEquals("规则运行错误", s.category);
    }

    @Test
    void 提取行号并带出出错行内容() {
        ErrorSummarizer.Summary s = ErrorSummarizer.summarize(
                new RuntimeException("error at line 3: xxx"), LINES);
        assertEquals(Integer.valueOf(3), s.lineNo);
        assertTrue(s.detail.contains("打折积分 = 积分 * 2"), "应带出第3行的内容");
    }

    @Test
    void 行号超出脚本范围时行号置空() {
        ErrorSummarizer.Summary s = ErrorSummarizer.summarize(
                new RuntimeException("error at line 99: xxx"), LINES);
        assertNull(s.lineNo);
    }

    @Test
    void 无行号时行号置空() {
        ErrorSummarizer.Summary s = ErrorSummarizer.summarize(
                new RuntimeException("boom"), LINES);
        assertNull(s.lineNo);
    }

    @Test
    void 原始信息保留在摘要里便于开发排查() {
        String raw = "error at line 2: original cause";
        ErrorSummarizer.Summary s = ErrorSummarizer.summarize(
                new RuntimeException(raw), LINES);
        assertTrue(s.detail.contains(raw));
    }

    @Test
    void 行号为1时带出第一行内容() {
        ErrorSummarizer.Summary s = ErrorSummarizer.summarize(
                new RuntimeException("syntax error at line 1"), LINES);
        assertEquals(Integer.valueOf(1), s.lineNo);
        assertTrue(s.detail.contains("user = '张三'"));
    }

    @Test
    void 脚本行为空时也不报错() {
        ErrorSummarizer.Summary s = ErrorSummarizer.summarize(
                new RuntimeException("error at line 2: xxx"), null);
        assertNull(s.lineNo);
        assertEquals("规则运行错误", s.category);
    }
}
