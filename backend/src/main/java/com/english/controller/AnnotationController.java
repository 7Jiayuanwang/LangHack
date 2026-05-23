package com.english.controller;

import com.english.dto.Result;
import com.english.entity.TextAnnotation;
import com.english.service.AnnotationService;
import com.english.service.UserService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/student/annotations")
@PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
public class AnnotationController {

    @Autowired
    private AnnotationService annotationService;

    @Autowired
    private UserService userService;

    @GetMapping("/text/{textId}")
    public Result<List<TextAnnotation>> list(@PathVariable Long textId, Authentication auth) {
        Long userId = getUserId(auth);
        return Result.success(annotationService.listByText(textId, userId));
    }

    @PostMapping
    public Result<TextAnnotation> create(@RequestBody CreateRequest req, Authentication auth) {
        Long userId = getUserId(auth);
        if (req.getSelectedText() == null || req.getSelectedText().isBlank()) {
            return Result.error("请选择要批注的文本");
        }
        if (req.getNote() == null || req.getNote().isBlank()) {
            return Result.error("批注内容不能为空");
        }
        TextAnnotation ann = annotationService.create(req.getTextId(), userId,
                req.getSelectedText().trim(), req.getNote().trim());
        return Result.success(ann);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, Authentication auth) {
        Long userId = getUserId(auth);
        annotationService.delete(id, userId);
        return Result.success(null);
    }

    @PostMapping("/{id}/upvote")
    public Result<Map<String, Object>> upvote(@PathVariable Long id, Authentication auth) {
        Long userId = getUserId(auth);
        int count = annotationService.toggleUpvote(id, userId);
        return Result.success(Map.of("upvoteCount", count));
    }

    private Long getUserId(Authentication auth) {
        return userService.getUserByUsername(auth.getName()).getId();
    }

    @Data
    public static class CreateRequest {
        private Long textId;
        private String selectedText;
        private String note;
    }
}
