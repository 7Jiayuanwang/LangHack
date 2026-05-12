package com.english.service;

import com.english.service.impl.AccessMetricsService;
import com.english.service.impl.SystemMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccessMetricsService.
 *
 * Implementation facts:
 * - Uses AtomicLong for concurrentRequests and peakConcurrentToday (in-memory)
 * - recordRequestStart() increments concurrent count, updates peak, writes to Redis
 * - recordRequestEnd() decrements concurrent count (no Redis)
 * - getRealTimeMetrics() reads from Redis (todayRequests, todayUniqueUsers, activeUsers)
 *   plus in-memory concurrentRequests/peakConcurrentToday
 * - All Redis operations wrapped in try/catch — returns defaults on failure
 * - registerEmitter() creates SseEmitter, sends initial data, adds to emitters list
 * - Dependencies: StringRedisTemplate, SystemMetricsService
 */
@ExtendWith(MockitoExtension.class)
class AccessMetricsServiceTest {

    @InjectMocks
    private AccessMetricsService accessMetricsService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SystemMetricsService systemMetricsService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private void stubRedisOperations() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    // ==================== recordRequestStart tests ====================

    @Nested
    @DisplayName("recordRequestStart() 方法测试")
    class RecordRequestStartTests {

        @BeforeEach
        void setUp() {
            stubRedisOperations();
        }

        @Test
        @DisplayName("增加并发请求计数")
        void recordRequestStart_incrementsConcurrentCount() {
            // Arrange
            AtomicLong concurrentRequests = (AtomicLong) ReflectionTestUtils.getField(
                    accessMetricsService, "concurrentRequests");
            long before = concurrentRequests.get();

            // Act
            accessMetricsService.recordRequestStart(null);

            // Assert
            assertEquals(before + 1, concurrentRequests.get());
        }

        @Test
        @DisplayName("多次调用递增并发计数")
        void recordRequestStart_multipleCalls_incrementsEachTime() {
            // Act
            accessMetricsService.recordRequestStart(null);
            accessMetricsService.recordRequestStart(null);
            accessMetricsService.recordRequestStart(null);

            // Assert
            AtomicLong concurrentRequests = (AtomicLong) ReflectionTestUtils.getField(
                    accessMetricsService, "concurrentRequests");
            assertEquals(3, concurrentRequests.get());
        }

        @Test
        @DisplayName("新并发数超过当前峰值时更新峰值")
        void recordRequestStart_updatesPeakWhenNewConcurrentExceedsCurrent() {
            // Arrange
            AtomicLong peakConcurrentToday = (AtomicLong) ReflectionTestUtils.getField(
                    accessMetricsService, "peakConcurrentToday");
            assertEquals(0, peakConcurrentToday.get());

            // Act
            accessMetricsService.recordRequestStart(null);
            accessMetricsService.recordRequestStart(null);

            // Assert
            assertEquals(2, peakConcurrentToday.get());
        }

        @Test
        @DisplayName("带用户名时记录到 Redis 用户集合")
        void recordRequestStart_withUsername_recordsToRedis() {
            // Arrange
            when(valueOperations.increment(anyString())).thenReturn(1L);

            // Act
            accessMetricsService.recordRequestStart("testuser");

            // Assert
            verify(valueOperations).increment(contains("access:today:count:"));
            verify(redisTemplate).expire(contains("access:today:count:"), anyLong(), any());
            verify(setOperations).add(contains("access:today:users:"), eq("testuser"));
            verify(zSetOperations).add(eq("access:active:users"), eq("testuser"), anyDouble());
        }

        @Test
        @DisplayName("username 为 null 时不记录用户集合")
        void recordRequestStart_nullUsername_skipsUserTracking() {
            // Arrange
            when(valueOperations.increment(anyString())).thenReturn(1L);

            // Act
            accessMetricsService.recordRequestStart(null);

            // Assert
            verify(valueOperations).increment(contains("access:today:count:"));
            verify(setOperations, never()).add(anyString(), anyString());
            verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("Redis 不可用时优雅处理不抛出异常")
        void recordRequestStart_redisUnavailable_handlesGracefully() {
            // Arrange
            when(redisTemplate.opsForValue()).thenThrow(
                    new RedisConnectionFailureException("Connection refused"));

            // Act & Assert — should not throw
            assertDoesNotThrow(() -> accessMetricsService.recordRequestStart("testuser"));

            // Concurrent count should still be updated (it's in-memory, before Redis calls)
            AtomicLong concurrentRequests = (AtomicLong) ReflectionTestUtils.getField(
                    accessMetricsService, "concurrentRequests");
            assertEquals(1, concurrentRequests.get());
        }
    }

    // ==================== recordRequestEnd tests ====================

    @Nested
    @DisplayName("recordRequestEnd() 方法测试")
    class RecordRequestEndTests {

        @BeforeEach
        void setUp() {
            stubRedisOperations();
        }

        @Test
        @DisplayName("减少并发请求计数")
        void recordRequestEnd_decrementsConcurrentCount() {
            // Arrange — start 3 requests first
            accessMetricsService.recordRequestStart(null);
            accessMetricsService.recordRequestStart(null);
            accessMetricsService.recordRequestStart(null);

            AtomicLong concurrentRequests = (AtomicLong) ReflectionTestUtils.getField(
                    accessMetricsService, "concurrentRequests");
            assertEquals(3, concurrentRequests.get());

            // Act
            accessMetricsService.recordRequestEnd();

            // Assert
            assertEquals(2, concurrentRequests.get());
        }

        @Test
        @DisplayName("多次结束后并发计数正确递减")
        void recordRequestEnd_multipleCalls_decrementsCorrectly() {
            // Arrange
            accessMetricsService.recordRequestStart(null);
            accessMetricsService.recordRequestStart(null);

            // Act
            accessMetricsService.recordRequestEnd();
            accessMetricsService.recordRequestEnd();

            // Assert
            AtomicLong concurrentRequests = (AtomicLong) ReflectionTestUtils.getField(
                    accessMetricsService, "concurrentRequests");
            assertEquals(0, concurrentRequests.get());
        }

        @Test
        @DisplayName("结束次数过多时并发计数不会变成负数")
        void recordRequestEnd_doesNotGoBelowZero() {
            // Act
            accessMetricsService.recordRequestEnd();
            accessMetricsService.recordRequestEnd();

            // Assert
            AtomicLong concurrentRequests = (AtomicLong) ReflectionTestUtils.getField(
                    accessMetricsService, "concurrentRequests");
            assertEquals(0, concurrentRequests.get());
        }
    }

    // ==================== getRealTimeMetrics tests ====================

    @Nested
    @DisplayName("getRealTimeMetrics() 方法测试")
    class GetRealTimeMetricsTests {

        @BeforeEach
        void setUp() {
            stubRedisOperations();
        }

        @Test
        @DisplayName("返回包含所有必需键的 Map")
        void getRealTimeMetrics_returnsMapWithRequiredKeys() {
            // Arrange
            when(valueOperations.get(anyString())).thenReturn("42");
            when(setOperations.size(anyString())).thenReturn(10L);
            when(zSetOperations.zCard(anyString())).thenReturn(5L);

            // Act
            Map<String, Object> metrics = accessMetricsService.getRealTimeMetrics();

            // Assert
            assertNotNull(metrics);
            assertTrue(metrics.containsKey("todayRequests"), "应包含 todayRequests");
            assertTrue(metrics.containsKey("todayUniqueUsers"), "应包含 todayUniqueUsers");
            assertTrue(metrics.containsKey("activeUsers"), "应包含 activeUsers");
            assertTrue(metrics.containsKey("concurrentRequests"), "应包含 concurrentRequests");
            assertTrue(metrics.containsKey("peakConcurrentToday"), "应包含 peakConcurrentToday");
        }

        @Test
        @DisplayName("正确解析 Redis 中的请求数")
        void getRealTimeMetrics_parsesRedisValues() {
            // Arrange
            when(valueOperations.get(anyString())).thenReturn("123");
            when(setOperations.size(anyString())).thenReturn(45L);
            when(zSetOperations.zCard(anyString())).thenReturn(8L);

            // Act
            Map<String, Object> metrics = accessMetricsService.getRealTimeMetrics();

            // Assert
            assertEquals(123L, metrics.get("todayRequests"));
            assertEquals(45L, metrics.get("todayUniqueUsers"));
            assertEquals(8L, metrics.get("activeUsers"));
        }

        @Test
        @DisplayName("Redis 返回 null 时使用默认值 0")
        void getRealTimeMetrics_redisReturnsNull_usesDefaults() {
            // Arrange
            when(valueOperations.get(anyString())).thenReturn(null);
            when(setOperations.size(anyString())).thenReturn(null);
            when(zSetOperations.zCard(anyString())).thenReturn(null);

            // Act
            Map<String, Object> metrics = accessMetricsService.getRealTimeMetrics();

            // Assert
            assertEquals(0L, metrics.get("todayRequests"));
            assertEquals(0L, metrics.get("todayUniqueUsers"));
            assertEquals(0L, metrics.get("activeUsers"));
        }

        @Test
        @DisplayName("Redis 不可用时返回所有默认值")
        void getRealTimeMetrics_redisUnavailable_returnsDefaults() {
            // Arrange
            when(redisTemplate.opsForValue()).thenThrow(
                    new RedisConnectionFailureException("Connection refused"));

            // Act
            Map<String, Object> metrics = accessMetricsService.getRealTimeMetrics();

            // Assert
            assertNotNull(metrics);
            assertEquals(0L, metrics.get("todayRequests"));
            assertEquals(0L, metrics.get("todayUniqueUsers"));
            assertEquals(0L, metrics.get("activeUsers"));
            assertEquals(0L, metrics.get("concurrentRequests"));
            assertEquals(0L, metrics.get("peakConcurrentToday"));
        }

        @Test
        @DisplayName("反映内存中的并发请求计数")
        void getRealTimeMetrics_reflectsInMemoryConcurrentCount() {
            // Arrange
            when(valueOperations.get(anyString())).thenReturn("0");
            when(setOperations.size(anyString())).thenReturn(0L);
            when(zSetOperations.zCard(anyString())).thenReturn(0L);

            // Simulate 3 active requests
            accessMetricsService.recordRequestStart(null);
            accessMetricsService.recordRequestStart(null);
            accessMetricsService.recordRequestStart(null);

            // Act
            Map<String, Object> metrics = accessMetricsService.getRealTimeMetrics();

            // Assert
            assertEquals(3L, metrics.get("concurrentRequests"));
            assertEquals(3L, metrics.get("peakConcurrentToday"));
        }
    }

    // ==================== registerEmitter tests ====================

    @Nested
    @DisplayName("registerEmitter() 方法测试")
    class RegisterEmitterTests {

        @BeforeEach
        void setUp() {
            stubRedisOperations();
        }

        @Test
        @DisplayName("注册 SSE 发射器并加入列表")
        void registerEmitter_addsSseEmitterToList() {
            // Arrange
            when(valueOperations.get(anyString())).thenReturn("0");
            when(setOperations.size(anyString())).thenReturn(0L);
            when(zSetOperations.zCard(anyString())).thenReturn(0L);
            when(systemMetricsService.getSystemMetrics()).thenReturn(new LinkedHashMap<>());

            // Act
            SseEmitter emitter = accessMetricsService.registerEmitter();

            // Assert
            assertNotNull(emitter);
            @SuppressWarnings("unchecked")
            CopyOnWriteArrayList<SseEmitter> emitters = (CopyOnWriteArrayList<SseEmitter>)
                    ReflectionTestUtils.getField(accessMetricsService, "emitters");
            assertTrue(emitters.contains(emitter));
        }

        @Test
        @DisplayName("注册时发送初始数据")
        void registerEmitter_sendsInitialData() {
            // Arrange
            when(valueOperations.get(anyString())).thenReturn("10");
            when(setOperations.size(anyString())).thenReturn(3L);
            when(zSetOperations.zCard(anyString())).thenReturn(2L);

            Map<String, Object> systemMetrics = new LinkedHashMap<>();
            systemMetrics.put("uptimeMs", 12345L);
            when(systemMetricsService.getSystemMetrics()).thenReturn(systemMetrics);

            // Act
            SseEmitter emitter = accessMetricsService.registerEmitter();

            // Assert — emitter was created and initial data was sent (no exception)
            assertNotNull(emitter);
            // Verify systemMetricsService was called for initial data
            verify(systemMetricsService).getSystemMetrics();
        }

        @Test
        @DisplayName("多个发射器可以同时注册")
        void registerEmitter_multipleEmitters_allRegistered() {
            // Arrange
            when(valueOperations.get(anyString())).thenReturn("0");
            when(setOperations.size(anyString())).thenReturn(0L);
            when(zSetOperations.zCard(anyString())).thenReturn(0L);
            when(systemMetricsService.getSystemMetrics()).thenReturn(new LinkedHashMap<>());

            // Act
            SseEmitter emitter1 = accessMetricsService.registerEmitter();
            SseEmitter emitter2 = accessMetricsService.registerEmitter();

            // Assert
            @SuppressWarnings("unchecked")
            CopyOnWriteArrayList<SseEmitter> emitters = (CopyOnWriteArrayList<SseEmitter>)
                    ReflectionTestUtils.getField(accessMetricsService, "emitters");
            assertEquals(2, emitters.size());
            assertTrue(emitters.contains(emitter1));
            assertTrue(emitters.contains(emitter2));
        }
    }
}
