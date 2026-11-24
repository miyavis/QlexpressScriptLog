package com.example.rulelog.service;

import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.exception.QLException;
import com.alibaba.qlexpress4.exception.QLSyntaxException;
import com.alibaba.qlexpress4.exception.QLTimeoutException;
import com.alibaba.qlexpress4.runtime.trace.ExpressionTrace;
import com.example.rulelog.express.Express4Engine;
import com.example.rulelog.express.FunctionAliasRegistry;
import com.example.rulelog.model.LineExecution;
import com.example.rulelog.model.RuleExecutionRecord;
import com.example.rulelog.model.Script;
import com.example.rulelog.model.ServiceCallLog;
import com.example.rulelog.model.StepLog;
import com.example.rulelog.trace.RuleTraceContext;
import com.example.rulelog.trace.OtelTraceService;
import com.example.rulelog.trace.StepLogger;
import com.example.rulelog.store.ExecutionRecordStore;
import com.example.rulelog.store.ScriptStore;
import com.example.rulelog.util.ErrorSummarizer;
import com.example.rulelog.util.LineMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 脚本执行入口。
 *
 * 一次执行的完整生命周期：
 *   1. 生成 traceId（这一次执行的唯一身份）
 *   2. traceId 写入 RuleTraceContext（ThreadLocal）
 *   3. StepLogger.startTrace 开始采集
 *   4. 引擎执行脚本（脚本里调用的 Spring Service 被 AOP 与手动埋点覆盖）
 *   5. finally: StepLogger.drain 收集全部日志 -> 聚合成 RuleExecutionRecord
 *   6. finally: RuleTraceContext.clear()
 *   7. 存入执行记录库（含 traceId、参数快照、结果、全部调用链）
 */
@Service
public class RuleExecutionService {

    private final Express4Engine engine;

    private final FunctionAliasRegistry aliasRegistry;

    private final ScriptStore scriptStore;

    private final ExecutionRecordStore recordStore;

    private final OtelTraceService otel;

    private final long timeoutMillis;

    public RuleExecutionService(Express4Engine engine,
                                FunctionAliasRegistry aliasRegistry,
                                ScriptStore scriptStore,
                                ExecutionRecordStore recordStore,
                                @Value("${app.script-timeout-millis:5000}") long timeoutMillis) {
        this(engine, aliasRegistry, scriptStore, recordStore, timeoutMillis, new OtelTraceService(""));
    }

    @Autowired
    public RuleExecutionService(Express4Engine engine,
                                FunctionAliasRegistry aliasRegistry,
                                ScriptStore scriptStore,
                                ExecutionRecordStore recordStore,
                                @Value("${app.script-timeout-millis:5000}") long timeoutMillis,
                                OtelTraceService otel) {
        this.engine = engine;
        this.aliasRegistry = aliasRegistry;
        this.scriptStore = scriptStore;
        this.recordStore = recordStore;
        this.timeoutMillis = timeoutMillis;
        this.otel = otel == null ? new OtelTraceService("") : otel;
    }

    public RuleExecutionRecord run(String scriptId, Map<String, Object> overrideParams) throws Exception {
        Script script = scriptStore.get(scriptId);
        if (script == null) {
            throw new IllegalArgumentException("脚本不存在: " + scriptId);
        }

        // 参数解析顺序：请求覆盖参数 > 脚本默认参数
        Map<String, Object> actualParams = new LinkedHashMap<>();
        if (script.getDefaultParams() != null) {
            actualParams.putAll(script.getDefaultParams());
        }
        if (overrideParams != null) {
            actualParams.putAll(overrideParams);
        }
        return executeInternal(script.getId(), script.getName(), script.getContent(), actualParams);
    }

    /** 执行任意脚本文本（比对画布里"函数调用"取数节点用），同样生成可下钻的执行记录 */
    public RuleExecutionRecord runByContent(String virtualScriptId, String content, Map<String, Object> params) throws Exception {
        return executeInternal(virtualScriptId, virtualScriptId, content,
                params == null ? new LinkedHashMap<>() : params);
    }

    private RuleExecutionRecord executeInternal(String scriptId, String scriptName,
                                                 String content, Map<String, Object> actualParams) throws Exception {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                .toUpperCase();

        RuleExecutionRecord record = new RuleExecutionRecord();
        record.setTraceId(traceId);
        record.setTraceparent(com.example.rulelog.trace.TraceParent.fromPlatformTraceId(traceId));
        record.setScriptId(scriptId);
        record.setScriptName(scriptName);
        record.setScriptSnapshot(content);
        record.setParams(actualParams);
        record.setStartTime(System.currentTimeMillis());

        QLResult qlResult = null;
        io.opentelemetry.context.Context otelRoot = otel.startExecution(traceId, scriptId);
        try {
            // startTrace/set 必须放进 try：万一它们自身异常，finally 仍能清理 ThreadLocal，避免上下文泄漏到后续请求
            StepLogger.startTrace(traceId);
            RuleTraceContext.set(traceId);
            qlResult = engine.execute(content, actualParams, timeoutMillis);
            record.setResult(qlResult.getResult());
            record.setVariables(collectVariables(qlResult));
            record.setSuccess(true);
        } catch (QLException e) {
            // 编译期语法错误与运行期错误统一记录到执行结果里，前端直接展示
            record.setSuccess(false);
            record.setResult(null);
            record.setErrorType(describeErrorType(e));
            // errorMessage 存中文摘要（第几行/什么错/出错行内容），原始英文信息保留在摘要尾部供开发排查
            record.setErrorMessage(ErrorSummarizer.summarize(e, splitLines(content)).detail);
        } finally {
            // ① 采集并聚合日志（StepLogger 采集器必须先于 ThreadLocal 清理）
            StepLogger.Collector collector = StepLogger.drainAndRemove();
            record.setCalls(collector.getRootCalls());
            record.setRootSteps(collector.getRootSteps());
            groupLogsByLine(record, qlResult, content);
            record.setEndTime(System.currentTimeMillis());
            record.setDurationMs(Math.max(0, record.getEndTime() - record.getStartTime()));
            // ② OTel 导出是"尽力而为"：collector 不可达等异常绝不能吞掉执行记录或泄漏 ThreadLocal
            try {
                otel.exportRecord(record, otelRoot);
                otel.endExecution(otelRoot, record.isSuccess(), record.getErrorMessage());
            } catch (Exception otelEx) {
                org.slf4j.LoggerFactory.getLogger(RuleExecutionService.class)
                        .warn("OTel 导出失败（不影响执行记录）: {}", otelEx.getMessage());
                otel.safeClear(); // 确保 IdGenerator 的 ThreadLocal 也被清理
            } finally {
                RuleTraceContext.clear();
                recordStore.save(record); // 无论 OTel 成败，执行记录都要落盘
            }
        }
        return record;
    }

    /** 从表达式轨迹里提取脚本产生的变量（供下游比对卡作为输入） */
    private Map<String, Object> collectVariables(QLResult result) {
        Map<String, Object> vars = new LinkedHashMap<>();
        if (result == null || result.getExpressionTraces() == null) return vars;
        for (ExpressionTrace t : result.getExpressionTraces()) collectVariableTrace(t, vars);
        return vars;
    }

    private void collectVariableTrace(ExpressionTrace t, Map<String, Object> vars) {
        if (t == null) return;
        boolean assignmentOperator = t.getType() == com.alibaba.qlexpress4.runtime.trace.TraceType.OPERATOR
                && "=".equals(t.getToken()) && t.getValue() != null;
        if (com.alibaba.qlexpress4.runtime.trace.TraceType.VARIABLE == t.getType()
                && t.isEvaluated() && t.getToken() != null) {
            if (t.getValue() != null) {
                vars.put(t.getToken(), t.getValue());
            } else {
                vars.putIfAbsent(t.getToken(), null);
            }
        }
        if (t.getChildren() != null) {
            for (ExpressionTrace c : t.getChildren()) {
                // 赋值语句 "变量 = 表达式"：赋值目标 VARIABLE 的 value 为 null，
                // 实际值在父级 OPERATOR("=") 上，这里回填
                if (assignmentOperator && c.getType() == com.alibaba.qlexpress4.runtime.trace.TraceType.VARIABLE
                        && c.getToken() != null && c.getValue() == null) {
                    vars.put(c.getToken(), t.getValue());
                }
                collectVariableTrace(c, vars);
            }
        }
    }

    /** 把一次比对结果作为步骤追加到脚本执行记录（同一 traceId，执行记录里能一并看到） */
    public void appendCompareStep(RuleExecutionRecord record, String cardName,
                                  com.example.rulelog.compare.CompareResult result, Integer lineNo) {
        if (record == null || result == null) return;
        String detail = "比对 " + result.getTotalRows() + " 行，一致 " + result.getConsistentRows()
                + "，不一致 " + result.getInconsistentRows() + "（不一致字段 " + result.getInconsistentCells() + "）";
        StepLog step = new StepLog(record.getTraceId(), "比对引擎",
                "比对: " + (cardName == null ? "比对卡" : cardName), detail, System.currentTimeMillis());
        record.getRootSteps().add(step);
        // 挂到比对卡对应的脚本行，右侧按行日志才能显示（并高亮对应卡片）
        if (lineNo != null) {
            com.example.rulelog.model.LineExecution line = record.getLineExecutions().get(Integer.valueOf(lineNo));
            if (line != null) {
                line.getSteps().add(step);
                line.setHasActivity(true);
            }
        }
        recordStore.save(record);
    }

    /**
     * 将 Service 调用与根步骤按脚本行号分组到 {@link RuleExecutionRecord#lineExecutions}。
     * 为每个调用查找脚本中包含其中文函数名的行号，保证调用与代码行一一对应。
     */
    private void groupLogsByLine(RuleExecutionRecord record, QLResult qlResult, String scriptContent) {
        String[] lines = scriptContent == null ? new String[0] : scriptContent.split("\n", -1);
        initLineExecutions(record, lines);
        LineMatcher matcher = new LineMatcher(lines); // 同名函数按出现顺序依次消费行号
        groupCalls(record, lines, matcher);
        groupRootSteps(record, lines);
        collectTraces(record, qlResult, lines);
        recomputeActivity(record);
    }

    /** 初始化行级记录：含中文函数名的行，兜底为所有非空行 */
    private void initLineExecutions(RuleExecutionRecord record, String[] lines) {
        Set<String> chineseNames = aliasRegistry.getAllChineseNames();
        for (int i = 0; i < lines.length; i++) {
            for (String name : chineseNames) {
                if (lines[i].contains(name)) {
                    putLine(record, i + 1, lines);
                    break;
                }
            }
        }
        if (record.getLineExecutions().isEmpty()) {
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].trim().isEmpty()) {
                    putLine(record, i + 1, lines);
                }
            }
        }
    }

    /** 确保行号有对应 LineExecution（存在则跳过） */
    private void putLine(RuleExecutionRecord record, int lineNo, String[] lines) {
        if (record.getLineExecutions().containsKey(lineNo)) return;
        String content = (lineNo >= 1 && lineNo <= lines.length) ? lines[lineNo - 1].trim() : "";
        record.getLineExecutions().put(lineNo,
                new LineExecution(lineNo, content.isEmpty() && lineNo >= 1 && lineNo <= lines.length
                        ? lines[lineNo - 1] : content));
    }

    /** 把服务调用（含子调用）按中文函数名归到各自行 */
    private void groupCalls(RuleExecutionRecord record, String[] lines, LineMatcher matcher) {
        List<ServiceCallLog> calls = record.getCalls();
        if (calls == null) return;
        calls.sort((a, b) -> Long.compare(a.getStartTime(), b.getStartTime()));
        for (ServiceCallLog call : calls) {
            Integer targetLine = matcher.next(call.getChineseName());
            if (targetLine == null) targetLine = lines.length;
            call.setLineNo(targetLine);
            putLine(record, targetLine, lines);
            record.getLineExecutions().get(targetLine).addCall(call);
        }
        // 顶层全部归行后，再递归处理子调用（保持与原实现一致的顺序）
        for (ServiceCallLog call : calls) {
            groupChildrenByLine(record, call, lines, matcher);
        }
    }

    /** 把脚本打印（根步骤）按步骤名归到所在行 */
    private void groupRootSteps(RuleExecutionRecord record, String[] lines) {
        List<StepLog> rootSteps = record.getRootSteps();
        if (rootSteps == null) return;
        for (StepLog step : rootSteps) {
            Integer targetLine = findLineByStepName(lines, step.getStepName());
            if (targetLine == null) {
                targetLine = record.getLineExecutions().isEmpty()
                        ? 1 : record.getLineExecutions().keySet().iterator().next();
            }
            putLine(record, targetLine, lines);
            record.getLineExecutions().get(targetLine).addStep(step);
        }
    }

    /** 按步骤名找脚本行（首次匹配），找不到返回 null */
    private Integer findLineByStepName(String[] lines, String stepName) {
        if (stepName == null || stepName.isEmpty()) return null;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(stepName)) return i + 1;
        }
        return null;
    }

    /** 把 QLExpress 表达式轨迹递归归入对应行 */
    private void collectTraces(RuleExecutionRecord record, QLResult qlResult, String[] lines) {
        if (qlResult == null || qlResult.getExpressionTraces() == null) return;
        for (ExpressionTrace trace : qlResult.getExpressionTraces()) {
            collectExpressionTraces(record, trace, lines);
        }
    }

    /** 重算活跃标记：有调用/打印/被求值表达式才算已执行（排除未走分支的预演节点） */
    private void recomputeActivity(RuleExecutionRecord record) {
        for (LineExecution le : record.getLineExecutions().values()) {
            boolean active = !le.getCalls().isEmpty() || !le.getSteps().isEmpty();
            for (LineExecution.LineExpression e : le.getExpressions()) {
                if (e.isEvaluated()) {
                    active = true;
                    break;
                }
            }
            le.setHasActivity(active);
        }
    }

    /** 递归收集 QLExpress 表达式轨迹，按行号归入对应的 LineExecution */
    private void collectExpressionTraces(RuleExecutionRecord record, ExpressionTrace trace, String[] lines) {
        if (trace == null) {
            return;
        }
        int line = trace.getLine();
        // 如果行号无效（<=0 或超出范围），分配到最后一行，避免表达式丢失
        if (line <= 0 || line > lines.length) {
            line = lines.length;
        }
        LineExecution le = record.getLineExecutions().get(line);
        if (le == null) {
            String content = (line >= 1 && line <= lines.length) ? lines[line - 1] : "";
            le = new LineExecution(line, content);
            record.getLineExecutions().put(line, le);
        }
        le.addExpression(new LineExecution.LineExpression(
                trace.getType().name(),
                trace.getToken(),
                trace.getValue(),
                trace.isEvaluated()));
        List<ExpressionTrace> children = trace.getChildren();
        if (children != null) {
            for (ExpressionTrace child : children) {
                collectExpressionTraces(record, child, lines);
            }
        }
    }

    /** 递归处理子调用，分配到父调用所在行 */
    private void groupChildrenByLine(RuleExecutionRecord record, ServiceCallLog parent,
                                     String[] lines, com.example.rulelog.util.LineMatcher matcher) {
        List<ServiceCallLog> children = parent.getChildren();
        if (children == null) {
            return;
        }
        // 子调用按 startTime 排序
        children.sort((a, b) -> Long.compare(a.getStartTime(), b.getStartTime()));
        Integer parentLine = findParentLine(record, parent);
        for (ServiceCallLog child : children) {
            // 子调用优先分配到父调用所在行，否则按中文名查找下一个匹配行
            Integer targetLine = parentLine != null ? parentLine : matcher.next(child.getChineseName());
            if (targetLine == null) {
                targetLine = lines.length;
            }
            child.setLineNo(targetLine);
            LineExecution le = record.getLineExecutions().get(targetLine);
            if (le == null) {
                String content = (targetLine >= 1 && targetLine <= lines.length)
                        ? lines[targetLine - 1] : "";
                le = new LineExecution(targetLine, content);
                record.getLineExecutions().put(targetLine, le);
            }
            le.addCall(child);
            groupChildrenByLine(record, child, lines, matcher);
        }
    }

    /** 查找父调用所在的行号 */
    private Integer findParentLine(RuleExecutionRecord record, ServiceCallLog parent) {
        for (Map.Entry<Integer, LineExecution> entry : record.getLineExecutions().entrySet()) {
            if (entry.getValue().getCalls().contains(parent)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String describeErrorType(QLException e) {
        if (e instanceof QLSyntaxException) {
            return "语法错误";
        }
        if (e instanceof QLTimeoutException) {
            return "超时";
        }
        return "规则运行错误";
    }

    /** 脚本文本按行拆分（ErrorSummarizer 用它带出出错行的原文） */
    private List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        return Arrays.asList(content.split("\n", -1));
    }
}
