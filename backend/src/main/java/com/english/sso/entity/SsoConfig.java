package com.english.sso.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SSO 配置实体
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Data
@TableName("sso_config")
public class SsoConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 配置类型: SYSTEM, CAS, OAUTH2, ROLE_MAPPING
     */
    private String configType;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 是否加密存储
     */
    private Integer isEncrypted;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
