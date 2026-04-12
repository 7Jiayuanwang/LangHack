package com.english.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段映射配置实体
 */
@Data
@TableName("field_mappings")
public class FieldMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("data_source")
    private String dataSource;

    @TableField("source_field")
    private String sourceField;

    @TableField("target_field")
    private String targetField;

    @TableField("source_type")
    private String sourceType;

    @TableField("target_type")
    private String targetType;

    @TableField("converter")
    private String converter;

    @TableField("converter_config")
    private String converterConfig;

    @TableField("default_value")
    private String defaultValue;

    @JsonProperty("isRequired")
    @TableField("is_required")
    private Boolean isRequired;

    @TableField("sort_order")
    private Integer sortOrder;

    @JsonProperty("isActive")
    @TableField("is_active")
    private Boolean isActive;

    @TableField("remark")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
