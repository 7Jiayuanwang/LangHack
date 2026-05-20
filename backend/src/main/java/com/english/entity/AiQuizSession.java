package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("ai_quiz_session")
public class AiQuizSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("text_id")
    private Long textId;

    @TableField("difficulty")
    private String difficulty;

    @TableField("passage_analysis")
    private String passageAnalysis;

    @TableField("quality_metrics")
    private String qualityMetrics;

    @TableField("question_count")
    private Integer questionCount;

    @TableField("ai_service")
    private String aiService;

    @TableField("ai_model")
    private String aiModel;

    @TableField("created_by")
    private Long createdBy;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    public AiQuizSession() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTextId() {
        return textId;
    }

    public void setTextId(Long textId) {
        this.textId = textId;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getPassageAnalysis() {
        return passageAnalysis;
    }

    public void setPassageAnalysis(String passageAnalysis) {
        this.passageAnalysis = passageAnalysis;
    }

    public String getQualityMetrics() {
        return qualityMetrics;
    }

    public void setQualityMetrics(String qualityMetrics) {
        this.qualityMetrics = qualityMetrics;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public String getAiService() {
        return aiService;
    }

    public void setAiService(String aiService) {
        this.aiService = aiService;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
}
