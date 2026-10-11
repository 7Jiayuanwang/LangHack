package com.english.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;

@Data
public class EnglishExemptionApplicationRequest {

    @NotBlank(message = "申请课程不能为空")
    @Size(max = 100, message = "申请课程不能超过100字")
    private String courseName;

    @NotBlank(message = "申请方式不能为空")
    @Pattern(regexp = "EXEMPT_FULL|EXEMPT_WITH_EXAM", message = "申请方式不正确")
    private String applyType;

    @NotBlank(message = "考试类型不能为空")
    @Pattern(regexp = "CET4|CET6|IELTS|TOEFL", message = "考试类型不正确")
    private String examType;

    @NotBlank(message = "考试成绩不能为空")
    @Size(max = 20, message = "考试成绩不能超过20字")
    private String examScore;

    private LocalDate examDate;

    @NotBlank(message = "请上传成绩证明材料")
    @Size(max = 500, message = "证明材料地址不能超过500字")
    private String certificateUrl;

    @Size(max = 500, message = "申请说明不能超过500字")
    private String studentRemark;
}
