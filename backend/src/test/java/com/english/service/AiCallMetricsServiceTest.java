package com.english.service;

import com.english.entity.AiCallLog;
import com.english.mapper.AiCallLogMapper;
import com.english.service.impl.AiCallMetricsService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiCallMetricsService 单元测试")
class AiCallMetricsServiceTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AiCallMetricsService aiCallMetricsService;

    @SuppressWarnings("unchecked")
    private ConcurrentLinkedQueue<AiCallLog> getBuffer() {
        return (ConcurrentLinkedQueue<AiCallLog>) ReflectionTestUtils.getField(aiCallMetricsService, "buffer");
    }

    @BeforeEach
    void setUp() {
        // Clear the buffer before each test to prevent state leakage
        getBuffer().clear();
    }

    // ========== startTimer ==========

    @Nested
    @DisplayName("startTimer 方法")
    class StartTimerTests {

        @Test
        @DisplayName("startTimer — 返回当前系统时间（误差在100ms以内）")
        void startTimer_returnsCurrentTimeWithinRange() {
            long before = System.currentTimeMillis();
            long result = aiCallMetricsService.startTimer();
            long after = System.currentTimeMillis();

            assertThat(result).isBetween(before, after);
        }
    }

    // ========== recordSuccess ==========

    @Nested
    @DisplayName("recordSuccess 方法")
    class RecordSuccessTests {

        @Test
        @DisplayName("recordSuccess — 向缓冲区添加 success=true 的日志条目")
        void recordSuccess_addsSuccessEntryToBuffer() {
            // Arrange
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            long startTime = System.currentTimeMillis() - 150;

            // Act
            aiCallMetricsService.recordSuccess("OPENROUTER", "chat", startTime, "testuser");

            // Assert
            ConcurrentLinkedQueue<AiCallLog> buffer = getBuffer();
            assertThat(buffer).hasSize(1);

            AiCallLog log = buffer.peek();
            assertThat(log).isNotNull();
            assertThat(log.getServiceName()).isEqualTo("OPENROUTER");
            assertThat(log.getMethodName()).isEqualTo("chat");
            assertThat(log.getSuccess()).isTrue();
            assertThat(log.getErrorMessage()).isNull();
            assertThat(log.getTriggeredBy()).isEqualTo("testuser");
            assertThat(log.getResponseTimeMs()).isGreaterThanOrEqualTo(150);
            assertThat(log.getCreatedTime()).isNotNull();
        }

        @Test
        @DisplayName("recordSuccess（带modelName）— 设置模型名称")
        void recordSuccess_withModelName_setsModelName() {
            // Arrange
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            long startTime = System.currentTimeMillis() - 50;

            // Act
            aiCallMetricsService.recordSuccess("OPENROUTER", "chat", "gpt-4", startTime, "admin");

            // Assert
            ConcurrentLinkedQueue<AiCallLog> buffer = getBuffer();
            assertThat(buffer).hasSize(1);

            AiCallLog log = buffer.peek();
            assertThat(log).isNotNull();
            assertThat(log.getModelName()).isEqualTo("gpt-4");
            assertThat(log.getSuccess()).isTrue();
        }
    }

    // ========== recordFailure ==========

    @Nested
    @DisplayName("recordFailure 方法")
    class RecordFailureTests {

        @Test
        @DisplayName("recordFailure — 向缓冲区添加 success=false 并记录错误信息")
        void recordFailure_addsFailureEntryWithErrorMessage() {
            // Arrange
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            long startTime = System.currentTimeMillis() - 200;

            // Act
            aiCallMetricsService.recordFailure("TENCENT_ASR", "recognize",
                    startTime, "Connection timeout", "student1");

            // Assert
            ConcurrentLinkedQueue<AiCallLog> buffer = getBuffer();
            assertThat(buffer).hasSize(1);

            AiCallLog log = buffer.peek();
            assertThat(log).isNotNull();
            assertThat(log.getServiceName()).isEqualTo("TENCENT_ASR");
            assertThat(log.getMethodName()).isEqualTo("recognize");
            assertThat(log.getSuccess()).isFalse();
            assertThat(log.getErrorMessage()).isEqualTo("Connection timeout");
            assertThat(log.getTriggeredBy()).isEqualTo("student1");
        }

        @Test
        @DisplayName("recordFailure — 超长错误信息截断为500字符")
        void recordFailure_truncatesLongErrorMessage() {
            // Arrange
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String longError = "E".repeat(1000);
            long startTime = System.currentTimeMillis() - 100;

            // Act
            aiCallMetricsService.recordFailure("HIAGENT", "query",
                    startTime, longError, "user1");

            // Assert
            ConcurrentLinkedQueue<AiCallLog> buffer = getBuffer();
            AiCallLog log = buffer.peek();
            assertThat(log).isNotNull();
            assertThat(log.getErrorMessage()).hasSize(500);
        }
    }

    // ========== recordCall ==========

    @Nested
    @DisplayName("recordCall 方法")
    class RecordCallTests {

        @Test
        @DisplayName("recordCall — 创建包含所有字段的 AiCallLog 条目")
        void recordCall_createsLogWithAllFieldsSet() {
            // Arrange
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // Act
            aiCallMetricsService.recordCall("OPENROUTER", "chat", "claude-3",
                    true, 350L, null, 100, 200, "admin");

            // Assert
            ConcurrentLinkedQueue<AiCallLog> buffer = getBuffer();
            assertThat(buffer).hasSize(1);

            AiCallLog log = buffer.peek();
            assertThat(log).isNotNull();
            assertThat(log.getServiceName()).isEqualTo("OPENROUTER");
            assertThat(log.getMethodName()).isEqualTo("chat");
            assertThat(log.getModelName()).isEqualTo("claude-3");
            assertThat(log.getSuccess()).isTrue();
            assertThat(log.getResponseTimeMs()).isEqualTo(350L);
            assertThat(log.getErrorMessage()).isNull();
            assertThat(log.getInputTokens()).isEqualTo(100);
            assertThat(log.getOutputTokens()).isEqualTo(200);
            assertThat(log.getTriggeredBy()).isEqualTo("admin");
            assertThat(log.getCreatedTime()).isNotNull();
        }

        @Test
        @DisplayName("recordCall — Redis异常不影响缓冲区写入")
        void recordCall_redisException_stillBuffersLog() {
            // Arrange: Redis throws exception
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

            // Act — recordCall catches all exceptions internally, so this should not throw,
            // but since Redis fails in updateRedisCounters, the log should still be in buffer
            // (the try-catch in recordCall wraps everything including updateRedisCounters)
            aiCallMetricsService.recordCall("HIAGENT", "query", null,
                    true, 100L, null, null, null, "user1");

            // Assert — The entire recordCall is wrapped in try-catch, so if Redis fails
            // during updateRedisCounters after buffer.offer(), the log is still buffered
            // But if it fails before offer(), buffer is empty. Let's check the actual behavior:
            // buffer.offer() happens at line 84, updateRedisCounters at line 87 — offer first
            ConcurrentLinkedQueue<AiCallLog> buffer = getBuffer();
            assertThat(buffer).hasSize(1);
        }
    }

    // ========== flush ==========

    @Nested
    @DisplayName("flush 方法")
    class FlushTests {

        @Test
        @DisplayName("flush — 将缓冲区条目批量写入数据库")
        void flush_flushesBufferedEntriesToDb() {
            // Arrange: manually add items to buffer
            ConcurrentLinkedQueue<AiCallLog> buffer = getBuffer();
            AiCallLog log1 = new AiCallLog();
            log1.setServiceName("OPENROUTER");
            AiCallLog log2 = new AiCallLog();
            log2.setServiceName("HIAGENT");
            buffer.offer(log1);
            buffer.offer(log2);

            // Act
            aiCallMetricsService.flush();

            // Assert
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<AiCallLog>> captor = ArgumentCaptor.forClass(List.class);
            verify(aiCallLogMapper).batchInsert(captor.capture());

            List<AiCallLog> flushedBatch = captor.getValue();
            assertThat(flushedBatch).hasSize(2);
            assertThat(flushedBatch.get(0).getServiceName()).isEqualTo("OPENROUTER");
            assertThat(flushedBatch.get(1).getServiceName()).isEqualTo("HIAGENT");

            // Buffer should be empty after flush
            assertThat(buffer).isEmpty();
        }

        @Test
        @DisplayName("flush — 缓冲区为空时不执行任何操作")
        void flush_doesNothingWhenBufferIsEmpty() {
            // Arrange: buffer is already empty (cleared in @BeforeEach)

            // Act
            aiCallMetricsService.flush();

            // Assert
            verifyNoInteractions(aiCallLogMapper);
        }

        @Test
        @DisplayName("flush — 数据库写入异常不会抛出，日志丢失")
        void flush_dbExceptionDoesNotThrow() {
            // Arrange
            ConcurrentLinkedQueue<AiCallLog> buffer = getBuffer();
            AiCallLog log = new AiCallLog();
            log.setServiceName("OPENROUTER");
            buffer.offer(log);

            doThrow(new RuntimeException("DB connection lost"))
                    .when(aiCallLogMapper).batchInsert(anyList());

            // Act & Assert — should not throw
            assertThatCode(() -> aiCallMetricsService.flush())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("flush — OOM安全：队列容量上限为 MAX_QUEUE_SIZE (500)")
        void flush_queueCappedAtMaxSize() {
            // Arrange: Record more than MAX_QUEUE_SIZE (500) entries
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            for (int i = 0; i < 600; i++) {
                aiCallMetricsService.recordCall("SVC", "method", null,
                        true, 10L, null, null, null, "user");
            }

            // Assert: buffer should never exceed MAX_QUEUE_SIZE
            ConcurrentLinkedQueue<AiCallLog> buffer = getBuffer();
            assertThat(buffer.size()).isLessThanOrEqualTo(500);
        }

        @Test
        @DisplayName("flush — 批次大小不超过 BATCH_SIZE (200)")
        void flush_batchSizeLimited() {
            // Arrange: add 300 items to buffer
            ConcurrentLinkedQueue<AiCallLog> buffer = getBuffer();
            for (int i = 0; i < 300; i++) {
                AiCallLog log = new AiCallLog();
                log.setServiceName("SVC_" + i);
                buffer.offer(log);
            }

            // Act
            aiCallMetricsService.flush();

            // Assert: only up to 200 should be flushed in one batch
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<AiCallLog>> captor = ArgumentCaptor.forClass(List.class);
            verify(aiCallLogMapper).batchInsert(captor.capture());
            assertThat(captor.getValue().size()).isLessThanOrEqualTo(200);

            // Remaining items should still be in buffer
            assertThat(buffer.size()).isEqualTo(100);
        }
    }

    // ========== getTodayStats ==========

    @Nested
    @DisplayName("getTodayStats 方法")
    class GetTodayStatsTests {

        @Test
        @DisplayName("getTodayStats — Redis有数据时返回统计信息")
        void getTodayStats_returnsStatsFromRedis() {
            // Arrange
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // Mock Redis values for HIAGENT service
            when(valueOperations.get(contains(":HIAGENT:count:")))
                    .thenReturn("100");
            when(valueOperations.get(contains(":HIAGENT:success:")))
                    .thenReturn("95");
            when(valueOperations.get(contains(":HIAGENT:total_time:")))
                    .thenReturn("5000");

            // Other services return null (no data)
            when(valueOperations.get(contains(":OPENROUTER:count:")))
                    .thenReturn(null);
            when(valueOperations.get(contains(":OPENROUTER:success:")))
                    .thenReturn(null);
            when(valueOperations.get(contains(":OPENROUTER:total_time:")))
                    .thenReturn(null);

            when(valueOperations.get(contains(":TENCENT_TRANSLATE:count:")))
                    .thenReturn(null);
            when(valueOperations.get(contains(":TENCENT_TRANSLATE:success:")))
                    .thenReturn(null);
            when(valueOperations.get(contains(":TENCENT_TRANSLATE:total_time:")))
                    .thenReturn(null);

            when(valueOperations.get(contains(":TENCENT_ASR:count:")))
                    .thenReturn(null);
            when(valueOperations.get(contains(":TENCENT_ASR:success:")))
                    .thenReturn(null);
            when(valueOperations.get(contains(":TENCENT_ASR:total_time:")))
                    .thenReturn(null);

            when(valueOperations.get(contains(":BILIBILI:count:")))
                    .thenReturn(null);
            when(valueOperations.get(contains(":BILIBILI:success:")))
                    .thenReturn(null);
            when(valueOperations.get(contains(":BILIBILI:total_time:")))
                    .thenReturn(null);

            // Act
            Map<String, Object> result = aiCallMetricsService.getTodayStats();

            // Assert
            assertThat(result).containsKeys("HIAGENT", "OPENROUTER",
                    "OPENAI_COMPATIBLE", "TENCENT_TRANSLATE",
                    "HIAGENT_TRANSLATE", "TENCENT_ASR", "BILIBILI");

            @SuppressWarnings("unchecked")
            Map<String, Object> hiagentStats = (Map<String, Object>) result.get("HIAGENT");
            assertThat(hiagentStats.get("totalCalls")).isEqualTo(100L);
            assertThat(hiagentStats.get("successCalls")).isEqualTo(95L);
            assertThat(hiagentStats.get("failedCalls")).isEqualTo(5L);
            assertThat(hiagentStats.get("successRate")).isEqualTo(95L);
            assertThat(hiagentStats.get("avgResponseTimeMs")).isEqualTo(50L);
        }

        @Test
        @DisplayName("getTodayStats — Redis不可用时降级返回默认值")
        void getTodayStats_fallsBackWhenRedisUnavailable() {
            // Arrange: Redis throws exception
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));

            // Act
            Map<String, Object> result = aiCallMetricsService.getTodayStats();

            // Assert: all services should have default (zeroed) stats
            assertThat(result).hasSize(7);

            for (Object serviceStatsObj : result.values()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> serviceStats = (Map<String, Object>) serviceStatsObj;
                assertThat(serviceStats.get("totalCalls")).isEqualTo(0);
                assertThat(serviceStats.get("successCalls")).isEqualTo(0);
                assertThat(serviceStats.get("failedCalls")).isEqualTo(0);
                assertThat(serviceStats.get("successRate")).isEqualTo(100);
                assertThat(serviceStats.get("avgResponseTimeMs")).isEqualTo(0);
            }
        }

        @Test
        @DisplayName("getTodayStats — Redis返回null值时默认为0")
        void getTodayStats_nullRedisValuesDefaultToZero() {
            // Arrange
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            // All gets return null
            when(valueOperations.get(anyString())).thenReturn(null);

            // Act
            Map<String, Object> result = aiCallMetricsService.getTodayStats();

            // Assert
            assertThat(result).hasSize(7);

            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) result.get("OPENROUTER");
            assertThat(stats.get("totalCalls")).isEqualTo(0L);
            assertThat(stats.get("successCalls")).isEqualTo(0L);
            assertThat(stats.get("failedCalls")).isEqualTo(0L);
            assertThat(stats.get("successRate")).isEqualTo(100L);
            assertThat(stats.get("avgResponseTimeMs")).isEqualTo(0L);
        }
    }
}
