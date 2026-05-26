package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生 AI 题答题明细（每题一行，挂在 attempt 下）
 * options_snapshot / analysis_snapshot 等字段在 regenerate 删原题后仍保留历史
 */
@Data
@TableName("student_ai_quiz_records")
public class StudentAiQuizRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("attempt_id")
    private Long attemptId;

    @TableField("student_id")
    private Long studentId;

    @TableField("text_id")
    private Long textId;

    @TableField("exercise_id")
    private Long exerciseId;

    @TableField("question_snapshot")
    private String questionSnapshot;

    /** JSON 字符串：[{"key":"A","content":"...","isCorrect":1},...] */
    @TableField("options_snapshot")
    private String optionsSnapshot;

    @TableField("correct_answer")
    private String correctAnswer;

    @TableField("analysis_snapshot")
    private String analysisSnapshot;

    @TableField("user_answer")
    private String userAnswer;

    @TableField("is_correct")
    private Boolean isCorrect;

    @TableField("time_spent_seconds")
    private Integer timeSpentSeconds;

    @TableField("question_order")
    private Integer questionOrder;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
