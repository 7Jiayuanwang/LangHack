package com.english.datasource;

public class DynamicDataSourceContext {
    private static final ThreadLocal<DataSourceType> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FORCE_MASTER = new ThreadLocal<>();

    public static void setDataSourceType(DataSourceType type) {
        CONTEXT.set(type);
    }

    public static DataSourceType getDataSourceType() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /** Set by AdminMasterInterceptor for /api/admin/** and /api/ops/** paths */
    public static void setForceMaster(boolean force) {
        FORCE_MASTER.set(force);
    }

    public static boolean isForceMaster() {
        return Boolean.TRUE.equals(FORCE_MASTER.get());
    }

    public static void clearForceMaster() {
        FORCE_MASTER.remove();
    }
}
