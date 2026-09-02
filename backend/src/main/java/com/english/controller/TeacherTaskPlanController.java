package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.TaskPlanService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/teacher/task-plans")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherTaskPlanController {

    @Autowired
    private TaskPlanService taskPlanService;
    @Autowired
    private UserService userService;

    @GetMapping
    public Result<?> list(Authentication auth) {
        return taskPlanService.listTeacherPlans(getUserId(auth));
    }

    @PutMapping("/{planId}/classes/{classId}")
    public Result<?> updateOverride(@PathVariable Long planId, @PathVariable Long classId,
                                    @RequestBody Map<String, Object> params, Authentication auth) {
        return taskPlanService.updateClassOverride(planId, classId, params, getUserId(auth));
    }

    @PostMapping("/{planId}/classes/{classId}/opt-out")
    public Result<?> optOut(@PathVariable Long planId, @PathVariable Long classId, Authentication auth) {
        return taskPlanService.optOutClass(planId, classId, getUserId(auth));
    }

    @PostMapping("/{planId}/classes/{classId}/opt-in")
    public Result<?> optIn(@PathVariable Long planId, @PathVariable Long classId, Authentication auth) {
        return taskPlanService.optInClass(planId, classId, getUserId(auth));
    }

    @GetMapping("/{planId}/classes/{classId}/progress")
    public Result<?> progress(@PathVariable Long planId, @PathVariable Long classId) {
        return taskPlanService.getClassProgress(planId, classId);
    }

    @GetMapping("/{planId}/classes/{classId}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long planId, @PathVariable Long classId) {
        byte[] data = taskPlanService.exportClassScore(planId, classId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=class_score.csv")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
    }

    private Long getUserId(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        return user != null ? user.getId() : null;
    }
}
