package com.english.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 习题数据传输对象
 */
public class ExerciseDTO {
    private Long id;

    @NotBlank(message = "问题不能为空")
    private String question;

    @NotBlank(message = "习题类型不能为空")
    private String exerciseType;

    @NotNull(message = "排序号不能为空")
    private Integer orderNum;

    @NotNull(message = "选项列表不能为空")
    private List<ExerciseOptionDTO> options;

    // 构造函数
    public ExerciseDTO() {}

    public ExerciseDTO(String question, String exerciseType, Integer orderNum, List<ExerciseOptionDTO> options) {
        this.question = question;
        this.exerciseType = exerciseType;
        this.orderNum = orderNum;
        this.options = options;
    }

    // Getter和Setter方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public List<ExerciseOptionDTO> getOptions() {
        return options;
    }

    public void setOptions(List<ExerciseOptionDTO> options) {
        this.options = options;
    }
}