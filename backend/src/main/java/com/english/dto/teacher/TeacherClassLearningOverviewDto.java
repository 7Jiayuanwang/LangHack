package com.english.dto.teacher;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Evidence-based class overview; no student conversation or profile text is included. */
@Data
public class TeacherClassLearningOverviewDto {
    private Long classId;
    private String className;
    private LocalDate windowStart;
    private LocalDate windowEnd;
    private int totalStudents;
    private int activeStudents;
    private int readingStudents;
    private int quizStudents;
    private int noRecordStudents;
    private long readingSessions;
    private long practiceAttempts;
    private long answeredQuestions;
    private Integer correctRate;
    private List<WeeklyActivity> weeklyActivity;
    private List<AccuracyBand> accuracyDistribution;
    private List<StudentProgress> studentProgress;

    @Data
    public static class WeeklyActivity {
        private LocalDate weekStart;
        private long readingStudents;
        private long readingSessions;
        private long quizStudents;
        private long practiceAttempts;
        private Integer correctRate;
    }

    @Data
    public static class AccuracyBand {
        private String key;
        private String label;
        private int studentCount;

        public AccuracyBand(String key, String label, int studentCount) {
            this.key = key;
            this.label = label;
            this.studentCount = studentCount;
        }
    }

    @Data
    public static class StudentProgress {
        private Long studentId;
        private String username;
        private String displayName;
        private long readingSessions;
        private long textsRead;
        private long practiceAttempts;
        private long answeredQuestions;
        private Integer correctRate;
        private LocalDateTime lastActivityAt;
    }
}
