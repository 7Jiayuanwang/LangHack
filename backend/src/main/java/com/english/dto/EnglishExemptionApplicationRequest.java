package com.english.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class EnglishExemptionApplicationRequest {

    @NotBlank(message = "申请说明不能为空")
    @Size(max = 500, message = "申请说明不能超过500字")
    private String applicationReason;
}
