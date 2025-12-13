package com.english.dto;

import java.time.LocalDateTime;
import java.util.List;

public class PracticeSessionDTO {

    /**
     * Request DTO for starting a practice session
     */
    public static class StartSessionRequest {
        private Long studentId;
        private String sessionName;
        private String questionType;
        private String difficulty;
        private Integer questionCount;
        private Long knowledgePointId;
        private String examType;

        // Getters and Setters
        public Long getStudentId() {
            return studentId;
        }

        public void setStudentId(Long studentId) {
            this.studentId = studentId;
        }

        public String getSessionName() {
            return sessionName;
        }

        public void setSessionName(String sessionName) {
            this.sessionName = sessionName;
        }

        public String getQuestionType() {
            return questionType;
        }

        public void setQuestionType(String questionType) {
            this.questionType = questionType;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty;
        }

        public Integer getQuestionCount() {
            return questionCount;
        }

        public void setQuestionCount(Integer questionCount) {
            this.questionCount = questionCount;
        }

        public Long getKnowledgePointId() {
            return knowledgePointId;
        }

        public void setKnowledgePointId(Long knowledgePointId) {
            this.knowledgePointId = knowledgePointId;
        }

        public String getExamType() {
            return examType;
        }

        public void setExamType(String examType) {
            this.examType = examType;
        }
    }

    /**
     * Response DTO for practice session
     */
    public static class SessionResponse {
        private Long sessionId;
        private List<Long> questionIds;
        private LocalDateTime startTime;
        private String message;

        public SessionResponse() {
        }

        public SessionResponse(Long sessionId, List<Long> questionIds, LocalDateTime startTime, String message) {
            this.sessionId = sessionId;
            this.questionIds = questionIds;
            this.startTime = startTime;
            this.message = message;
        }

        // Getters and Setters
        public Long getSessionId() {
            return sessionId;
        }

        public void setSessionId(Long sessionId) {
            this.sessionId = sessionId;
        }

        public List<Long> getQuestionIds() {
            return questionIds;
        }

        public void setQuestionIds(List<Long> questionIds) {
            this.questionIds = questionIds;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalDateTime startTime) {
            this.startTime = startTime;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * Request DTO for completing a session
     */
    public static class CompleteSessionRequest {
        private Integer totalTimeSpent;
        private List<Long> answeredQuestionIds;

        // Getters and Setters
        public Integer getTotalTimeSpent() {
            return totalTimeSpent;
        }

        public void setTotalTimeSpent(Integer totalTimeSpent) {
            this.totalTimeSpent = totalTimeSpent;
        }

        public List<Long> getAnsweredQuestionIds() {
            return answeredQuestionIds;
        }

        public void setAnsweredQuestionIds(List<Long> answeredQuestionIds) {
            this.answeredQuestionIds = answeredQuestionIds;
        }
    }

    /**
     * DTO for question with additional practice info
     */
    public static class QuestionDetailDTO {
        private Long id;
        private String title;
        private String questionType;
        private String difficulty;
        private String examType;
        private String examLevel;
        private String options;
        private Integer score;
        private String knowledgePointIds;
        private Boolean hasAnswered;
        private String studentAnswer;
        private Boolean isCorrect;

        // Getters and Setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getQuestionType() {
            return questionType;
        }

        public void setQuestionType(String questionType) {
            this.questionType = questionType;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty;
        }

        public String getExamType() {
            return examType;
        }

        public void setExamType(String examType) {
            this.examType = examType;
        }

        public String getExamLevel() {
            return examLevel;
        }

        public void setExamLevel(String examLevel) {
            this.examLevel = examLevel;
        }

        public String getOptions() {
            return options;
        }

        public void setOptions(String options) {
            this.options = options;
        }

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }

        public String getKnowledgePointIds() {
            return knowledgePointIds;
        }

        public void setKnowledgePointIds(String knowledgePointIds) {
            this.knowledgePointIds = knowledgePointIds;
        }

        public Boolean getHasAnswered() {
            return hasAnswered;
        }

        public void setHasAnswered(Boolean hasAnswered) {
            this.hasAnswered = hasAnswered;
        }

        public String getStudentAnswer() {
            return studentAnswer;
        }

        public void setStudentAnswer(String studentAnswer) {
            this.studentAnswer = studentAnswer;
        }

        public Boolean getIsCorrect() {
            return isCorrect;
        }

        public void setIsCorrect(Boolean isCorrect) {
            this.isCorrect = isCorrect;
        }
    }

    /**
     * DTO for question analysis
     */
    public static class QuestionAnalysisDTO {
        private Long questionId;
        private String correctAnswer;
        private String explanation;
        private String knowledgePointIds;
        private Integer totalAttempts;
        private Integer correctAttempts;
        private Double accuracyRate;

        // Getters and Setters
        public Long getQuestionId() {
            return questionId;
        }

        public void setQuestionId(Long questionId) {
            this.questionId = questionId;
        }

        public String getCorrectAnswer() {
            return correctAnswer;
        }

        public void setCorrectAnswer(String correctAnswer) {
            this.correctAnswer = correctAnswer;
        }

        public String getExplanation() {
            return explanation;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }

        public String getKnowledgePointIds() {
            return knowledgePointIds;
        }

        public void setKnowledgePointIds(String knowledgePointIds) {
            this.knowledgePointIds = knowledgePointIds;
        }

        public Integer getTotalAttempts() {
            return totalAttempts;
        }

        public void setTotalAttempts(Integer totalAttempts) {
            this.totalAttempts = totalAttempts;
        }

        public Integer getCorrectAttempts() {
            return correctAttempts;
        }

        public void setCorrectAttempts(Integer correctAttempts) {
            this.correctAttempts = correctAttempts;
        }

        public Double getAccuracyRate() {
            return accuracyRate;
        }

        public void setAccuracyRate(Double accuracyRate) {
            this.accuracyRate = accuracyRate;
        }
    }
}
