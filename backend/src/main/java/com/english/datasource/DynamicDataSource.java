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
 * 鍔ㄦ€佹暟鎹簮璺敱锛堜富浠庡垏鎹?+ 浠庡簱鏁呴殰/澶嶅埗寤惰繜鑷姩闄嶇骇锛? *
 * 闄嶇骇瑙﹀彂鏉′欢锛堜换涓€婊¤冻鍗冲垏涓诲簱锛夛細
 *   1. 浠庡簱杩炴帴澶辫触锛圫QLException锛? *   2. 浠庡簱澶嶅埗绾跨▼鏈繍琛岋紙Replica_SQL_Running != Yes锛? *   3. 浠庡簱澶嶅埗寤惰繜瓒呰繃闃堝€硷紙Seconds_Behind_Source > MAX_SLAVE_LAG_SECONDS锛? *
 * 鎭㈠鏈哄埗锛? *   - 闄嶇骇鍚庢瘡 PROBE_INTERVAL_MS 鎺㈡祴涓€娆′粠搴擄紝鎭㈠鍚庤嚜鍔ㄩ噸鏂板惎鐢? *   - 鍏ㄧ▼鏃犻渶閲嶅惎锛屼笟鍔℃棤鎰熺煡
 */
@Slf4j
public class DynamicDataSource extends AbstractRoutingDataSource {

    /** 浠庡簱澶嶅埗寤惰繜瓒呰繃姝ゅ€硷紙绉掞級鏃堕檷绾?*/
    private static final int MAX_SLAVE_LAG_SECONDS = 30;

    /** 闄嶇骇鍚庢帰娴嬫仮澶嶇殑闂撮殧锛堟绉掞級 */
    private static final long PROBE_INTERVAL_MS = 60_000;

    /** 浠庡簱褰撳墠鏄惁鍙敤 */
    private final AtomicBoolean slaveAvailable = new AtomicBoolean(true);

    /** 涓婃鏍囪涓嶅彲鐢ㄧ殑鏃堕棿鎴筹紝鐢ㄤ簬鎺у埗鎺㈡祴棰戠巼 */
    private final AtomicLong slaveUnavailableSince = new AtomicLong(0);

    /** 鏉冮檺涓嶈冻瀵艰嚧鏃犳硶 SHOW REPLICA STATUS 鏃跺彧璀﹀憡涓€娆★紝閬垮厤鏃ュ織鍒峰睆 */
    private final AtomicBoolean privilegeWarnLogged = new AtomicBoolean(false);

    /** 鍚庡彴鎺㈡祴绾跨▼ */
    private ScheduledExecutorService probeScheduler;

    // 鈹€鈹€ 璺敱鍐崇瓥 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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

    // 鈹€鈹€ 杩炴帴鑾峰彇锛堝厹搴曪細杩炴帴澶辫触鏃堕檷绾э級鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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

    // 鈹€鈹€ 鍚姩鍚庡垵濮嬪寲锛氭帰娴嬩粠搴撳鍒剁姸鎬?鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
// 启动时立即检测一次从库复制状态
        boolean replicationOk = checkSlaveReplication();
        if (!replicationOk) {
            markSlaveUnavailable("鍚姩鏃跺鍒剁姸鎬佹娴嬪け璐ワ紝闄嶇骇鍒颁富搴?)");
        } else {
            log.info("[DataSource] Slave replication healthy at startup, slave routing enabled");
        }
        // 鍚姩鍚庡彴瀹氭椂鎺㈡祴锛堥檷绾у悗鎭㈠ + 杩愯鏃舵寔缁洃鎺э級
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
                    markSlaveUnavailable("瀹氭椂鎺㈡祴锛氬鍒剁姸鎬佸紓甯?)");
                }
            } catch (Exception e) {
                log.debug("[DataSource] Slave probe error: {}", e.getMessage());
            }
        }, PROBE_INTERVAL_MS, PROBE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // 鈹€鈹€ 浠庡簱澶嶅埗鐘舵€佹娴?鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    /**
     * 妫€娴嬩粠搴撳鍒舵槸鍚﹀仴搴凤細
     *   - 鑳藉缓绔嬭繛鎺?     *   - Replica_SQL_Running = Yes锛堝鍒剁嚎绋嬭繍琛屼腑锛?     *   - Seconds_Behind_Source <= MAX_SLAVE_LAG_SECONDS锛堝欢杩熷彲鎺ュ彈锛?     *
     * 鑻ヤ粠搴?URL 涓庝富搴撶浉鍚岋紙鍗曟満閮ㄧ讲锛夛紝璺宠繃澶嶅埗妫€娴嬬洿鎺ヨ繑鍥?true銆?     */
    private boolean checkSlaveReplication() {
        DataSource slaveDs = resolveSlaveDataSource();
        if (slaveDs == null) {
            log.warn("[DataSource] Slave datasource not found in routing map");
            return false;
        }

        try (Connection conn = slaveDs.getConnection()) {
            // 鍗曟満閮ㄧ讲锛氫粠搴?URL 涓庝富搴撶浉鍚岋紝鏃犲鍒讹紝鐩存帴鍙敤
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

            // 妫€鏌ュ鍒剁姸鎬
            try (Statement stmt = conn.createStatement();                 ResultSet rs = stmt.executeQuery("SHOW REPLICA STATUS")) {
                if (!rs.next()) {
                    // SHOW REPLICA STATUS 杩斿洖绌?= 澶嶅埗鏈厤缃
                    log.warn("[DataSource] Slave replication not configured (SHOW REPLICA STATUS empty). " +                             "Slave routing disabled until replication is set up.");
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
                // MySQL 8.0 鏃х増鏈敤 SHOW SLAVE STATUS
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
     * 鍒ゆ柇鏄惁涓烘潈闄愪笉瓒崇被寮傚父锛圡ySQL error 1227 / 1142 / SQLState 42000 + access denied锛夈€?     * 杩欑被寮傚父涓嶄唬琛ㄤ粠搴撲笉鍙敤锛屽彧浠ｈ〃褰撳墠璐﹀彿鏃?REPLICATION CLIENT 鏉冮檺銆?     */
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
     * 鏉冮檺涓嶈冻鏃剁殑闄嶇骇绛栫暐锛氭墽琛屼竴娆?SELECT 1 纭浠庡簱鍙繛鎺ワ紱
     * 鎴愬姛鍒欒涓哄彲鐢紙澶卞幓澶嶅埗寤惰繜鐩戞帶锛屼絾璇诲啓鍒嗙浠嶇敓鏁堬級銆?     */
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

    // 鈹€鈹€ 闄嶇骇鏍囪 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private void markSlaveUnavailable(String reason) {
        if (slaveAvailable.compareAndSet(true, false)) {
            slaveUnavailableSince.set(System.currentTimeMillis());
            log.warn("[DataSource] Slave marked UNAVAILABLE. Reason: {}. All reads routed to MASTER.", reason);
            log.warn("[DataSource] Will probe slave recovery every {}s", PROBE_INTERVAL_MS / 1000);
        }
    }
}
