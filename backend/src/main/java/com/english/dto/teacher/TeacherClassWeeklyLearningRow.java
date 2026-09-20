package com.english.dto.teacher;

import lombok.Data;

import java.time.LocalDate;

/** One calendar week's recorded reading or AI reading-quiz activity. */
@Data
public class TeacherClassWeeklyLearningRow {
    private LocalDate weekStart;
    private long readingStudents;
    private long readingSessions;
    private long quizStudents;
    private long practiceAttempts;
    private long correctAnswers;
    private long answeredQuestions;
}
