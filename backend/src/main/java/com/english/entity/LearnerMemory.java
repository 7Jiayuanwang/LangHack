package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("learner_memories")
public class LearnerMemory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String memoryKey;
    private String memoryType;
    private String content;
    private BigDecimal confidence;
    private String source;
    private String evidenceType;
    private String evidenceId;
    private Boolean sensitive;
    private String status;
    private LocalDateTime expiresTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
