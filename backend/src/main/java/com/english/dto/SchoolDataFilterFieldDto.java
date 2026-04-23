package com.english.dto;

import java.util.ArrayList;
import java.util.List;

public class SchoolDataFilterFieldDto {

    private String field;
    private String label;
    private String column;
    private String type;
    private String sourceField;
    private List<String> operators = new ArrayList<>();
    private Capabilities capabilities = new Capabilities();

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public List<String> getOperators() {
        return operators;
    }

    public void setOperators(List<String> operators) {
        this.operators = operators;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Capabilities capabilities) {
        this.capabilities = capabilities;
    }

    public static class Capabilities {
        private boolean listQuery;
        private boolean localSyncFilter;
        private boolean upstreamSyncFilter;

        public boolean isListQuery() {
            return listQuery;
        }

        public void setListQuery(boolean listQuery) {
            this.listQuery = listQuery;
        }

        public boolean isLocalSyncFilter() {
            return localSyncFilter;
        }

        public void setLocalSyncFilter(boolean localSyncFilter) {
            this.localSyncFilter = localSyncFilter;
        }

        public boolean isUpstreamSyncFilter() {
            return upstreamSyncFilter;
        }

        public void setUpstreamSyncFilter(boolean upstreamSyncFilter) {
            this.upstreamSyncFilter = upstreamSyncFilter;
        }
    }
}
