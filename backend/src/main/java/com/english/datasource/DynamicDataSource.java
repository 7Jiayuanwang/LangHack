package com.english.datasource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 动态数据源路由（主从切换 + 从库故障/复制延迟自动降级）
 *
 * 降级触发条件（任一满足即切主库）：
 *   1. 从库连接失败（SQLException）
 *   2. 从库复制线程未运行（Replica_SQL_Running != Yes）
 *   3. 从库复制延迟超过阈值（Seconds_Behind_Source > MAX_SLAVE_LAG_SECONDS）
 *
 * 恢复机制：
 *   - 降级后每 PROBE_INTERVAL_MS 探测一次从库，恢复后自动重新启用
 *   - 全程无需重启，业务无感知
 */
@Slf4j
public class DynamicDataSource extends AbstractRoutingDataSource {

    /** 从库复制延迟超过此值（秒）时降级 */
    private static final int MAX_SLAVE_LAG_SECONDS = 30;

    /** 降级后探测恢复的间隔（毫秒） */
    private static final long PROBE_INTERVAL_MS = 60_000;

    /** 从库当前是否可用 */
    private final AtomicBoolean slaveAvailable = new AtomicBoolean(true);

    /** 上次标记不可用的时间戳，用于控制探测频率 */
    private final AtomicLong slaveUnavailableSince = new AtomicLong(0);

    /** 权限不足导致无法 SHOW REPLICA STATUS 时只警告一次，避免日志刷屏 */
    private final AtomicBoolean privilegeWarnLogged = new AtomicBoolean(false);

    /** 后台探测线程 */
    private ScheduledExecutorService probeScheduler;

    // ── 路由决策 ──────────────────────────────────────────

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType type = DynamicDataSourceContext.getDataSourceType();
        if (type == null) {
            return DataSourceType.MASTER;
        }
        if (type == DataSourceType.SLAVE && !slaveAvailable.get()) {
            log.debug("[DataSource] Slave unavailable, routing to MASTER (fallback)");
            return DataSourceType.MASTER;
        }
        return type;
    }

    // ── 连接获取（兜底：连接失败时降级）─────────────────────

    @Override
    public Connection getConnection() throws SQLException {
        try {
            return super.getConnection();
        } catch (SQLException e) {
            return fallbackToMasterIfSlave(e, () -> super.getConnection());
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        try {
            return super.getConnection(username, password);
        } catch (SQLException e) {
            return fallbackToMasterIfSlave(e, () -> super.getConnection(username, password));
        }
    }

    private Connection fallbackToMasterIfSlave(SQLException e, ConnectionSupplier supplier) throws SQLException {
        if (DynamicDataSourceContext.getDataSourceType() == DataSourceType.SLAVE) {
            markSlaveUnavailable("连接失败: " + e.getMessage());
            DynamicDataSourceContext.setDataSourceType(DataSourceType.MASTER);
            return supplier.get();
        }
        throw e;
    }

    @FunctionalInterface
    private interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    // ── 启动后初始化：探测从库复制状态 ───────────────────────

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        // 启动时立即检测一次从库复制状态
        boolean replicationOk = checkSlaveReplication();
        if (!replicationOk) {
            markSlaveUnavailable("启动时复制状态检测失败，降级到主库");
        } else {
            log.info("[DataSource] Slave replication healthy at startup, slave routing enabled");
        }
        // 启动后台定时探测（降级后恢复 + 运行时持续监控）
        startProbeScheduler();
    }

    private void startProbeScheduler() {
        probeScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "datasource-slave-probe");
            t.setDaemon(true);
            return t;
        });
        probeScheduler.scheduleWithFixedDelay(() -> {
            try {
                boolean ok = checkSlaveReplication();
                if (ok && !slaveAvailable.get()) {
                    slaveAvailable.set(true);
                    slaveUnavailableSince.set(0);
                    log.info("[DataSource] Slave recovered, re-enabling slave routing");
                } else if (!ok && slaveAvailable.get()) {
                    markSlaveUnavailable("定时探测：复制状态异常");
                }
            } catch (Exception e) {
                log.debug("[DataSource] Slave probe error: {}", e.getMessage());
            }
        }, PROBE_INTERVAL_MS, PROBE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // ── 从库复制状态检测 ──────────────────────────────────

    /**
     * 检测从库复制是否健康：
     *   - 能建立连接
     *   - Replica_SQL_Running = Yes（复制线程运行中）
     *   - Seconds_Behind_Source <= MAX_SLAVE_LAG_SECONDS（延迟可接受）
     *
     * 若从库 URL 与主库相同（单机部署），跳过复制检测直接返回 true。
     */
    private boolean checkSlaveReplication() {
        DataSource slaveDs = resolveSlaveDataSource();
        if (slaveDs == null) {
            log.warn("[DataSource] Slave datasource not found in routing map");
            return false;
        }

        try (Connection conn = slaveDs.getConnection()) {
            // 单机部署：从库 URL 与主库相同，无复制，直接可用
            String slaveUrl = conn.getMetaData().getURL();
            DataSource masterDs = resolveMasterDataSource();
            if (masterDs != null) {
                try (Connection mc = masterDs.getConnection()) {
                    if (slaveUrl.equals(mc.getMetaData().getURL())) {
                        log.debug("[DataSource] Slave URL == Master URL (single-instance), skipping replication check");
                        return true;
                    }
                } catch (SQLException ignored) {}
            }

            // 检查复制状态
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW REPLICA STATUS")) {
                if (!rs.next()) {
                    // SHOW REPLICA STATUS 返回空 = 复制未配置
                    log.warn("[DataSource] Slave replication not configured (SHOW REPLICA STATUS empty). " +
                             "Slave routing disabled until replication is set up.");
                    return false;
                }
                String sqlRunning = rs.getString("Replica_SQL_Running");
                String ioRunning  = rs.getString("Replica_IO_Running");
                Object lagObj     = rs.getObject("Seconds_Behind_Source");

                if (!"Yes".equals(sqlRunning) || !"Yes".equals(ioRunning)) {
                    log.warn("[DataSource] Slave replication threads not running: IO={}, SQL={}. Falling back to MASTER.",
                             ioRunning, sqlRunning);
                    return false;
                }
                if (lagObj != null) {
                    int lag = ((Number) lagObj).intValue();
                    if (lag > MAX_SLAVE_LAG_SECONDS) {
                        log.warn("[DataSource] Slave lag {}s exceeds threshold {}s. Falling back to MASTER.",
                                 lag, MAX_SLAVE_LAG_SECONDS);
                        return false;
                    }
                }
                log.debug("[DataSource] Slave replication OK: IO={}, SQL={}, lag={}s",
                          ioRunning, sqlRunning, lagObj);
                return true;
            } catch (SQLException e) {
                if (isPrivilegeError(e)) {
                    return handlePrivilegeFallback(conn, e);
                }
                // MySQL 8.0 旧版本用 SHOW SLAVE STATUS
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW SLAVE STATUS")) {
                    if (!rs.next()) {
                        log.warn("[DataSource] Slave replication not configured. Slave routing disabled.");
                        return false;
                    }
                    String sqlRunning = rs.getString("Slave_SQL_Running");
                    String ioRunning  = rs.getString("Slave_IO_Running");
                    if (!"Yes".equals(sqlRunning) || !"Yes".equals(ioRunning)) {
                        log.warn("[DataSource] Slave threads not running: IO={}, SQL={}. Falling back to MASTER.",
                                 ioRunning, sqlRunning);
                        return false;
                    }
                    return true;
                } catch (SQLException e2) {
                    if (isPrivilegeError(e2)) {
                        return handlePrivilegeFallback(conn, e2);
                    }
                    throw e2;
                }
            }
        } catch (SQLException e) {
            log.warn("[DataSource] Cannot connect to slave: {}. Falling back to MASTER.", e.getMessage());
            return false;
        }
    }

    /**
     * 判断是否为权限不足类异常（MySQL error 1227 / 1142 / SQLState 42000 + access denied）。
     * 这类异常不代表从库不可用，只代表当前账号无 REPLICATION CLIENT 权限。
     */
    private boolean isPrivilegeError(SQLException e) {
        int code = e.getErrorCode();
        if (code == 1227 || code == 1142 || code == 1045) {
            return true;
        }
        String msg = e.getMessage();
        return msg != null && (msg.contains("REPLICATION CLIENT")
                || msg.contains("Access denied")
                || msg.contains("command denied"));
    }

    /**
     * 权限不足时的降级策略：执行一次 SELECT 1 确认从库可连接；
     * 成功则视为可用（失去复制延迟监控，但读写分离仍生效）。
     */
    private boolean handlePrivilegeFallback(Connection conn, SQLException origin) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            if (rs.next() && privilegeWarnLogged.compareAndSet(false, true)) {
                log.warn("[DataSource] Slave account lacks REPLICATION CLIENT privilege ({}). " +
                         "Skipping replication lag check; routing reads to slave based on connectivity only. " +
                         "Grant 'REPLICATION CLIENT ON *.*' to enable full health monitoring.",
                         origin.getMessage());
            }
            return true;
        } catch (SQLException probe) {
            log.warn("[DataSource] Slave connectivity probe failed after privilege error: {}. Falling back to MASTER.",
                     probe.getMessage());
            return false;
        }
    }

    private DataSource resolveSlaveDataSource() {
        try {
            java.util.Map<Object, DataSource> resolved = getResolvedDataSources();
            return resolved != null ? resolved.get(DataSourceType.SLAVE) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private DataSource resolveMasterDataSource() {
        try {
            java.util.Map<Object, DataSource> resolved = getResolvedDataSources();
            return resolved != null ? resolved.get(DataSourceType.MASTER) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── 降级标记 ──────────────────────────────────────────

    private void markSlaveUnavailable(String reason) {
        if (slaveAvailable.compareAndSet(true, false)) {
            slaveUnavailableSince.set(System.currentTimeMillis());
            log.warn("[DataSource] Slave marked UNAVAILABLE. Reason: {}. All reads routed to MASTER.", reason);
            log.warn("[DataSource] Will probe slave recovery every {}s", PROBE_INTERVAL_MS / 1000);
        }
    }
}
