package com.english.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.english.entity.AssignmentTemplate;
import com.english.entity.User;
import com.english.dto.Result;
import com.english.service.AssignmentTemplateService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher/assignment-templates")
@PreAuthorize("hasRole('TEACHER')")
public class AssignmentTemplateController {
    @Autowired private AssignmentTemplateService templateService;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;

    @GetMapping
    public Result<List<Map<String, Object>>> list(Authentication authentication) {
        Long teacherId = currentUserId(authentication);
        return Result.success(templateService.listByTeacher(teacherId).stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                               Authentication authentication) {
        AssignmentTemplate template = fromRequest(body);
        template.setCreatedBy(currentUserId(authentication));
        templateService.create(template);
        return Result.success(toResponse(template));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id,
                                               @RequestBody Map<String, Object> body,
                                               Authentication authentication) {
        AssignmentTemplate template = fromRequest(body);
        template.setId(id);
        Long teacherId = currentUserId(authentication);
        templateService.update(template, teacherId);
        return Result.success(toResponse(template));
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id, Authentication authentication) {
        templateService.delete(id, currentUserId(authentication));
        return Result.success("模板已删除");
    }

    private AssignmentTemplate fromRequest(Map<String, Object> body) {
        AssignmentTemplate template = new AssignmentTemplate();
        template.setName(body.get("name") == null ? null : body.get("name").toString());
        template.setDescription(body.get("description") == null ? null : body.get("description").toString());
        Object config = body.get("config");
        if (config == null) config = body.get("configJson");
        try {
            template.setConfigJson(config == null ? null : objectMapper.writeValueAsString(config));
        } catch (JsonProcessingException e) {
            throw new com.english.exception.BusinessException("模板配置格式错误");
        }
        return template;
    }

    private Map<String, Object> toResponse(AssignmentTemplate template) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", template.getId());
        result.put("name", template.getName());
        result.put("description", template.getDescription());
        try {
            result.put("config", objectMapper.readValue(template.getConfigJson(), Map.class));
        } catch (Exception e) {
            result.put("config", Map.of());
        }
        result.put("createdTime", template.getCreatedTime());
        result.put("updatedTime", template.getUpdatedTime());
        return result;
    }

    private Long currentUserId(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        if (user == null) throw new com.english.exception.BusinessException("用户不存在");
        return user.getId();
    }
}
