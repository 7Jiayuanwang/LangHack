package com.english.dto;

import java.util.List;

/**
 * 包含习题的课文数据传输对象
 */
public class TextWithExercisesDTO {
    private Long textId;
    private String title;
    private String content;
    private String difficulty;
    private String audioUrl;
    private Long unitId;
    private Integer orderNum;
    private List<ExerciseDTO> exercises;

    // 构造函数
    public TextWithExercisesDTO() {}

    public TextWithExercisesDTO(Long textId, String title, String content, String difficulty, String audioUrl, Long unitId, Integer orderNum, List<ExerciseDTO> exercises) {
        this.textId = textId;
        this.title = title;
        this.content = content;
        this.difficulty = difficulty;
        this.audioUrl = audioUrl;
        this.unitId = unitId;
        this.orderNum = orderNum;
        this.exercises = exercises;
    }

    // Getter和Setter方法
    public Long getTextId() {
        return textId;
    }

    public void setTextId(Long textId) {
        this.textId = textId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public Integer getOrderNum() {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum) {
        this.orderNum = orderNum;
    }

    public List<ExerciseDTO> getExercises() {
        return exercises;
    }

    public void setExercises(List<ExerciseDTO> exercises) {
        this.exercises = exercises;
    }

    public int getExerciseCount() {
        return exercises != null ? exercises.size() : 0;
    }
}