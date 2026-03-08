package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 课文习题实体类
 */
@TableName("text_exercises")
public class TextExercise {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("text_id")
    private Long textId;

    private String question;

    private String exerciseType;

    @TableField("order_num")
    private Integer orderNum;

    @TableField("created_by")
    private Long createdBy;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    // 构造函数
    public TextExercise() {}

    public TextExercise(Long textId, String question, String exerciseType, Integer orderNum, Long createdBy) {
        this.textId = textId;
        this.question = question;
        this.exerciseType = exerciseType;
        this.orderNum = orderNum;
        this.createdBy = createdBy;
    }

    // Getter和Setter方法
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

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getExerciseType() {
        return exerciseType;
    }

    public void setExerciseType(String exerciseType) {
        this.exerciseType = exerciseType;
    }

    public Integer getOrderNum() {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum) {
        this.orderNum = orderNum;
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

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    @Override
    public String toString() {
        return "TextExercise{" +
                "id=" + id +
                ", textId=" + textId +
                ", question='" + question + '\'' +
                ", exerciseType='" + exerciseType + '\'' +
                ", orderNum=" + orderNum +
                ", createdBy=" + createdBy +
                ", createdTime=" + createdTime +
                ", updatedTime=" + updatedTime +
                '}';
    }
}