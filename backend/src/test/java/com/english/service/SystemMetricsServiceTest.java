package com.english.service;

import com.english.service.impl.SystemMetricsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SystemMetricsService.
 *
 * Implementation facts:
 * - getSystemMetrics() returns a LinkedHashMap with keys: jvm, cpu, threads, disk, uptimeMs
 * - JVM heap metrics from ManagementFactory.getMemoryMXBean()
 * - CPU metrics from ManagementFactory.getOperatingSystemMXBean() (com.sun cast)
 * - Thread metrics from ManagementFactory.getThreadMXBean()
 * - Disk metrics from File(backupDir) if directory exists
 * - Uptime from ManagementFactory.getRuntimeMXBean()
 * - All wrapped in try/catch — returns empty map on failure
 */
@ExtendWith(MockitoExtension.class)
class SystemMetricsServiceTest {

    @InjectMocks
    private SystemMetricsService systemMetricsService;

    // ==================== getSystemMetrics tests ====================

    @Nested
    @DisplayName("getSystemMetrics() 方法测试")
    class GetMetricsTests {

        @Test
        @DisplayName("返回包含 jvm、cpu、threads、uptimeMs 等必需键的 Map")
        void getMetrics_returnsMapWithRequiredKeys() {
            // Arrange
            ReflectionTestUtils.setField(systemMetricsService, "backupDir", "./backups");

            // Act
            Map<String, Object> metrics = systemMetricsService.getSystemMetrics();

            // Assert
            assertNotNull(metrics);
            assertTrue(metrics.containsKey("jvm"), "应包含 jvm 键");
            assertTrue(metrics.containsKey("cpu"), "应包含 cpu 键");
            assertTrue(metrics.containsKey("threads"), "应包含 threads 键");
            assertTrue(metrics.containsKey("uptimeMs"), "应包含 uptimeMs 键");
        }

        @Test
        @DisplayName("JVM 堆内存值为非负数")
        @SuppressWarnings("unchecked")
        void getMetrics_jvmHeapValuesAreNonNegative() {
            // Arrange
            ReflectionTestUtils.setField(systemMetricsService, "backupDir", "./backups");

            // Act
            Map<String, Object> metrics = systemMetricsService.getSystemMetrics();

            // Assert
            Map<String, Object> jvm = (Map<String, Object>) metrics.get("jvm");
            assertNotNull(jvm, "jvm 指标不应为 null");

            long heapUsed = (long) jvm.get("heapUsed");
            long heapMax = (long) jvm.get("heapMax");
            long heapTotal = (long) jvm.get("heapTotal");

            assertTrue(heapUsed >= 0, "heapUsed 应 >= 0, 实际值: " + heapUsed);
            assertTrue(heapMax >= -1, "heapMax 应 >= -1 (未定义时为 -1), 实际值: " + heapMax);
            assertTrue(heapTotal >= 0, "heapTotal 应 >= 0, 实际值: " + heapTotal);
        }

        @Test
        @DisplayName("线程数大于 0")
        @SuppressWarnings("unchecked")
        void getMetrics_threadCountGreaterThanZero() {
            // Arrange
            ReflectionTestUtils.setField(systemMetricsService, "backupDir", "./backups");

            // Act
            Map<String, Object> metrics = systemMetricsService.getSystemMetrics();

            // Assert
            Map<String, Object> threads = (Map<String, Object>) metrics.get("threads");
            assertNotNull(threads, "threads 指标不应为 null");

            int threadCount = (int) threads.get("count");
            assertTrue(threadCount > 0, "线程数应 > 0, 实际值: " + threadCount);

            int peakCount = (int) threads.get("peakCount");
            assertTrue(peakCount > 0, "峰值线程数应 > 0, 实际值: " + peakCount);

            int daemonCount = (int) threads.get("daemonCount");
            assertTrue(daemonCount >= 0, "守护线程数应 >= 0, 实际值: " + daemonCount);
        }

        @Test
        @DisplayName("运行时间大于 0")
        void getMetrics_uptimeGreaterThanZero() {
            // Arrange
            ReflectionTestUtils.setField(systemMetricsService, "backupDir", "./backups");

            // Act
            Map<String, Object> metrics = systemMetricsService.getSystemMetrics();

            // Assert
            long uptimeMs = (long) metrics.get("uptimeMs");
            assertTrue(uptimeMs > 0, "JVM 运行时间应 > 0, 实际值: " + uptimeMs);
        }

        @Test
        @DisplayName("CPU 指标包含 availableProcessors")
        @SuppressWarnings("unchecked")
        void getMetrics_cpuContainsProcessorCount() {
            // Arrange
            ReflectionTestUtils.setField(systemMetricsService, "backupDir", "./backups");

            // Act
            Map<String, Object> metrics = systemMetricsService.getSystemMetrics();

            // Assert
            Map<String, Object> cpu = (Map<String, Object>) metrics.get("cpu");
            assertNotNull(cpu, "cpu 指标不应为 null");

            int processors = (int) cpu.get("availableProcessors");
            assertTrue(processors > 0, "可用处理器数应 > 0, 实际值: " + processors);
        }

        @Test
        @DisplayName("backupDir 不存在时不包含 disk 键但不报错")
        void getMetrics_backupDirNotExist_noDiskKeyButNoError() {
            // Arrange — use a non-existent directory
            ReflectionTestUtils.setField(systemMetricsService, "backupDir",
                    "./non_existent_dir_" + System.nanoTime());

            // Act
            Map<String, Object> metrics = systemMetricsService.getSystemMetrics();

            // Assert
            assertNotNull(metrics);
            assertFalse(metrics.containsKey("disk"), "不存在的目录不应产生 disk 指标");
            // Other metrics should still be present
            assertTrue(metrics.containsKey("jvm"));
            assertTrue(metrics.containsKey("uptimeMs"));
        }
    }
}
