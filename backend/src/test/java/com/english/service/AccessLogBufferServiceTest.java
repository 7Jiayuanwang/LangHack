package com.english.service;

import com.english.entity.AccessLog;
import com.english.mapper.AccessLogMapper;
import com.english.service.impl.AccessLogBufferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccessLogBufferService.
 *
 * Implementation facts:
 * - Uses ConcurrentLinkedQueue<AccessLog> as internal buffer
 * - MAX_QUEUE_SIZE = 1000, BATCH_SIZE = 500
 * - add() drops oldest entry when queue exceeds MAX_QUEUE_SIZE
 * - flush() is @Scheduled(fixedRate=10000), drains up to BATCH_SIZE entries
 * - flush() catches all exceptions from batchInsert (records are lost on failure)
 */
@ExtendWith(MockitoExtension.class)
class AccessLogBufferServiceTest {

    @InjectMocks
    private AccessLogBufferService accessLogBufferService;

    @Mock
    private AccessLogMapper accessLogMapper;

    private AccessLog createTestLog(String uri) {
        AccessLog log = new AccessLog();
        log.setMethod("GET");
        log.setUri(uri);
        log.setStatusCode(200);
        log.setResponseTimeMs(50L);
        log.setIpAddress("127.0.0.1");
        log.setCreatedTime(LocalDateTime.now());
        return log;
    }

    @SuppressWarnings("unchecked")
    private ConcurrentLinkedQueue<AccessLog> getBuffer() {
        return (ConcurrentLinkedQueue<AccessLog>) ReflectionTestUtils.getField(
                accessLogBufferService, "buffer");
    }

    // ==================== add tests ====================

    @Nested
    @DisplayName("add() 方法测试")
    class AddTests {

        @Test
        @DisplayName("添加日志条目到缓冲队列")
        void add_addsLogEntryToBuffer() {
            // Arrange
            AccessLog log = createTestLog("/api/test");

            // Act
            accessLogBufferService.add(log);

            // Assert
            ConcurrentLinkedQueue<AccessLog> buffer = getBuffer();
            assertEquals(1, buffer.size());
            assertSame(log, buffer.peek());
        }

        @Test
        @DisplayName("多次添加日志条目到缓冲队列")
        void add_multipleEntries_allAddedToBuffer() {
            // Arrange
            AccessLog log1 = createTestLog("/api/test1");
            AccessLog log2 = createTestLog("/api/test2");
            AccessLog log3 = createTestLog("/api/test3");

            // Act
            accessLogBufferService.add(log1);
            accessLogBufferService.add(log2);
            accessLogBufferService.add(log3);

            // Assert
            ConcurrentLinkedQueue<AccessLog> buffer = getBuffer();
            assertEquals(3, buffer.size());
        }

        @Test
        @DisplayName("队列超过 MAX_QUEUE_SIZE 时丢弃最旧条目防止 OOM")
        void add_exceedsMaxQueueSize_dropsOldest() {
            // Arrange — fill buffer to MAX_QUEUE_SIZE (1000)
            ConcurrentLinkedQueue<AccessLog> buffer = getBuffer();
            AccessLog firstLog = createTestLog("/api/first");
            buffer.offer(firstLog);
            for (int i = 1; i < 1000; i++) {
                buffer.offer(createTestLog("/api/fill/" + i));
            }
            assertEquals(1000, buffer.size());

            // Act — add one more, should drop the oldest (firstLog)
            AccessLog newLog = createTestLog("/api/new");
            accessLogBufferService.add(newLog);

            // Assert
            assertEquals(1000, buffer.size());
            // The oldest (firstLog) should have been polled
            assertNotSame(firstLog, buffer.peek());
            // The new log should be at the tail (last element)
            assertTrue(buffer.contains(newLog));
        }
    }

    // ==================== flush tests ====================

    @Nested
    @DisplayName("flush() 方法测试")
    class FlushTests {

        @Test
        @DisplayName("将缓冲条目刷新到数据库")
        void flush_bufferedEntries_callsBatchInsert() {
            // Arrange
            AccessLog log1 = createTestLog("/api/test1");
            AccessLog log2 = createTestLog("/api/test2");
            accessLogBufferService.add(log1);
            accessLogBufferService.add(log2);

            // Act
            accessLogBufferService.flush();

            // Assert
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<AccessLog>> captor = ArgumentCaptor.forClass(List.class);
            verify(accessLogMapper).batchInsert(captor.capture());
            List<AccessLog> flushed = captor.getValue();
            assertEquals(2, flushed.size());
            assertTrue(flushed.contains(log1));
            assertTrue(flushed.contains(log2));

            // Buffer should be empty after flush
            ConcurrentLinkedQueue<AccessLog> buffer = getBuffer();
            assertEquals(0, buffer.size());
        }

        @Test
        @DisplayName("缓冲区为空时不执行任何操作")
        void flush_emptyBuffer_doesNothing() {
            // Act
            accessLogBufferService.flush();

            // Assert
            verify(accessLogMapper, never()).batchInsert(anyList());
        }

        @Test
        @DisplayName("数据库异常时优雅处理不抛出异常")
        void flush_dbException_handlesGracefully() {
            // Arrange
            accessLogBufferService.add(createTestLog("/api/test"));
            doThrow(new RuntimeException("DB connection lost"))
                    .when(accessLogMapper).batchInsert(anyList());

            // Act & Assert — should not throw
            assertDoesNotThrow(() -> accessLogBufferService.flush());
            verify(accessLogMapper).batchInsert(anyList());
        }

        @Test
        @DisplayName("单次刷新不超过 BATCH_SIZE 条记录")
        void flush_respectsBatchSizeLimit() {
            // Arrange — add 600 entries (BATCH_SIZE = 500)
            for (int i = 0; i < 600; i++) {
                accessLogBufferService.add(createTestLog("/api/test/" + i));
            }

            // Act
            accessLogBufferService.flush();

            // Assert — only 500 should be flushed
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<AccessLog>> captor = ArgumentCaptor.forClass(List.class);
            verify(accessLogMapper).batchInsert(captor.capture());
            List<AccessLog> flushed = captor.getValue();
            assertEquals(500, flushed.size());

            // 100 entries should remain in buffer
            ConcurrentLinkedQueue<AccessLog> buffer = getBuffer();
            assertEquals(100, buffer.size());
        }
    }
}
