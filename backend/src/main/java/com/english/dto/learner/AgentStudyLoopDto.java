package com.english.dto.learner;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Evidence from existing article quiz attempts; no inferred ability claims. */
@Data
public class AgentStudyLoopDto {
    private int attemptCount;
    private int practicedArticleCount;
    private int answeredQuestionCount;
    private int correctQuestionCount;
    private int recentSevenDayAttempts;
    private int dueReviewCount;
    /** True when achievement and mistake summaries only cover the latest 200 attempts. */
    private boolean sampleLimited;
    private List<ReviewTask> dueReviews = new ArrayList<>();
    private List<WrongQuestion> recentWrongQuestions = new ArrayList<>();

    @Data
    public static class ReviewTask {
        private Long textId;
        private String title;
        private LocalDateTime dueAt;
        private int latestCorrect;
        private int latestTotal;
        private String reason;
    }

    @Data
    public static class WrongQuestion {
        private Long textId;
        private String title;
        private String question;
        private String userAnswer;
        private String correctAnswer;
        private String analysis;
    }
}
