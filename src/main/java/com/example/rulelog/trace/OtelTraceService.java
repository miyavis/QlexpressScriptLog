package com.example.rulelog.trace;

import com.example.rulelog.model.RuleExecutionRecord;
import com.example.rulelog.model.ServiceCallLog;
import com.example.rulelog.model.StepLog;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * OpenTelemetry 桥接：把一次规则执行映射为 OTel trace。
 *
 * - 每次执行 = 一个 root span（traceId 与平台 traceId 对齐）
 * - 每个 ServiceCallLog = 一个 child span（带耗时/成败/参数属性）
 * - 每个 StepLog = root span 上的一个 event
 *
 * exporter 通过 app.otel.endpoint 配置：为空时使用 noop（不导出，零告警）；
 * 配置后使用 OTLP/HTTP exporter 推送到 collector。
 */
@Service
public class OtelTraceService {

    private static final Logger log = LoggerFactory.getLogger(OtelTraceService.class);
    private static final String INSTRUMENTATION = "rule-log";

    private final OtelIdGenerator idGenerator;
    private final Tracer tracer;
    private final OpenTelemetrySdk sdk;
    private final boolean enabled;

    @Autowired
    public OtelTraceService(@Value("${app.otel.endpoint:}") String endpoint) {
        this(endpoint, null);
    }

    /** 测试构造：注入自定义 exporter（如 InMemorySpanExporter） */
    public OtelTraceService(String endpoint, SpanExporter providedExporter) {
        this.idGenerator = new OtelIdGenerator();
        SpanExporter exporter;
        if (providedExporter != null) {
            exporter = providedExporter;
            this.enabled = false;
        } else if (endpoint != null && !endpoint.trim().isEmpty()) {
            exporter = io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter.builder()
                    .setEndpoint(endpoint.trim())
                    .build();
            this.enabled = true;
            log.info("OpenTelemetry OTLP 导出已启用: {}", endpoint.trim());
        } else {
            // noop exporter：span 正常创建但不导出，避免无 collector 的连接告警
            exporter = new SpanExporter() {
                @Override
                public io.opentelemetry.sdk.common.CompletableResultCode export(
                        java.util.Collection<io.opentelemetry.sdk.trace.data.SpanData> spans) {
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
            };
            this.enabled = false;
        }
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .setIdGenerator(idGenerator)
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        this.sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
        this.tracer = sdk.getTracer(INSTRUMENTATION);
    }

    /** 是否配置了 OTLP 导出（false 时 span 仍创建但不导出） */
    public boolean isEnabled() {
        return enabled;
    }

    /** 开始一次执行：设置 ID 生成器，创建 root span 并返回其 Context */
    public Context startExecution(String platformTraceId, String scriptId) {
        idGenerator.setPlatformTraceId(platformTraceId);
        Span root = tracer.spanBuilder("rule.execute")
                .setAttribute("rule.scriptId", scriptId == null ? "" : scriptId)
                .setAttribute("rule.traceId", platformTraceId == null ? "" : platformTraceId)
                .startSpan();
        return Context.root().with(root);
    }

    /** 结束一次执行：结束 root span 并清理 ThreadLocal */
    public void endExecution(Context rootContext, boolean success, String errorMessage) {
        Span root = Span.fromContext(rootContext);
        root.setAttribute("rule.success", success);
        if (!success && errorMessage != null) {
            root.setAttribute("rule.error", errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage);
            root.setStatus(StatusCode.ERROR);
        }
        root.end();
        idGenerator.clear();
    }

    /** 记录一个服务调用为 child span（递归处理子调用） */
    public void recordCall(Context parentContext, ServiceCallLog call) {
        if (call == null) return;
        Span span = tracer.spanBuilder(call.getDisplayName())
                .setParent(parentContext)
                .setAttribute("service.name", call.getServiceName() == null ? "" : call.getServiceName())
                .setAttribute("service.method", call.getMethodName() == null ? "" : call.getMethodName())
                .setAttribute("service.durationMs", call.getDurationMs())
                .setAttribute("service.success", call.isSuccess())
                .startSpan();
        if (!call.isSuccess() && call.getErrorMessage() != null) {
            span.setStatus(StatusCode.ERROR, call.getErrorMessage());
        }
        Context childCtx = parentContext.with(span);
        if (call.getChildren() != null) {
            for (ServiceCallLog child : call.getChildren()) {
                recordCall(childCtx, child);
            }
        }
        // 用记录的 startTime/endTime 还原 span 时间（OTel 用纳秒）
        span.end();
    }

    /** 把根步骤脚本打印记录为 root span 上的 event */
    public void recordStep(Context rootContext, StepLog step) {
        if (step == null) return;
        Span.fromContext(rootContext).addEvent(step.getStepName() == null ? "step" : step.getStepName(),
                Attributes.builder().put("step.detail", step.getDetail() == null ? "" : step.getDetail()).build());
    }

    /** 把整条执行记录导出到 OTel（便于批量补录/测试） */
    public void exportRecord(RuleExecutionRecord record, Context rootContext) {
        if (record == null) return;
        if (record.getCalls() != null) {
            for (ServiceCallLog call : record.getCalls()) {
                recordCall(rootContext, call);
            }
        }
        if (record.getRootSteps() != null) {
            for (StepLog step : record.getRootSteps()) {
                recordStep(rootContext, step);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        sdk.shutdown();
    }
}
