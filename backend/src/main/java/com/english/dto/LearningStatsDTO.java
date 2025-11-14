package com.english.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LearningStatsDTO {
    private List<DailyStatDTO> dailyStats;        // 每日答题统计
    private List<ActiveStudentDTO> activeStudents; // 活跃学生排行

    @Data
    public static class DailyStatDTO {
        private String date;
        private Integer count;
    }

    @Data
    public static class ActiveStudentDTO {
        private String studentName;
        private Integer answerCount;
        private Integer accuracy;
    }
}
