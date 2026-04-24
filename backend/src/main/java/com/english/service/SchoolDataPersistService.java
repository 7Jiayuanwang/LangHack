package com.english.service;

import com.english.entity.LevelExamScore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 学校数据本地持久化服务，仅负责映射与写库。
 */
public interface SchoolDataPersistService {

    PersistResult persistRawData(String dataSource, List<Map<String, Object>> rawData, String syncSource);

    void persistMappedRecord(String dataSource, Map<String, Object> mappedData);

    void persistLevelExamScores(List<Map<String, Object>> rawData, String syncSource, String studentId, LocalDateTime syncTime);

    List<LevelExamScore> queryLevelExamScores(String studentId);

    class PersistResult {
        private final int total;
        private final int success;
        private final int fail;
        private final int skip;

        public PersistResult(int total, int success, int fail, int skip) {
            this.total = total;
            this.success = success;
            this.fail = fail;
            this.skip = skip;
        }

        public int getTotal() {
            return total;
        }

        public int getSuccess() {
            return success;
        }

        public int getFail() {
            return fail;
        }

        public int getSkip() {
            return skip;
        }
    }
}
