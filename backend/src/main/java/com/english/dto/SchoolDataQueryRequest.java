package com.english.dto;

import java.util.ArrayList;
import java.util.List;

public class SchoolDataQueryRequest {

    private int page = 1;
    private int size = 20;
    private List<SchoolDataSyncFilterRequest.FilterGroup> filterGroups = new ArrayList<>();

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<SchoolDataSyncFilterRequest.FilterGroup> getFilterGroups() {
        return filterGroups;
    }

    public void setFilterGroups(List<SchoolDataSyncFilterRequest.FilterGroup> filterGroups) {
        this.filterGroups = filterGroups;
    }
}
