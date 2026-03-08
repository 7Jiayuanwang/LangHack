package com.english.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证 DataSourceAspect 根据 @Transactional(readOnly) 属性正确路由数据源。
 */
public class DataSourceAspectTest {

    @InjectMocks
    private com.english.datasource.DataSourceAspect aspect;

    // 内部辅助接口，提供带不同注解的方法用于获取 Method 对象
    interface SampleService {
        @Transactional(readOnly = true)
        void readOnlyMethod();

        @Transactional
        void writeMethod();

        void nonTransactionalMethod();
    }

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() {
        // 确保每个测试后清理 ThreadLocal，避免污染其他测试
        DynamicDataSourceContext.clear();
    }

    @Test
    public void readOnlyTransactional_shouldRouteTo_SLAVE() throws Throwable {
        Method method = SampleService.class.getMethod("readOnlyMethod");

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(method);

        AtomicReference<DataSourceType> capturedType = new AtomicReference<>();
        when(pjp.proceed()).then(inv -> {
            capturedType.set(DynamicDataSourceContext.getDataSourceType());
            return null;
        });

        aspect.routeDataSource(pjp);

        assertEquals(DataSourceType.SLAVE, capturedType.get(),
                "readOnly=true 的事务方法应路由到从库 SLAVE");
    }

    @Test
    public void writeTransactional_shouldRouteTo_MASTER() throws Throwable {
        Method method = SampleService.class.getMethod("writeMethod");

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(method);

        AtomicReference<DataSourceType> capturedType = new AtomicReference<>();
        when(pjp.proceed()).then(inv -> {
            capturedType.set(DynamicDataSourceContext.getDataSourceType());
            return null;
        });

        aspect.routeDataSource(pjp);

        assertEquals(DataSourceType.MASTER, capturedType.get(),
                "readOnly=false 的事务方法应路由到主库 MASTER");
    }

    @Test
    public void contextShouldBeClearedAfterProceed() throws Throwable {
        Method method = SampleService.class.getMethod("readOnlyMethod");

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenReturn(null);

        aspect.routeDataSource(pjp);

        assertNull(DynamicDataSourceContext.getDataSourceType(),
                "切面执行完毕后应清除 ThreadLocal，防止连接池泄漏");
    }

    @Test
    public void contextShouldBeClearedEvenOnException() throws Throwable {
        Method method = SampleService.class.getMethod("writeMethod");

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> aspect.routeDataSource(pjp));

        assertNull(DynamicDataSourceContext.getDataSourceType(),
                "即使 proceed() 抛出异常，finally 块也应清除 ThreadLocal");
    }

    @Test
    public void classLevelReadOnlyAnnotation_shouldRouteTo_SLAVE() throws Throwable {
        // 模拟方法本身无注解，但类级别有 @Transactional(readOnly=true)
        Method method = SampleService.class.getMethod("nonTransactionalMethod");

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(method);

        // 模拟 target 对象是带 class-level @Transactional(readOnly=true) 的类
        @Transactional(readOnly = true)
        class ReadOnlyService {}

        when(pjp.getTarget()).thenReturn(new ReadOnlyService());

        AtomicReference<DataSourceType> capturedType = new AtomicReference<>();
        when(pjp.proceed()).then(inv -> {
            capturedType.set(DynamicDataSourceContext.getDataSourceType());
            return null;
        });

        aspect.routeDataSource(pjp);

        assertEquals(DataSourceType.SLAVE, capturedType.get(),
                "类级别 readOnly=true 注解应路由到从库 SLAVE");
    }
}
