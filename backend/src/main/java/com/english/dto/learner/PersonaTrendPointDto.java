package com.english.dto.learner;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PersonaTrendPointDto {
    private LocalDate date;
    private int totalUsers;
    private int riskHigh;
    private int riskMedium;
    private int riskLow;
    private double avgActiveDays7d;
    private int avgStudyMinutes14d;
    private double avgOverdueReviewRatio;
}
