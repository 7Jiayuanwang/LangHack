package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("conversation_shares")
public class ConversationShare {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("share_token")
    private String shareToken;

    @TableField("access_password")
    private String accessPassword;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("created_by")
    private Long createdBy;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
