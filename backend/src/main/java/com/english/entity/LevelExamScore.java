package com.english.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 等级考试成绩实体
 */
@Data
@TableName("level_exam_scores")
public class LevelExamScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("student_id")
    private String studentId;

    @TableField("student_name")
    private String studentName;

    @TableField("exam_type")
    private String examType;

    @TableField("exam_name")
    private String examName;

    @TableField("admission_ticket_no")
    private String admissionTicketNo;

    @TableField("exam_date")
    private LocalDate examDate;

    @TableField("score")
    private BigDecimal score;

    @TableField("grade")
    private String grade;

    @JsonProperty("isPassed")
    @TableField("is_passed")
    private Boolean isPassed;

    @TableField("passing_score")
    private BigDecimal passingScore;

    @TableField("listening_score")
    private BigDecimal listeningScore;

    @TableField("reading_score")
    private BigDecimal readingScore;

    @TableField("writing_score")
    private BigDecimal writingScore;

    @TableField("translation_score")
    private BigDecimal translationScore;

    @TableField("certificate_no")
    private String certificateNo;

    @TableField("certificate_date")
    private LocalDate certificateDate;

    @TableField("exam_location")
    private String examLocation;

    @TableField("exam_room_no")
    private String examRoomNo;

    @TableField("seat_no")
    private String seatNo;

    @TableField("registration_date")
    private LocalDate registrationDate;

    @TableField("score_release_date")
    private LocalDate scoreReleaseDate;

    @TableField("data_source")
    private String dataSource;

    @TableField("remark")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("sync_time")
    private LocalDateTime syncTime;

    @TableField("sync_source")
    private String syncSource;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
