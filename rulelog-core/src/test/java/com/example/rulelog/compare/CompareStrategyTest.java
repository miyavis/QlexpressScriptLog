package com.example.rulelog.compare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompareStrategyTest {

    @Test
    void equalComparesStringAndNumber() {
        assertTrue(CompareStrategy.compare("VIP", "VIP", "equal", 0).isConsistent());
        assertFalse(CompareStrategy.compare("VIP", "普通", "equal", 0).isConsistent());
        assertTrue(CompareStrategy.compare(100, 100, "equal", 0).isConsistent());
        // null 与空串视为一致
        assertTrue(CompareStrategy.compare(null, "", "equal", 0).isConsistent());
    }

    @Test
    void numericToleranceRespectsTolerance() {
        assertTrue(CompareStrategy.compare(100, 105, "numericTolerance", 5).isConsistent());
        assertTrue(CompareStrategy.compare(100, 105, "numericTolerance", 10).isConsistent());
        assertFalse(CompareStrategy.compare(100, 106, "numericTolerance", 5).isConsistent());
        // 字符串数字也能比
        assertTrue(CompareStrategy.compare("100", "103", "numericTolerance", 5).isConsistent());
    }

    @Test
    void comparisonStrategies() {
        assertTrue(CompareStrategy.compare(200, 100, "gt", 0).isConsistent());
        assertFalse(CompareStrategy.compare(50, 100, "gt", 0).isConsistent());
        assertTrue(CompareStrategy.compare(100, 200, "lt", 0).isConsistent());
        assertTrue(CompareStrategy.compare("VIP用户", "VIP", "contains", 0).isConsistent());
        assertFalse(CompareStrategy.compare("普通用户", "VIP", "contains", 0).isConsistent());
    }

    @Test
    void notEqualStrategy() {
        assertTrue(CompareStrategy.compare("A", "B", "notEqual", 0).isConsistent());
        assertFalse(CompareStrategy.compare("A", "A", "notEqual", 0).isConsistent());
    }
}
