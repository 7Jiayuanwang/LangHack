package com.english.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DynamicDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType type = DynamicDataSourceContext.getDataSourceType();
        return type != null ? type : DataSourceType.MASTER;
    }
}
