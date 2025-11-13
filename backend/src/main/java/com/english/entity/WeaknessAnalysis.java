package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("weakness_analysis")
public class WeaknessAnalysis {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("student_id")
    private Long studentId;

    @TableField("knowledge_point_id")
    private Long knowledgePointId;

    @TableField("total_questions")
    private Integer totalQuestions;

    @TableField("correct_count")
    private Integer correctCount;

    @TableField("wrong_count")
    private Integer wrongCount;

    @TableField("accuracy_rate")
    private BigDecimal accuracyRate;

    @TableField("weakness_level")
    private String weaknessLevel;

    @TableField("last_practice_time")
    private LocalDateTime lastPracticeTime;

    @TableField(value = "analysis_time", fill = FieldFill.INSERT)
    private LocalDateTime analysisTime;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(Long knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
    }

    public Integer getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(Integer totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public Integer getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(Integer correctCount) {
        this.correctCount = correctCount;
    }

    public Integer getWrongCount() {
        return wrongCount;
    }

    public void setWrongCount(Integer wrongCount) {
        this.wrongCount = wrongCount;
    }

    public BigDecimal getAccuracyRate() {
        return accuracyRate;
    }

    public void setAccuracyRate(BigDecimal accuracyRate) {
        this.accuracyRate = accuracyRate;
    }

    public String getWeaknessLevel() {
        return weaknessLevel;
    }

    public void setWeaknessLevel(String weaknessLevel) {
        this.weaknessLevel = weaknessLevel;
    }

    public LocalDateTime getLastPracticeTime() {
        return lastPracticeTime;
    }

    public void setLastPracticeTime(LocalDateTime lastPracticeTime) {
        this.lastPracticeTime = lastPracticeTime;
    }

    public LocalDateTime getAnalysisTime() {
        return analysisTime;
    }

    public void setAnalysisTime(LocalDateTime analysisTime) {
        this.analysisTime = analysisTime;
    }
}
