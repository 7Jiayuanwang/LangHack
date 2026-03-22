package com.english.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SystemMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(SystemMetricsService.class);

    @Value("${backup.dir:./backups}")
    private String backupDir;

    public Map<String, Object> getSystemMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        try {
            // JVM Heap
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memoryBean.getHeapMemoryUsage();
            Map<String, Object> jvm = new LinkedHashMap<>();
            jvm.put("heapUsed", heap.getUsed());
            jvm.put("heapMax", heap.getMax());
            jvm.put("heapTotal", heap.getCommitted());
            jvm.put("heapUsagePercent", heap.getMax() > 0
                    ? Math.round((double) heap.getUsed() / heap.getMax() * 100) : 0);
            metrics.put("jvm", jvm);

            // CPU
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            Map<String, Object> cpu = new LinkedHashMap<>();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean =
                        (com.sun.management.OperatingSystemMXBean) osBean;
                double cpuLoad = sunOsBean.getProcessCpuLoad();
                cpu.put("processCpuLoad", Math.round(cpuLoad * 100));
                cpu.put("systemCpuLoad", Math.round(sunOsBean.getSystemCpuLoad() * 100));
            } else {
                cpu.put("processCpuLoad", -1);
                cpu.put("systemCpuLoad", -1);
            }
            cpu.put("availableProcessors", osBean.getAvailableProcessors());
            metrics.put("cpu", cpu);

            // Threads
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            Map<String, Object> threads = new LinkedHashMap<>();
            threads.put("count", threadBean.getThreadCount());
            threads.put("peakCount", threadBean.getPeakThreadCount());
            threads.put("daemonCount", threadBean.getDaemonThreadCount());
            metrics.put("threads", threads);

            // Disk (backup directory)
            File diskDir = new File(backupDir);
            if (diskDir.exists()) {
                Map<String, Object> disk = new LinkedHashMap<>();
                long totalSpace = diskDir.getTotalSpace();
                long freeSpace = diskDir.getUsableSpace();
                long usedSpace = totalSpace - freeSpace;
                disk.put("total", totalSpace);
                disk.put("free", freeSpace);
                disk.put("used", usedSpace);
                disk.put("usagePercent", totalSpace > 0
                        ? Math.round((double) usedSpace / totalSpace * 100) : 0);
                metrics.put("disk", disk);
            }

            // Uptime
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            metrics.put("uptimeMs", runtimeBean.getUptime());

        } catch (Exception e) {
            logger.debug("Failed to collect system metrics", e);
        }

        return metrics;
    }
}
