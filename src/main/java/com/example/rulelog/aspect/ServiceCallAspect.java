package com.example.rulelog.aspect;

import com.example.rulelog.express.FunctionAliasRegistry;
import com.example.rulelog.trace.StepLogger;
import com.example.rulelog.util.JsonUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AOP 观测面：自动拦截业务 Service（com.example.rulelog.biz 包）的每一次方法调用，
 * 记录入口、出口（成功/异常）、入参、出参、耗时，交给 StepLogger 归档到当前 traceId。
 *
 * 已知边界（与手动 StepLogger.log() 互补）：
 *   Spring AOP 拦截不了同一个 Bean 内部的自调用，Service 内部
 *   的关键业务步骤应由开发者显式调用 StepLogger.log(步骤名, 详情)。
 *
 * 显示名称：优先使用 FunctionAliasRegistry 中注册的脚本中文函数名（如"查询用户等级"），
 * 与业务人员在脚本中看到的完全一致，不再显示英文的 Service/方法名。
 */
@Aspect
@Component
public class ServiceCallAspect {

    private static final int MAX_ARG_JSON = 300;

    @Autowired
    private FunctionAliasRegistry aliasRegistry;

    @Around("execution(* com.example.rulelog.biz..*.*(..))")
    public Object observe(ProceedingJoinPoint pjp) throws Throwable {
        String serviceName = pjp.getTarget().getClass().getSimpleName();
        String methodName = ((MethodSignature) pjp.getSignature()).getMethod().getName();
        String argsJson = JsonUtils.toJsonTruncated(pjp.getArgs(), MAX_ARG_JSON);

        // 查找脚本中使用的中文函数名（如"查询用户等级"），找不到则为 null
        String chineseName = aliasRegistry != null ? aliasRegistry.getChineseName(serviceName, methodName) : null;

        // 没有处于被追踪的执行中（如应用启动调用）时，直接放行，不产生日志
        boolean traced = StepLogger.isActive();
        if (traced) {
            StepLogger.beginCall(serviceName, methodName, argsJson, chineseName);
        }
        try {
            Object result = pjp.proceed();
            if (traced) {
                StepLogger.endCall(result);
            }
            return result;
        } catch (Throwable t) {
            if (traced) {
                StepLogger.endCallError(t);
            }
            throw t;
        }
    }
}
