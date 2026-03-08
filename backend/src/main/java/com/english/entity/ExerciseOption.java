package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 习题选项实体类
 */
@TableName("exercise_options")
public class ExerciseOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("exercise_id")
    private Long exerciseId;

    @TableField("option_content")
    private String optionContent;

    @TableField("option_key")
    private String optionKey;

    @TableField("is_correct")
    private Boolean isCorrect;

    @TableField("order_num")
    private Integer orderNum;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    // 构造函数
    public ExerciseOption() {}

    public ExerciseOption(Long exerciseId, String optionContent, String optionKey, Boolean isCorrect, Integer orderNum) {
        this.exerciseId = exerciseId;
        this.optionContent = optionContent;
        this.optionKey = optionKey;
        this.isCorrect = isCorrect;
        this.orderNum = orderNum;
    }

    // Getter和Setter方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public String getOptionContent() {
        return optionContent;
    }

    public void setOptionContent(String optionContent) {
        this.optionContent = optionContent;
    }

    public String getOptionKey() {
        return optionKey;
    }

    public void setOptionKey(String optionKey) {
        this.optionKey = optionKey;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public Integer getOrderNum() {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum) {
        this.orderNum = orderNum;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    @Override
    public String toString() {
        return "ExerciseOption{" +
                "id=" + id +
                ", exerciseId=" + exerciseId +
                ", optionContent='" + optionContent + '\'' +
                ", optionKey='" + optionKey + '\'' +
                ", isCorrect=" + isCorrect +
                ", orderNum=" + orderNum +
                ", createdTime=" + createdTime +
                '}';
    }
}