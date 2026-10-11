package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.UserService;
import com.english.service.impl.CourseExemptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 教师端大学英语免修/免考审批入口。 */
@RestController
@RequestMapping("/api/teacher/exemption")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherExemptionController {

    @Autowired
    private CourseExemptionService exemptionService;

    @Autowired
    private UserService userService;

    @GetMapping("/applications")
    public Result<?> list(@RequestParam(defaultValue = "") String status,
                          @RequestParam(defaultValue = "1") long page,
                          @RequestParam(defaultValue = "10") long size) {
        return exemptionService.list(status, page, size);
    }

    @GetMapping("/stats")
    public Result<?> stats() {
        return exemptionService.stats();
    }

    @GetMapping("/applications/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return exemptionService.detail(id);
    }

    @PostMapping("/applications/{id}/approve")
    public Result<?> approve(@PathVariable Long id,
                             @RequestBody(required = false) Map<String, String> body,
                             Authentication auth) {
        String comment = body == null ? null : body.get("comment");
        return exemptionService.review(id, getUserId(auth), true, comment);
    }

    @PostMapping("/applications/{id}/reject")
    public Result<?> reject(@PathVariable Long id,
                            @RequestBody Map<String, String> body,
                            Authentication auth) {
        String comment = body == null ? null : body.get("comment");
        return exemptionService.review(id, getUserId(auth), false, comment);
    }

    private Long getUserId(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        return user != null ? user.getId() : null;
    }
}
