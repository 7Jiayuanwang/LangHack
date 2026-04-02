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
@Order(1)
public class DataSourceAspect {

    private static final String[] READ_PREFIXES = {
        "get", "find", "list", "search", "count", "query", "select",
        "check", "exists", "is", "has", "load", "fetch", "retrieve"
    };

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional) || " +
            "@annotation(com.english.datasource.UseMaster) || " +
            "@within(com.english.datasource.UseMaster)")
    public Object routeDataSource(ProceedingJoinPoint joinPoint) throws Throwable {
        DataSourceType previous = DynamicDataSourceContext.getDataSourceType();
        DataSourceType type = resolveTransactionalType(joinPoint);
        DynamicDataSourceContext.setDataSourceType(type);
        log.debug("Routing to {} datasource for {}", type, joinPoint.getSignature().getName());
        try {
            return joinPoint.proceed();
        } finally {
            restorePreviousDataSource(previous);
        }
    }

    @Around("execution(public * com.english.service..*.*(..))")
    public Object autoRouteByMethodName(ProceedingJoinPoint joinPoint) throws Throwable {
        if (DynamicDataSourceContext.getDataSourceType() != null) {
            return joinPoint.proceed();
        }

        // Admin/OPS interceptor forces master for all operations
        if (DynamicDataSourceContext.isForceMaster()) {
            DynamicDataSourceContext.setDataSourceType(DataSourceType.MASTER);
            log.debug("Force-master routing for method {}", joinPoint.getSignature().getName());
            try {
                return joinPoint.proceed();
            } finally {
                DynamicDataSourceContext.clear();
            }
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = resolveTargetClass(joinPoint, method);

        if (hasTransactionalAnnotation(method, targetClass)) {
            return joinPoint.proceed();
        }

        DataSourceType type = isForceMaster(method, targetClass)
                ? DataSourceType.MASTER
                : (isReadMethod(method.getName()) ? DataSourceType.SLAVE : DataSourceType.MASTER);

        DynamicDataSourceContext.setDataSourceType(type);
        log.debug("Auto-routing to {} for method {}.{}", type, targetClass.getSimpleName(), method.getName());
        try {
            return joinPoint.proceed();
        } finally {
            DynamicDataSourceContext.clear();
        }
    }

    private DataSourceType resolveTransactionalType(ProceedingJoinPoint joinPoint) {
        // Admin/OPS interceptor forces master for all operations
        if (DynamicDataSourceContext.isForceMaster()) {
            return DataSourceType.MASTER;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Class<?> targetClass = resolveTargetClass(joinPoint, method);

            if (isForceMaster(method, targetClass)) {
                return DataSourceType.MASTER;
            }

            Transactional methodTx = method.getAnnotation(Transactional.class);
            if (methodTx != null) {
                return methodTx.readOnly() ? DataSourceType.SLAVE : DataSourceType.MASTER;
            }

            Transactional classTx = targetClass.getAnnotation(Transactional.class);
            if (classTx != null) {
                return classTx.readOnly() ? DataSourceType.SLAVE : DataSourceType.MASTER;
            }
        } catch (Exception e) {
            log.warn("Failed to determine transactional datasource, defaulting to MASTER", e);
        }
        return DataSourceType.MASTER;
    }

    private boolean hasTransactionalAnnotation(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(Transactional.class)
                || (targetClass != null && targetClass.isAnnotationPresent(Transactional.class))
                || method.isAnnotationPresent(UseMaster.class)
                || (targetClass != null && targetClass.isAnnotationPresent(UseMaster.class));
    }

    private boolean isReadMethod(String methodName) {
        for (String prefix : READ_PREFIXES) {
            if (methodName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isForceMaster(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(UseMaster.class)
                || (targetClass != null && targetClass.isAnnotationPresent(UseMaster.class));
    }

    private void restorePreviousDataSource(DataSourceType previous) {
        if (previous == null) {
            DynamicDataSourceContext.clear();
        } else {
            DynamicDataSourceContext.setDataSourceType(previous);
        }
    }

    private Class<?> resolveTargetClass(ProceedingJoinPoint joinPoint, Method method) {
        Object target = joinPoint.getTarget();
        if (target != null) {
            return AopUtils.getTargetClass(target);
        }
        return method != null ? method.getDeclaringClass() : null;
    }
}
