package com.english.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicDataSourceTest {

    @AfterEach
    void clearContext() {
        DynamicDataSourceContext.clear();
    }

    @Test
    void unconfiguredReplicaFallsBackToMaster() throws Exception {
        DataSource master = dataSourceWithUrl("jdbc:mysql://master/english_learning");
        DataSource slave = mock(DataSource.class);
        Connection slaveConnection = mock(Connection.class);
        DatabaseMetaData slaveMetaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet replicaStatus = mock(ResultSet.class);

        when(slave.getConnection()).thenReturn(slaveConnection);
        when(slaveConnection.getMetaData()).thenReturn(slaveMetaData);
        when(slaveMetaData.getURL()).thenReturn("jdbc:mysql://slave/english_learning");
        when(slaveConnection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SHOW REPLICA STATUS")).thenReturn(replicaStatus);
        when(replicaStatus.next()).thenReturn(false);

        TestDynamicDataSource routingDataSource = createRoutingDataSource(master, slave);
        routingDataSource.afterPropertiesSet();

        DynamicDataSourceContext.setDataSourceType(DataSourceType.SLAVE);
        assertEquals(DataSourceType.MASTER, routingDataSource.currentLookupKey());
    }

    private DataSource dataSourceWithUrl(String url) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getURL()).thenReturn(url);
        return dataSource;
    }

    private TestDynamicDataSource createRoutingDataSource(DataSource master, DataSource slave) {
        TestDynamicDataSource routingDataSource = new TestDynamicDataSource();
        Map<Object, Object> targets = new HashMap<>();
        targets.put(DataSourceType.MASTER, master);
        targets.put(DataSourceType.SLAVE, slave);
        routingDataSource.setTargetDataSources(targets);
        routingDataSource.setDefaultTargetDataSource(master);
        return routingDataSource;
    }

    private static class TestDynamicDataSource extends DynamicDataSource {
        Object currentLookupKey() {
            return determineCurrentLookupKey();
        }
    }
}
