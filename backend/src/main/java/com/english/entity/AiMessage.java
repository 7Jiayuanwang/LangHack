package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_messages")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    @TableField("selected_text")
    private String selectedText;

    @TableField("article_context")
    private String articleContext;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
