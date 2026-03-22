package com.english.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 提权请求 DTO
 */
public class ElevateRequest {

    @NotBlank(message = "密码不能为空")
    @Size(max = 100, message = "密码长度不能超过100位")
    private String password;

    @NotBlank(message = "目标角色不能为空")
    @Size(max = 20, message = "角色名称过长")
    private String targetRole;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }
}
