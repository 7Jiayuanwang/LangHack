package com.english.controller;

import com.english.dto.EnglishExemptionApplicationRequest;
import com.english.dto.Result;
import com.english.entity.EnglishExemptionApplication;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.EnglishExemptionApplicationService;
import com.english.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/student/english-exemptions")
@PreAuthorize("hasAnyRole('STUDENT', 'USER')")
public class EnglishExemptionApplicationController {

    private final EnglishExemptionApplicationService applicationService;
    private final UserService userService;

    public EnglishExemptionApplicationController(
            EnglishExemptionApplicationService applicationService,
            UserService userService) {
        this.applicationService = applicationService;
        this.userService = userService;
    }

    @GetMapping("/me")
    public Result<EnglishExemptionApplication> getMyApplication(Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(applicationService.getLatestForStudent(user.getId()));
    }

    @PostMapping
    public Result<EnglishExemptionApplication> submit(
            @Valid @RequestBody EnglishExemptionApplicationRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        try {
            return Result.success("申请已提交，任课教师将收到审核通知", applicationService.submit(user, request));
        } catch (BusinessException exception) {
            return Result.error(400, exception.getMessage());
        }
    }

    private User currentUser(Authentication authentication) {
        return authentication == null ? null : userService.getUserByUsername(authentication.getName());
    }
}
