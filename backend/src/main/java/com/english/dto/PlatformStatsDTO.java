package com.english.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PlatformStatsDTO {
    private Integer totalUsers;
    private Integer totalCourses;
    private Integer totalQuestions;
    private Integer totalWords;
    private Integer totalClasses;
    private Integer totalTexts;
}
