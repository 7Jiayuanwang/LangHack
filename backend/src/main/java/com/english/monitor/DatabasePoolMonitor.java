package com.english.monitor;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据库连接池监控器
 * 定期收集连接池指标并记录异常情况
 */
@Component
public class DatabasePoolMonitor {

    private static final Logger log = LoggerFactory.getLogger(DatabasePoolMonitor.class);

    @Autowired
    @Qualifier("masterDataSource")
    private DataSource masterDataSource;

    @Autowired(required = false)
    @Qualifier("slaveDataSource")
    private DataSource slaveDataSource;

    // 历史峰值记录
    private int masterMaxActiveConnections = 0;
    private int slaveMaxActiveConnections = 0;

    /**
     * 每分钟收集一次连接池指标
     */
    @Scheduled(fixedRate = 60000)
    public void monitorConnectionPools() {
        try {
            Map<String, Object> masterMetrics = collectPoolMetrics("master", masterDataSource);
            logPoolMetrics("Master", masterMetrics);

            if (slaveDataSource != null) {
                Map<String, Object> slaveMetrics = collectPoolMetrics("slave", slaveDataSource);
                logPoolMetrics("Slave", slaveMetrics);
            }
        } catch (Exception e) {
            log.error("连接池监控失败", e);
        }
    }

    private Map<String, Object> collectPoolMetrics(String poolName, DataSource dataSource) {
        Map<String, Object> metrics = new HashMap<>();

        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDS = (HikariDataSource) dataSource;
            HikariPoolMXBean poolMXBean = hikariDS.getHikariPoolMXBean();

            int activeConnections = poolMXBean.getActiveConnections();
            int idleConnections = poolMXBean.getIdleConnections();
            int totalConnections = poolMXBean.getTotalConnections();
            int threadsAwaitingConnection = poolMXBean.getThreadsAwaitingConnection();

            metrics.put("active", activeConnections);
            metrics.put("idle", idleConnections);
            metrics.put("total", totalConnections);
            metrics.put("awaiting", threadsAwaitingConnection);
            metrics.put("maxPoolSize", hikariDS.getMaximumPoolSize());

            // 更新峰值
            if ("master".equals(poolName)) {
                if (activeConnections > masterMaxActiveConnections) {
                    masterMaxActiveConnections = activeConnections;
                    metrics.put("isNewPeak", true);
                }
                metrics.put("peakActive", masterMaxActiveConnections);
            } else {
                if (activeConnections > slaveMaxActiveConnections) {
                    slaveMaxActiveConnections = activeConnections;
                    metrics.put("isNewPeak", true);
                }
                metrics.put("peakActive", slaveMaxActiveConnections);
            }

            // 告警阈值检查
            double utilizationRate = (double) activeConnections / hikariDS.getMaximumPoolSize();
            if (utilizationRate > 0.8) {
                log.warn("[{}连接池] 使用率超过80%: active={}, max={}, utilization={:.1f}%",
                        poolName, activeConnections, hikariDS.getMaximumPoolSize(), utilizationRate * 100);
            }

            if (threadsAwaitingConnection > 0) {
                log.warn("[{}连接池] 有{}个线程正在等待连接，可能存在连接池耗尽风险",
                        poolName, threadsAwaitingConnection);
            }
        }

        return metrics;
    }

    private void logPoolMetrics(String poolName, Map<String, Object> metrics) {
        if (Boolean.TRUE.equals(metrics.get("isNewPeak"))) {
            log.info("[{}连接池] 新峰值: active={}, idle={}, total={}, peak={}, awaiting={}",
                    poolName,
                    metrics.get("active"),
                    metrics.get("idle"),
                    metrics.get("total"),
                    metrics.get("peakActive"),
                    metrics.get("awaiting"));
        } else {
            log.debug("[{}连接池] active={}, idle={}, total={}, peak={}, awaiting={}",
                    poolName,
                    metrics.get("active"),
                    metrics.get("idle"),
                    metrics.get("total"),
                    metrics.get("peakActive"),
                    metrics.get("awaiting"));
        }
    }

    public Map<String, Object> getPoolMetrics() {
        Map<String, Object> result = new HashMap<>();
        result.put("master", collectPoolMetrics("master", masterDataSource));
        if (slaveDataSource != null) {
            result.put("slave", collectPoolMetrics("slave", slaveDataSource));
        }
        return result;
    }
}
