package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

@TableName("users")
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String email;

    private String role;

    private String nickname;

    @TableField("real_name")
    private String realName;

    private String avatar;

    private String status;

    @TableField("deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /**
     * 是否通过SSO绑定
     */
    @TableField("sso_bound")
    private Integer ssoBound;

    /**
     * SSO提供商
     */
    @TableField("sso_provider")
    private String ssoProvider;

    /**
     * SSO唯一标识
     */
    @TableField("sso_subject")
    private String ssoSubject;

    /**
     * 关联 school_student.id（学号匹配，由 V25 迁移引入）
     */
    @TableField("school_student_id")
    private Long schoolStudentId;

    /**
     * 关联 school_teacher.id（工号匹配，由 V25 迁移引入）
     */
    @TableField("school_teacher_id")
    private Long schoolTeacherId;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public Integer getSsoBound() {
        return ssoBound;
    }

    public void setSsoBound(Integer ssoBound) {
        this.ssoBound = ssoBound;
    }

    public String getSsoProvider() {
        return ssoProvider;
    }

    public void setSsoProvider(String ssoProvider) {
        this.ssoProvider = ssoProvider;
    }

    public String getSsoSubject() {
        return ssoSubject;
    }

    public void setSsoSubject(String ssoSubject) {
        this.ssoSubject = ssoSubject;
    }

    public Long getSchoolStudentId() {
        return schoolStudentId;
    }

    public void setSchoolStudentId(Long schoolStudentId) {
        this.schoolStudentId = schoolStudentId;
    }

    public Long getSchoolTeacherId() {
        return schoolTeacherId;
    }

    public void setSchoolTeacherId(Long schoolTeacherId) {
        this.schoolTeacherId = schoolTeacherId;
    }

    @TableField("read_article_count")
    private Integer readArticleCount = 0;

    @TableField("total_read_time")
    private Integer totalReadTime = 0;

    @TableField("read_total_times")
    private Integer readTotalTimes = 0;

    @TableField("completed_quizzes")
    private Integer completedQuizzes = 0;

    @TableField("correct_answers")
    private Integer correctAnswers = 0;

    @TableField("study_days")
    private Integer studyDays = 0;

    @TableField("last_study_time")
    private LocalDateTime lastStudyTime;

    public Integer getReadArticleCount() {
        return readArticleCount;
    }

    public void setReadArticleCount(Integer readArticleCount) {
        this.readArticleCount = readArticleCount;
    }

    public Integer getTotalReadTime() {
        return totalReadTime;
    }

    public void setTotalReadTime(Integer totalReadTime) {
        this.totalReadTime = totalReadTime;
    }

    public Integer getReadTotalTimes() {
        return readTotalTimes;
    }

    public void setReadTotalTimes(Integer readTotalTimes) {
        this.readTotalTimes = readTotalTimes;
    }

    public Integer getCompletedQuizzes() {
        return completedQuizzes;
    }

    public void setCompletedQuizzes(Integer completedQuizzes) {
        this.completedQuizzes = completedQuizzes;
    }

    public Integer getCorrectAnswers() {
        return correctAnswers;
    }

    public void setCorrectAnswers(Integer correctAnswers) {
        this.correctAnswers = correctAnswers;
    }

    public Integer getStudyDays() {
        return studyDays;
    }

    public void setStudyDays(Integer studyDays) {
        this.studyDays = studyDays;
    }

    public LocalDateTime getLastStudyTime() {
        return lastStudyTime;
    }

    public void setLastStudyTime(LocalDateTime lastStudyTime) {
        this.lastStudyTime = lastStudyTime;
    }
}
