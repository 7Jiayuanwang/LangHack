package com.english.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SchoolDataFetchResult {
    private final List<Map<String, Object>> rows;
    private final PaginationSummary paginationSummary;

    public SchoolDataFetchResult(List<Map<String, Object>> rows, PaginationSummary paginationSummary) {
        this.rows = rows == null ? Collections.emptyList() : rows;
        this.paginationSummary = paginationSummary == null ? PaginationSummary.empty() : paginationSummary;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public PaginationSummary getPaginationSummary() {
        return paginationSummary;
    }

    public static class PaginationSummary {
        private final boolean complete;
        private final String stopReason;
        private final int pagesFetched;
        private final int lastSkip;
        private final int pageSize;
        private final boolean guardHit;
        private final int fetchedRowCount;

        public PaginationSummary(boolean complete,
                          String stopReason,
                          int pagesFetched,
                          int lastSkip,
                          int pageSize,
                          boolean guardHit,
                          int fetchedRowCount) {
            this.complete = complete;
            this.stopReason = stopReason;
            this.pagesFetched = pagesFetched;
            this.lastSkip = lastSkip;
            this.pageSize = pageSize;
            this.guardHit = guardHit;
            this.fetchedRowCount = fetchedRowCount;
        }

        public static PaginationSummary empty() {
            return new PaginationSummary(true, "EMPTY_FIRST_PAGE", 0, 0, 1000, false, 0);
        }

        public boolean isComplete() {
            return complete;
        }

        public String getStopReason() {
            return stopReason;
        }

        public int getPagesFetched() {
            return pagesFetched;
        }

        public int getLastSkip() {
            return lastSkip;
        }

        public int getPageSize() {
            return pageSize;
        }

        public boolean isGuardHit() {
            return guardHit;
        }

        public int getFetchedRowCount() {
            return fetchedRowCount;
        }
    }
}
