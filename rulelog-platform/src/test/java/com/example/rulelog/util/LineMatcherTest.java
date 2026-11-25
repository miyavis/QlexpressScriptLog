package com.example.rulelog.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 重复调用归属行测试：同一函数在同一脚本出现多次时，按出现顺序依次分配行号，
 * 避免所有调用都堆到第一行（P1 行号归属修正）。
 */
class LineMatcherTest {

    @Test
    void repeatedNameConsumesNextMatchEachTime() {
        String[] lines = {
            "等级 = 查询用户等级(user);",
            "记录日志(\"x\", 等级);",
            "再查 = 查询用户等级(user);"
        };
        LineMatcher matcher = new LineMatcher(lines);
        assertEquals(1, matcher.next("查询用户等级"));
        assertEquals(3, matcher.next("查询用户等级"));
        assertNull(matcher.next("查询用户等级"), "只有两处，第三次无匹配");
    }

    @Test
    void differentNamesIndependent() {
        String[] lines = {
            "a = 查询用户等级(user);",
            "b = 计算折扣(user);",
            "c = 查询用户等级(user);"
        };
        LineMatcher matcher = new LineMatcher(lines);
        assertEquals(1, matcher.next("查询用户等级"));
        assertEquals(2, matcher.next("计算折扣"));
        assertEquals(3, matcher.next("查询用户等级"));
    }

    @Test
    void nullNameSafe() {
        LineMatcher matcher = new LineMatcher(new String[]{"x = 1;"});
        assertNull(matcher.next(null));
    }

    @Test
    void rangesExcludeLookalikeLinesOutsideCards() {
        String[] lines = {
            "记录日志(\"查询用户等级\", \"伪调用\");", // 字符串里的同名，不属于卡片区间
            "等级 = 查询用户等级(user);",
            "返回 等级;"
        };
        List<int[]> ranges = Arrays.asList(new int[]{2, 3});
        LineMatcher matcher = new LineMatcher(lines, ranges);
        assertEquals(2, matcher.next("查询用户等级"), "只在实际调用卡的行区间内匹配");
        assertNull(matcher.next("查询用户等级"));
    }
}
