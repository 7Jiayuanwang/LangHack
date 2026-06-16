package com.english.sso.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SSO 用户绑定实体
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Data
@TableName("sso_user_bindings")
public class SsoUserBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 本地用户ID
     */
    private Long userId;

    /**
     * SSO提供商: CAS, OAUTH2
     */
    private String ssoProvider;

    /**
     * SSO用户唯一标识
     */
    private String ssoUserId;

    /**
     * SSO用户名
     */
    private String ssoUsername;

    /**
     * SSO邮箱
     */
    private String ssoEmail;

    /**
     * SSO真实姓名
     */
    private String ssoRealName;

    /**
     * SSO组织单位 (JSON数组)
     */
    private String ssoMemberOf;

    /**
     * 管理员备注：记录特殊情况（如用户被删除后重新登录）
     */
    private String adminRemark;

    /**
     * 管理员角色锁定: 1=管理员手动审批指定角色，SSO/学校目录不得覆盖
     */
    private Integer adminRoleLocked;

    /**
     * 是否自动审批: 0=待审批, 1=已审批
     */
    private Integer autoApproved;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 逻辑删除: 0=正常, 1=已删除
     */
    @TableLogic
    private Integer deleted;
}
