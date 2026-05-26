package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生 AI 题做题尝试汇总（每次提交一组 AI 题一行）
 */
@Data
@TableName("student_ai_quiz_attempts")
public class StudentAiQuizAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("student_id")
    private Long studentId;

    @TableField("text_id")
    private Long textId;

    @TableField("session_id")
    private Long sessionId;

    @TableField("assignment_id")
    private Long assignmentId;

    @TableField("total_questions")
    private Integer totalQuestions;

    @TableField("correct_count")
    private Integer correctCount;

    @TableField("score")
    private BigDecimal score;

    @TableField("total_time_seconds")
    private Integer totalTimeSeconds;

    @TableField("submitted_time")
    private LocalDateTime submittedTime;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
