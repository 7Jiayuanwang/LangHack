package com.english.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 习题选项数据传输对象
 */
public class ExerciseOptionDTO {
    private Long id;
    @NotBlank(message = "选项内容不能为空")
    private String optionContent;
    private String optionKey;
    @NotNull(message = "是否正确不能为空")
    private Boolean isCorrect;
    private Integer orderNum;

    // 构造函数
    public ExerciseOptionDTO() {}

    public ExerciseOptionDTO(String optionContent, String optionKey, Boolean isCorrect, Integer orderNum) {
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
}