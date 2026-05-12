package com.english.dto;

import java.util.ArrayList;
import java.util.List;

public class SchoolDataQueryRequest {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    private int page = DEFAULT_PAGE;
    private int size = DEFAULT_SIZE;
    private List<SchoolDataSyncFilterRequest.FilterGroup> filterGroups = new ArrayList<>();

    public int getPage() {
        return page < DEFAULT_PAGE ? DEFAULT_PAGE : page;
    }

    public void setPage(int page) {
        this.page = page < DEFAULT_PAGE ? DEFAULT_PAGE : page;
    }

    public int getSize() {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    public void setSize(int size) {
        if (size < 1) {
            this.size = DEFAULT_SIZE;
            return;
        }
        this.size = Math.min(size, MAX_SIZE);
    }

    public List<SchoolDataSyncFilterRequest.FilterGroup> getFilterGroups() {
        return filterGroups;
    }

    public void setFilterGroups(List<SchoolDataSyncFilterRequest.FilterGroup> filterGroups) {
        this.filterGroups = filterGroups;
    }
}
