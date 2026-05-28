package com.english.controller;

import com.english.dto.Result;
import com.english.dto.learner.LearnerProfileDto;
import com.english.dto.learner.LearnerTagDto;
import com.english.dto.learner.StudentFacingProfileDto;
import com.english.entity.LearnerProfileSummary;
import com.english.entity.User;
import com.english.service.LearnerProfileService;
import com.english.service.LearnerProfileSummaryService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student/profile")
@PreAuthorize("hasAnyRole('STUDENT', 'USER')")
public class StudentProfileController {

    @Autowired
    private LearnerProfileService learnerProfileService;

    @Autowired
    private UserService userService;

    @Autowired(required = false)
    private LearnerProfileSummaryService summaryService;

    @GetMapping("/me")
    public Result<StudentFacingProfileDto> getMyProfile(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        if (user == null) {
            return Result.error("用户不存在");
        }

        LearnerProfileDto fullProfile = learnerProfileService.getUserProfile(user.getId());
        if (fullProfile == null || "empty".equals(fullProfile.getProfileStatus())) {
            return Result.success(null);
        }

        StudentFacingProfileDto dto = new StudentFacingProfileDto();
        dto.setIdentity(fullProfile.getIdentity());
        dto.setAbility(fullProfile.getAbility());

        if (fullProfile.getHabit() != null) {
            dto.setActiveDays7d(fullProfile.getHabit().getActiveDays7d());
            dto.setConsecutiveDays(fullProfile.getHabit().getConsecutiveDays());
            dto.setStudyMinutes14d((int) fullProfile.getHabit().getStudyMinutes14d());
        }

        if (fullProfile.getPreference() != null) {
            dto.setPrimaryContentVersion(fullProfile.getPreference().getPrimaryContentVersion());
            dto.setTargetExamLevel(fullProfile.getPreference().getTargetExamLevel());
        }

        if (fullProfile.getTags() != null) {
            List<LearnerTagDto> positiveTags = fullProfile.getTags().stream()
                    .filter(t -> "positive".equals(t.getTagType()) || "ability".equals(t.getTagType()))
                    .collect(Collectors.toList());
            dto.setPositiveTags(positiveTags);
        } else {
            dto.setPositiveTags(Collections.emptyList());
        }

        if (summaryService != null) {
            LearnerProfileSummary summary = summaryService.getLatestSummary(user.getId(), "PERSONAL");
            if (summary != null) {
                dto.setNaturalLanguageSummary(summary.getSummaryText());
            }
        }

        return Result.success(dto);
    }

    @GetMapping("/me/summary")
    public Result<String> getMyProfileSummary(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (summaryService == null) {
            return Result.success(null);
        }

        LearnerProfileSummary summary = summaryService.getLatestSummary(user.getId(), "PERSONAL");
        return Result.success(summary != null ? summary.getSummaryText() : null);
    }
}
