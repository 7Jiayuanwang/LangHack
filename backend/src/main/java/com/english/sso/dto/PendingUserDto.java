package com.english.sso.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 待审批用户 DTO
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Data
public class PendingUserDto {

    /**
     * 绑定ID
     */
    private Long bindingId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 推断的角色
     */
    private String inferredRole;

    /**
     * 组织单位列表
     */
    private List<String> memberOf;

    /**
     * 原始属性
     */
    private Map<String, Object> rawAttributes;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
