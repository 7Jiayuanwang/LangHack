package com.english.datasource;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@Order(1) // 必须在事务切面（Order=2）之前执行，确保切换数据源后事务才绑定连接
public class DataSourceAspect {

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional)")
    public Object routeDataSource(ProceedingJoinPoint joinPoint) throws Throwable {
        boolean readOnly = isReadOnly(joinPoint);
        DataSourceType type = readOnly ? DataSourceType.SLAVE : DataSourceType.MASTER;
        DataSourceType previous = DynamicDataSourceContext.getDataSourceType();
        DynamicDataSourceContext.setDataSourceType(type);
        log.debug("Routing to {} datasource for {}", type, joinPoint.getSignature().getName());
        try {
            return joinPoint.proceed();
        } finally {
            // CRITICAL: ThreadLocal必须在finally块中恢复或清理，防止内存泄漏
            // 即使proceed()抛出异常也必须执行
            if (previous == null) {
                DynamicDataSourceContext.clear();
            } else {
                DynamicDataSourceContext.setDataSourceType(previous);
            }
        }
    }

    private boolean isReadOnly(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            Method method = sig.getMethod();
            // 优先检查方法级别注解
            Transactional tx = method.getAnnotation(Transactional.class);
            if (tx != null) {
                return tx.readOnly();
            }
            // 再检查类级别注解（使用 AopUtils 获取真实目标类，避免代理类干扰）
            Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
            tx = targetClass.getAnnotation(Transactional.class);
            return tx != null && tx.readOnly();
        } catch (Exception e) {
            log.warn("Failed to determine readOnly status, defaulting to false", e);
            return false;
        }
    }
}
