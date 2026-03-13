package com.english.config;

import com.english.datasource.DataSourceType;
import com.english.datasource.DynamicDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Source Configuration (Master-Slave Routing)
 * 
 * Security Notes:
 * - Master: handles all write operations (INSERT, UPDATE, DELETE)
 * - Slave: handles read-only operations (SELECT) - read-only flag enforced
 * - Both credentials must be provided via environment variables in production
 * - Connection pool security:
 *   - Connection timeout: 30s (prevents long connection hangs)
 *   - Max idle time: 600s (closes idle connections to free resources)
 *   - Leak detection: 60s threshold (detects connection leaks)
 *   - SSL/TLS: Configure via DB_SSL_MODE environment variable
 */
@Configuration
public class DataSourceConfig {

    @Bean("masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean("slaveDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.slave")
    public DataSource slaveDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * Dynamic routing data source (master for writes, slave for reads)
     * 
     * Uses constructor parameter injection instead of method calls
     * to avoid Spring CGLIB proxy re-entrance issues.
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public DataSource dynamicDataSource(
            @Qualifier("masterDataSource") DataSource master,
            @Qualifier("slaveDataSource") DataSource slave) {
        DynamicDataSource ds = new DynamicDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.MASTER, master);
        targetDataSources.put(DataSourceType.SLAVE, slave);
        ds.setTargetDataSources(targetDataSources);
        ds.setDefaultTargetDataSource(master);  // Fallback to master on routing error
        return ds;
    }
}
