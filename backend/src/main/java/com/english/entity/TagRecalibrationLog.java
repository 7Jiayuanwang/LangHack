package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tag_recalibration_log")
public class TagRecalibrationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tagCode;

    private String oldThreshold;

    private String newThreshold;

    private String reason;

    private Integer affectedUserCount;

    private String operator;

    private LocalDateTime createdTime;
}
