package com.example.rulelog.trace;

import com.example.rulelog.model.ServiceCallLog;
import com.example.rulelog.model.StepLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 业务步骤打点器（ThreadLocal）。
 *
 * 职责分工：
 *   - {@link com.example.rulelog.aspect.ServiceCallAspect}：AOP 自动记录 Service 方法入口/出口
 *   - 本类 {@code log()}：Service 方法内部的业务步骤（手动埋点，AOP 拦不了自调用）
 *   - 本类注册到脚本引擎的 “记录日志(...)” 函数：脚本层面的打点
 *
 * 所有记录自动携带当前执行的 traceId，最后由 {@code drainAndRemove()}
 * 一次性交给 RuleExecutionService 聚合进 RuleExecutionRecord。
 */
public final class StepLogger {

    /** 一次执行的采集容器 */
    public static final class Collector {
        private final String traceId;
        private final List<ServiceCallLog> rootCalls = new ArrayList<>();
        private final List<StepLog> rootSteps = new ArrayList<>();
        private final Deque<ServiceCallLog> stack = new ArrayDeque<>();

        private Collector(String traceId) {
            this.traceId = traceId;
        }

        public List<ServiceCallLog> getRootCalls() {
            return rootCalls;
        }

        public List<StepLog> getRootSteps() {
            return rootSteps;
        }
    }

    private static final ThreadLocal<Collector> COLLECTOR = new ThreadLocal<>();

    private StepLogger() {
    }

    /** 脚本执行开始时调用 */
    public static void startTrace(String traceId) {
        COLLECTOR.set(new Collector(traceId));
    }

    /** Service 方法进入（由 AOP 调用） */
    public static void beginCall(String serviceName, String methodName, String argsJson, String chineseName) {
        Collector c = COLLECTOR.get();
        if (c == null) {
            return;
        }
        ServiceCallLog call =
                new ServiceCallLog(c.traceId, serviceName, chineseName, methodName, argsJson, System.currentTimeMillis());
        if (c.stack.isEmpty()) {
            c.rootCalls.add(call);
        } else {
            // Service 内部再调用其他 Service：挂到外层调用下的 children
            c.stack.peek().addChild(call);
        }
        c.stack.push(call);
    }

    /** Service 方法正常返回（由 AOP 调用） */
    public static void endCall(Object result) {
        Collector c = COLLECTOR.get();
        if (c == null) {
            return;
        }
        ServiceCallLog call = c.stack.poll();
        if (call != null) {
            call.finish(result);
        }
    }

    /** Service 方法抛异常（由 AOP 调用） */
    public static void endCallError(Throwable t) {
        Collector c = COLLECTOR.get();
        if (c == null) {
            return;
        }
        ServiceCallLog call = c.stack.poll();
        if (call != null) {
            call.finishError(t);
        }
    }

    /**
     * 记录一步业务动作，自动归入当前最内层的 Service 调用；
     * 若当前不在任何 Service 内（如脚本根层级），归入根步骤。
     */
    public static void log(String stepName, String detail) {
        Collector c = COLLECTOR.get();
        if (c == null) {
            return;
        }
        ServiceCallLog top = c.stack.peek();
        StepLog step = new StepLog(
                c.traceId,
                top == null ? null : top.getServiceName(),
                stepName,
                detail,
                System.currentTimeMillis());
        if (top == null) {
            c.rootSteps.add(step);
        } else {
            top.getInternalSteps().add(step);
        }
    }

    /** 是否处于一次被追踪的执行中 */
    public static boolean isActive() {
        return COLLECTOR.get() != null;
    }

    /** 收集全部日志并清理 ThreadLocal（执行 finally 中调用） */
    public static Collector drainAndRemove() {
        Collector c = COLLECTOR.get();
        COLLECTOR.remove();
        return c;
    }
}
