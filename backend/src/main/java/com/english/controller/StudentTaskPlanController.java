package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.TaskPlanService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/task-plans")
@PreAuthorize("hasAnyRole('STUDENT','USER')")
public class StudentTaskPlanController {

    @Autowired
    private TaskPlanService taskPlanService;
    @Autowired
    private UserService userService;

    @GetMapping
    public Result<?> list(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        if (user == null) return Result.error("用户不存在");
        return taskPlanService.getStudentPlans(user.getId());
    }
}
