package com.example.rulelog.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * traceId 贯穿核心机制单测：StepLogger 采集 + RuleTraceContext ThreadLocal。
 */
class TraceContextTest {

    @AfterEach
    void cleanup() {
        RuleTraceContext.clear();
        StepLogger.drainAndRemove();
    }

    @Test
    void ruleTraceContextSetGetClear() {
        assertNull(RuleTraceContext.get());
        RuleTraceContext.set("TRACE-123");
        assertEquals("TRACE-123", RuleTraceContext.get());
        RuleTraceContext.clear();
        assertNull(RuleTraceContext.get());
    }

    @Test
    void stepLoggerCollectsRootAndNestedCalls() {
        StepLogger.startTrace("T1");
        assertTrue(StepLogger.isActive());
        StepLogger.beginCall("UserService", "queryLevel", "[\"张三\"]", "查询用户等级");
        StepLogger.log("步骤1", "详情1"); // 归入最内层调用
        StepLogger.endCall("VIP");
        StepLogger.log("根步骤", "根详情"); // 调用结束后归入根
        StepLogger.Collector c = StepLogger.drainAndRemove();

        assertFalse(StepLogger.isActive(), "drain 后应清理 ThreadLocal");
        assertEquals(1, c.getRootCalls().size());
        assertEquals("查询用户等级", c.getRootCalls().get(0).getChineseName());
        assertEquals(1, c.getRootCalls().get(0).getInternalSteps().size()); // 步骤1
        assertEquals("步骤1", c.getRootCalls().get(0).getInternalSteps().get(0).getStepName());
        assertEquals(1, c.getRootSteps().size()); // 根步骤
        assertEquals("根步骤", c.getRootSteps().get(0).getStepName());
        assertEquals("T1", c.getRootCalls().get(0).getTraceId());
    }

    @Test
    void logWithoutTraceIsNoop() {
        // 未 startTrace 时 log/beginCall 不应抛异常
        assertDoesNotThrow(() -> StepLogger.log("x", "y"));
        assertDoesNotThrow(() -> StepLogger.beginCall("A", "b", "[]", null));
    }
}
