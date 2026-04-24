package com.english.dto;

import java.util.ArrayList;
import java.util.List;

public class SchoolDataSyncFilterRequest {

    private String triggerType = "MANUAL";
    private List<FilterGroup> filterGroups = new ArrayList<>();

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public List<FilterGroup> getFilterGroups() {
        return filterGroups;
    }

    public void setFilterGroups(List<FilterGroup> filterGroups) {
        this.filterGroups = filterGroups;
    }

    public static class FilterGroup {
        private String relation = "AND";
        private List<FilterCondition> conditions = new ArrayList<>();

        public String getRelation() {
            return relation;
        }

        public void setRelation(String relation) {
            this.relation = relation;
        }

        public List<FilterCondition> getConditions() {
            return conditions;
        }

        public void setConditions(List<FilterCondition> conditions) {
            this.conditions = conditions;
        }
    }

    public static class FilterCondition {
        private String field;
        private String label;
        private String operator = "eq";
        private String value;

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

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
