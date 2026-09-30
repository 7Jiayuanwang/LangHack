package com.english.dto.teacher;

import lombok.Data;

import java.time.LocalDateTime;

/** Per-member aggregates for the teacher's class learning overview. */
@Data
public class TeacherClassStudentLearningRow {
    private Long studentId;
    private String username;
    private String displayName;
    private long readingSessions;
    private long textsRead;
    private LocalDateTime lastReadingAt;
    private long practiceAttempts;
    private long correctAnswers;
    private long answeredQuestions;
    private LocalDateTime lastPracticeAt;
}
