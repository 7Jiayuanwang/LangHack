package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_follows")
public class UserFollow {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long followerId;

    private Long followingId;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}