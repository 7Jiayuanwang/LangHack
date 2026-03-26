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

    // ==================== 切面2: 按方法名自动路由（优先级低于 @Transactional） ====================

    /**
     * 拦截 Service 层所有公共方法，按方法名前缀自动判断读/写并路由到对应数据源。
     * <p>
     * 优先级低于 {@link #routeDataSource}：如果方法已有 @Transactional 注解，
     * 或者 DynamicDataSourceContext 已被设置（说明被 routeDataSource 处理过），则跳过不覆盖。
     */
    @Around("execution(public * com.english.service..*.*(..))")
    public Object autoRouteByMethodName(ProceedingJoinPoint joinPoint) throws Throwable {
        // 如果已被 @Transactional 切面设置了数据源，则不覆盖
        if (DynamicDataSourceContext.getDataSourceType() != null) {
            return joinPoint.proceed();
        }

        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();

        // 如果方法有 @Transactional，留给 routeDataSource 处理
        if (method.isAnnotationPresent(Transactional.class)) {
            return joinPoint.proceed();
        }
        // 也检查类级别 @Transactional
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        if (targetClass.isAnnotationPresent(Transactional.class)) {
            return joinPoint.proceed();
        }

        String methodName = method.getName();
        boolean isRead = isReadMethod(methodName);
        DataSourceType type = isRead ? DataSourceType.SLAVE : DataSourceType.MASTER;
        DynamicDataSourceContext.setDataSourceType(type);
        log.debug("Auto-routing to {} for method {}.{}", type, targetClass.getSimpleName(), methodName);
        try {
            return joinPoint.proceed();
        } finally {
            DynamicDataSourceContext.clear();
        }
    }

    // ==================== 内部工具方法 ====================

    /** 读方法名前缀列表 */
    private static final String[] READ_PREFIXES = {
        "get", "find", "list", "search", "count", "query", "select",
        "check", "exists", "is", "has", "load", "fetch", "retrieve"
    };

    /**
     * 判断方法名是否属于读操作（以指定前缀开头）
     */
    private boolean isReadMethod(String methodName) {
        for (String prefix : READ_PREFIXES) {
            if (methodName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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
