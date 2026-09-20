package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("xiaoya_external_learning")
public class XiaoyaExternalLearning {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("learning_day")
    private LocalDate learningDay;
    @TableField("source_type")
    private String sourceType;
    @TableField("source_title")
    private String sourceTitle;
    @TableField("takeaway_en")
    private String takeawayEn;
    @TableField("takeaway_hash")
    private String takeawayHash;
    @TableField("created_time")
    private LocalDateTime createdTime;
}
