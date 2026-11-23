package com.example.rulelog.service;

import com.example.rulelog.util.ReviewWorkflow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 评审状态机测试：draft → review → published，驳回回到 draft。
 */
class ReviewWorkflowTest {

    @Test
    void validTransitions() {
        assertEquals("review", ReviewWorkflow.next("draft", ReviewWorkflow.Action.SUBMIT));
        assertEquals("published", ReviewWorkflow.next("review", ReviewWorkflow.Action.APPROVE));
        assertEquals("draft", ReviewWorkflow.next("review", ReviewWorkflow.Action.REJECT));
    }

    @Test
    void invalidTransitionsThrow() {
        // 草稿不能直接发布
        assertThrows(IllegalArgumentException.class,
                () -> ReviewWorkflow.next("draft", ReviewWorkflow.Action.APPROVE));
        // 已发布不能再提交评审
        assertThrows(IllegalArgumentException.class,
                () -> ReviewWorkflow.next("published", ReviewWorkflow.Action.SUBMIT));
    }

    @Test
    void unknownStatusOrActionHandled() {
        assertThrows(IllegalArgumentException.class,
                () -> ReviewWorkflow.next("weird", ReviewWorkflow.Action.SUBMIT));
        assertEquals(null, ReviewWorkflow.next("draft", null));
    }

    @Test
    void statusLabels() {
        assertEquals("草稿", ReviewWorkflow.label("draft"));
        assertEquals("评审中", ReviewWorkflow.label("review"));
        assertEquals("已发布", ReviewWorkflow.label("published"));
    }
}
