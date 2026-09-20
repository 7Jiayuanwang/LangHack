package com.english.controller;

import com.english.dto.Result;
import com.english.dto.learner.AgentStudyLoopDto;
import com.english.entity.User;
import com.english.service.UserService;
import com.english.service.impl.AgentStudyLoopService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/agent/study-loop")
@PreAuthorize("hasAnyRole('STUDENT', 'USER')")
@RequiredArgsConstructor
public class AgentStudyLoopController {

    private final AgentStudyLoopService studyLoopService;
    private final UserService userService;

    @GetMapping
    public Result<AgentStudyLoopDto> get(Authentication authentication) {
        if (authentication == null) return Result.error(401, "请先登录");
        User user = userService.getUserByUsername(authentication.getName());
        if (user == null) return Result.error(404, "用户不存在");
        return Result.success(studyLoopService.forUser(user.getId()));
    }
}
