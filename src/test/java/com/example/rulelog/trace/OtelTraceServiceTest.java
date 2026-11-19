package com.example.rulelog.trace;

import com.example.rulelog.model.ServiceCallLog;
import com.example.rulelog.model.StepLog;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OTel 桥接测试：验证执行被映射为 span（traceId 与平台一致）、调用为 child span、步骤为 event。
 */
class OtelTraceServiceTest {

    /** 测试用 exporter：收集完成的 span */
    private static final class CollectingExporter implements SpanExporter {
        final List<SpanData> spans = new ArrayList<>();
        @Override
        public io.opentelemetry.sdk.common.CompletableResultCode export(java.util.Collection<SpanData> data) {
            spans.addAll(data);
            return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
        }
        @Override
        public io.opentelemetry.sdk.common.CompletableResultCode flush() {
            return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
        }
        @Override
        public io.opentelemetry.sdk.common.CompletableResultCode shutdown() {
            return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
        }
    }

    @Test
    void executionCreatesRootSpanWithPlatformTraceId() {
        CollectingExporter exporter = new CollectingExporter();
        OtelTraceService otel = new OtelTraceService("", exporter);

        String platformTraceId = "A1B2C3D4E5F60718";
        Context ctx = otel.startExecution(platformTraceId, "script_1");

        ServiceCallLog call = new ServiceCallLog("t", "UserService", "查询用户等级", "queryLevel", "[]", 100);
        call.finish("VIP");
        otel.recordCall(ctx, call);
        otel.recordStep(ctx, new StepLog("t", "UserService", "步骤1", "详情", 110));
        otel.endExecution(ctx, true, null);

        List<SpanData> spans = exporter.spans;
        assertEquals(2, spans.size(), "root span + 1 个调用 child span");
        // 所有 span 的 traceId 应与平台 traceId 对齐（补零后）
        for (SpanData s : spans) {
            assertEquals(platformTraceId.toLowerCase() + "0000000000000000", s.getTraceId());
        }
        assertTrue(spans.stream().anyMatch(s -> s.getName().equals("rule.execute")));
        assertTrue(spans.stream().anyMatch(s -> s.getName().equals("查询用户等级")));
        // root span 上应有步骤 event
        SpanData root = spans.stream().filter(s -> s.getName().equals("rule.execute")).findFirst().get();
        assertTrue(root.getEvents().stream().anyMatch(e -> e.getName().equals("步骤1")));
        otel.shutdown();
    }

    @Test
    void disabledByDefaultWhenNoEndpoint() {
        OtelTraceService otel = new OtelTraceService("");
        assertFalse(otel.isEnabled());
        otel.shutdown();
    }
}
