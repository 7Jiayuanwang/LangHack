package com.english.service.impl;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class SystemMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(SystemMetricsService.class);

    private final DataSource masterDataSource;
    private final DataSource slaveDataSource;
    private final StringRedisTemplate redisTemplate;

    @Value("${backup.dir:./backups}")
    private String backupDir;

    public SystemMetricsService(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("slaveDataSource") DataSource slaveDataSource,
            StringRedisTemplate redisTemplate) {
        this.masterDataSource = masterDataSource;
        this.slaveDataSource = slaveDataSource;
        this.redisTemplate = redisTemplate;
    }

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

            // DB Connection Pool (HikariCP master + slave)
            try {
                Map<String, Object> dbPool = new LinkedHashMap<>();
                dbPool.put("master", collectPoolStats(masterDataSource));
                dbPool.put("slave", collectPoolStats(slaveDataSource));
                metrics.put("dbPool", dbPool);
            } catch (Exception e) {
                logger.debug("Failed to collect DB pool metrics", e);
            }

            // Redis
            try {
                metrics.put("redis", collectRedisMetrics());
            } catch (Exception e) {
                Map<String, Object> redis = new LinkedHashMap<>();
                redis.put("connected", false);
                metrics.put("redis", redis);
                logger.debug("Failed to collect Redis metrics", e);
            }

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

    private Map<String, Object> collectPoolStats(DataSource ds) {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (ds instanceof HikariDataSource) {
            HikariDataSource hikari = (HikariDataSource) ds;
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool != null) {
                stats.put("active", pool.getActiveConnections());
                stats.put("idle", pool.getIdleConnections());
                stats.put("total", pool.getTotalConnections());
                stats.put("max", hikari.getMaximumPoolSize());
                stats.put("pending", pool.getThreadsAwaitingConnection());
            } else {
                // Pool not yet started
                stats.put("active", 0);
                stats.put("idle", 0);
                stats.put("total", 0);
                stats.put("max", hikari.getMaximumPoolSize());
                stats.put("pending", 0);
            }
        }
        return stats;
    }

    private Map<String, Object> collectRedisMetrics() {
        Map<String, Object> redis = new LinkedHashMap<>();
        RedisConnection conn = redisTemplate.getRequiredConnectionFactory().getConnection();
        try {
            // Memory info
            Properties memInfo = conn.info("memory");
            if (memInfo != null) {
                long usedMemory = parseLongSafe(memInfo.getProperty("used_memory"));
                long maxMemory = parseLongSafe(memInfo.getProperty("maxmemory"));
                redis.put("usedMemory", usedMemory);
                redis.put("maxMemory", maxMemory);
                redis.put("usagePercent", maxMemory > 0
                        ? Math.round((double) usedMemory / maxMemory * 100) : 0);
            }

            // Client info
            Properties clientInfo = conn.info("clients");
            if (clientInfo != null) {
                redis.put("connectedClients", (int) parseLongSafe(clientInfo.getProperty("connected_clients")));
            }

            redis.put("connected", true);
        } finally {
            conn.close();
        }
        return redis;
    }

    private long parseLongSafe(String value) {
        if (value == null || value.isEmpty()) return 0L;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
