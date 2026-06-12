package com.english.monitor;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Component
public class DatabasePoolMonitor {

    private static final Logger log = LoggerFactory.getLogger(DatabasePoolMonitor.class);

    private final DataSource masterDataSource;
    private final DataSource slaveDataSource;

    public DatabasePoolMonitor(@Qualifier("masterDataSource") DataSource masterDataSource,
                                @Qualifier("slaveDataSource") DataSource slaveDataSource) {
        this.masterDataSource = masterDataSource;
        this.slaveDataSource = slaveDataSource;
    }

    @Scheduled(fixedRate = 60000)
    public void monitor() {
        try {
            checkPool("master", masterDataSource);
            checkPool("slave", slaveDataSource);
        } catch (Exception e) {
            log.error("连接池监控失败", e);
        }
    }

    private void checkPool(String name, DataSource ds) {
        if (ds instanceof HikariDataSource) {
            HikariDataSource hikari = (HikariDataSource) ds;
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();

            int active = pool.getActiveConnections();
            int max = hikari.getMaximumPoolSize();
            int awaiting = pool.getThreadsAwaitingConnection();

            if ((double) active / max > 0.8) {
                log.warn("[{}连接池] 使用率超80%: {}/{}", name, active, max);
            }
            if (awaiting > 0) {
                log.warn("[连接池] {}个线程等待连接", name, awaiting);
            }
        }
    }

    public Map<String, Object> getPoolMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("master", collectMetrics(masterDataSource));
        metrics.put("slave", collectMetrics(slaveDataSource));
        return metrics;
    }

    private Map<String, Object> collectMetrics(DataSource ds) {
        Map<String, Object> m = new HashMap<>();
        if (ds instanceof HikariDataSource) {
            HikariDataSource hikari = (HikariDataSource) ds;
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            m.put("active", pool.getActiveConnections());
            m.put("idle", pool.getIdleConnections());
            m.put("total", pool.getTotalConnections());
            m.put("awaiting", pool.getThreadsAwaitingConnection());
            m.put("max", hikari.getMaximumPoolSize());
        }
        return m;
    }
}
