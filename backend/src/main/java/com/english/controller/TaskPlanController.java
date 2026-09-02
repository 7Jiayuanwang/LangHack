package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.mapper.ClassMapper;
import com.english.service.TaskPlanService;
import com.english.service.UserService;
import com.english.service.impl.WordCategoryImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/task-plans")
@PreAuthorize("hasAnyRole('ADMIN','OPS')")
public class TaskPlanController {

    @Autowired
    private TaskPlanService taskPlanService;
    @Autowired
    private UserService userService;
    @Autowired
    private ClassMapper classMapper;
    @Autowired
    private WordCategoryImportService wordCategoryImportService;

    @PostMapping("/reimport-word-categories")
    public Result<?> reimportWordCategories() {
        int count = wordCategoryImportService.reimport();
        return Result.success("重新导入完成，共 " + count + " 条");
    }

    @GetMapping("/course-labels")
    public Result<?> courseLabels() {
        List<String> labels = classMapper.selectDistinctCourseLabels();
        return Result.success(labels);
    }

    @GetMapping("/classes-by-label")
    public Result<?> classesByLabel(@RequestParam String courseLabel) {
        List<Map<String, Object>> list = classMapper.selectClassesByLabel(courseLabel);
        return Result.success(list);
    }

    @PostMapping
    public Result<?> create(@RequestBody Map<String, Object> params, Authentication auth) {
        Long userId = getUserId(auth);
        return taskPlanService.createPlan(params, userId);
    }

    @GetMapping
    public Result<?> list(@RequestParam(required = false) String status) {
        Map<String, Object> params = new java.util.HashMap<>();
        if (status != null) params.put("status", status);
        return taskPlanService.listPlans(params);
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return taskPlanService.getPlanDetail(id);
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        return taskPlanService.updatePlan(id, params);
    }

    @PostMapping("/{id}/publish")
    public Result<?> publish(@PathVariable Long id) {
        return taskPlanService.publishPlan(id);
    }

    @PostMapping("/{id}/close")
    public Result<?> close(@PathVariable Long id) {
        return taskPlanService.closePlan(id);
    }

    @GetMapping("/{id}/stats")
    public Result<?> stats(@PathVariable Long id) {
        return taskPlanService.getPlanStats(id);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        byte[] data = taskPlanService.exportPlanScore(id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=score.csv")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
    }

    @PostMapping("/{id}/refresh")
    public Result<?> refresh(@PathVariable Long id) {
        taskPlanService.refreshPeriodRecords(id);
        return Result.success("刷新完成");
    }

    private Long getUserId(Authentication auth) {
        User user = userService.getUserByUsername(auth.getName());
        return user != null ? user.getId() : null;
    }
}
