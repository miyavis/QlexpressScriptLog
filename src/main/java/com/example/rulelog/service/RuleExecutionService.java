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
import com.example.rulelog.trace.StepLogger;
import com.example.rulelog.store.ExecutionRecordStore;
import com.example.rulelog.store.ScriptStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    private final long timeoutMillis;

    public RuleExecutionService(Express4Engine engine,
                                FunctionAliasRegistry aliasRegistry,
                                ScriptStore scriptStore,
                                ExecutionRecordStore recordStore,
                                @Value("${app.script-timeout-millis:5000}") long timeoutMillis) {
        this.engine = engine;
        this.aliasRegistry = aliasRegistry;
        this.scriptStore = scriptStore;
        this.recordStore = recordStore;
        this.timeoutMillis = timeoutMillis;
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

        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                .toUpperCase();

        RuleExecutionRecord record = new RuleExecutionRecord();
        record.setTraceId(traceId);
        record.setScriptId(script.getId());
        record.setScriptName(script.getName());
        record.setScriptSnapshot(script.getContent());
        record.setParams(actualParams);
        record.setStartTime(System.currentTimeMillis());

        StepLogger.startTrace(traceId);
        RuleTraceContext.set(traceId);
        QLResult qlResult = null;
        try {
            qlResult = engine.execute(script.getContent(), actualParams, timeoutMillis);
            record.setResult(qlResult.getResult());
            record.setSuccess(true);
        } catch (QLException e) {
            // 编译期语法错误与运行期错误统一记录到执行结果里，前端直接展示
            record.setSuccess(false);
            record.setResult(null);
            record.setErrorType(describeErrorType(e));
            record.setErrorMessage(e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            StepLogger.Collector collector = StepLogger.drainAndRemove();
            record.setCalls(collector.getRootCalls());
            record.setRootSteps(collector.getRootSteps());
            // 将调用/步骤按脚本行号分组，用于前端“每一行脚本对应的日志”展示
            groupLogsByLine(record, qlResult, script.getContent());
            record.setEndTime(System.currentTimeMillis());
            record.setDurationMs(Math.max(0, record.getEndTime() - record.getStartTime()));
            RuleTraceContext.clear();
            recordStore.save(record);
        }
        return record;
    }

    /**
     * 将 Service 调用与根步骤按脚本行号分组到 {@link RuleExecutionRecord#lineExecutions}。
     * 为每个调用查找脚本中包含其中文函数名的行号，保证调用与代码行一一对应。
     */
    private void groupLogsByLine(RuleExecutionRecord record, QLResult qlResult, String scriptContent) {
        String[] lines = scriptContent == null ? new String[0] : scriptContent.split("\n", -1);

        // 收集所有已注册的中文函数名
        Set<String> chineseNames = aliasRegistry.getAllChineseNames();

        // 初始化所有包含中文函数名的行
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (String name : chineseNames) {
                if (line.contains(name)) {
                    String content = line.trim();
                    if (content.isEmpty()) {
                        content = line;
                    }
                    record.getLineExecutions().putIfAbsent(i + 1,
                            new LineExecution(i + 1, content));
                    break;
                }
            }
        }

        // 若没有发现任何活跃行，兜底使用所有非空行
        if (record.getLineExecutions().isEmpty()) {
            for (int i = 0; i < lines.length; i++) {
                String content = lines[i].trim();
                if (!content.isEmpty()) {
                    record.getLineExecutions().putIfAbsent(i + 1,
                            new LineExecution(i + 1, content));
                }
            }
        }

        // 将 ServiceCallLog 按 startTime 排序，保证时间顺序正确
        List<ServiceCallLog> calls = record.getCalls();
        if (calls != null) {
            calls.sort((a, b) -> Long.compare(a.getStartTime(), b.getStartTime()));
        }

        // 将每个调用分配到脚本中包含其中文名的行号
        if (calls != null) {
            for (ServiceCallLog call : calls) {
                Integer targetLine = findLineByChineseName(lines, call.getChineseName());
                if (targetLine == null) {
                    // 找不到匹配行，放到最后一行
                    targetLine = lines.length;
                }
                call.setLineNo(targetLine);
                LineExecution le = record.getLineExecutions().get(targetLine);
                if (le == null) {
                    String content = (targetLine >= 1 && targetLine <= lines.length)
                            ? lines[targetLine - 1] : "";
                    le = new LineExecution(targetLine, content);
                    record.getLineExecutions().put(targetLine, le);
                }
                le.addCall(call);
            }
        }

        // 递归处理子调用，分配到父调用所在行
        if (calls != null) {
            for (ServiceCallLog call : calls) {
                groupChildrenByLine(record, call, lines);
            }
        }

        // 根层级步骤（脚本打印）：按 stepName 匹配所在行
        // 记录日志("积分查询完成", ...) 的行包含 "积分查询完成"，据此归到正确行
        List<StepLog> rootSteps = record.getRootSteps();
        if (rootSteps != null && !rootSteps.isEmpty()) {
            for (StepLog step : rootSteps) {
                Integer targetLine = null;
                if (step.getStepName() != null && !step.getStepName().isEmpty()) {
                    for (int i = 0; i < lines.length; i++) {
                        if (lines[i].contains(step.getStepName())) {
                            targetLine = i + 1;
                            break;
                        }
                    }
                }
                // 找不到匹配行，兜底放第一行
                if (targetLine == null) {
                    targetLine = !record.getLineExecutions().isEmpty()
                            ? record.getLineExecutions().keySet().iterator().next() : 1;
                }
                LineExecution le = record.getLineExecutions().get(targetLine);
                if (le == null) {
                    String content = (targetLine >= 1 && targetLine <= lines.length)
                            ? lines[targetLine - 1] : "";
                    le = new LineExecution(targetLine, content);
                    record.getLineExecutions().put(targetLine, le);
                }
                le.addStep(step);
            }
        }

        // 将 QLExpress 表达式轨迹按行号归入对应的 LineExecution
        if (qlResult != null && qlResult.getExpressionTraces() != null) {
            for (ExpressionTrace trace : qlResult.getExpressionTraces()) {
                collectExpressionTraces(record, trace, lines);
            }
        }

        // 重算 hasActivity：只有存在调用、打印或被真正求值（evaluated=true）的表达式才算活跃，
        // 避免未走到的分支（evaluated=false 的预演节点）把行标成“已执行”
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

    /** 在脚本行中查找包含指定中文函数名的行号（从1开始），找不到返回 null */
    private Integer findLineByChineseName(String[] lines, String chineseName) {
        if (chineseName == null) {
            return null;
        }
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(chineseName)) {
                return i + 1;
            }
        }
        return null;
    }

    /** 获取第一个活跃行号 */
    private Integer firstActiveLine(Map<Integer, LineExecution> lineExecutions) {
        if (lineExecutions == null || lineExecutions.isEmpty()) {
            return null;
        }
        return lineExecutions.keySet().iterator().next();
    }

    /** 递归处理子调用，分配到父调用所在行 */
    private void groupChildrenByLine(RuleExecutionRecord record, ServiceCallLog parent,
                                     String[] lines) {
        List<ServiceCallLog> children = parent.getChildren();
        if (children == null) {
            return;
        }
        // 子调用按 startTime 排序
        children.sort((a, b) -> Long.compare(a.getStartTime(), b.getStartTime()));
        Integer parentLine = findParentLine(record, parent);
        for (ServiceCallLog child : children) {
            // 子调用优先分配到父调用所在行，否则按中文名查找行号
            Integer targetLine = parentLine != null ? parentLine : findLineByChineseName(lines, child.getChineseName());
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
            groupChildrenByLine(record, child, lines);
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
}
