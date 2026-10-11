package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.AssignmentContent;
import com.english.dto.Result;
import com.english.entity.Assignment;
import com.english.entity.Class;
import com.english.entity.StudentClass;
import com.english.entity.User;
import com.english.mapper.AssignmentStudentMapper;
import com.english.mapper.StudentClassMapper;
import com.english.service.AssignmentService;
import com.english.service.ClassService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Teacher-to-selected-students flexible reading assignments. */
@RestController
@RequestMapping("/api/teacher/reading-assignments")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class TargetedReadingAssignmentController {
    @Autowired private AssignmentService assignmentService;
    @Autowired private AssignmentStudentMapper assignmentStudentMapper;
    @Autowired private StudentClassMapper studentClassMapper;
    @Autowired private ClassService classService;
    @Autowired private UserService userService;

    @GetMapping("/students")
    public Result<List<Map<String, Object>>> students(@RequestParam Long classId, Authentication authentication) {
        ensureClassAccess(classId, authentication);
        List<StudentClass> rows = studentClassMapper.selectStudentsByClassId(classId);
        List<Map<String, Object>> result = rows.stream().map(row -> {
            User student = row.getStudent();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getStudentId());
            item.put("username", student == null ? String.valueOf(row.getStudentId()) : student.getUsername());
            item.put("name", student == null ? null : (student.getRealName() != null ? student.getRealName() : student.getNickname()));
            return item;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(Authentication authentication) {
        Long teacherId = currentUser(authentication).getId();
        QueryWrapper<Assignment> query = new QueryWrapper<>();
        query.eq("created_by", teacherId).eq("status", "PUBLISHED").orderByDesc("created_time");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Assignment assignment : assignmentService.list(query)) {
            AssignmentContent content = assignment.parseContentFromDescription();
            if (content == null || !("READING_MIXED".equals(content.getType()) || "READING_FLEX".equals(content.getType()))) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", assignment.getId());
            item.put("title", assignment.getTitle());
            item.put("deadline", assignment.getDeadline());
            item.put("createdTime", assignment.getCreatedTime());
            item.put("studentIds", assignmentStudentMapper.selectStudentIds(assignment.getId()));
            AssignmentContent.ContentConfig cfg = content.getContent();
            int innerCount = cfg == null || cfg.getInnerRequiredCount() == null
                    ? (cfg == null || cfg.getInnerTextIds() == null ? 0 : cfg.getInnerTextIds().size()) : cfg.getInnerRequiredCount();
            int outerCount = cfg == null || cfg.getOuterRequiredCount() == null
                    ? (cfg == null || cfg.getOuterTextIds() == null ? 0 : cfg.getOuterTextIds().size()) : cfg.getOuterRequiredCount();
            item.put("innerCount", innerCount);
            item.put("outerCount", outerCount);
            item.put("articleType", innerCount > 0 && outerCount > 0 ? "BOTH" : (outerCount > 0 ? "OUTER" : "INNER"));
            item.put("requiredCount", Math.max(innerCount, outerCount));
            result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping
    @Transactional
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body, Authentication authentication) {
        User teacher = currentUser(authentication);
        String title = stringValue(body.get("title"));
        List<Long> studentIds;
        try {
            studentIds = longList(body.get("studentIds"));
        } catch (NumberFormatException ex) {
            return Result.error("学生ID格式错误");
        }
        if (title == null || title.isBlank()) return Result.error("请输入作业标题");
        if (studentIds.isEmpty()) return Result.error("请至少指定一名学生");
        int innerRequiredCount;
        int outerRequiredCount;
        if (body.containsKey("innerRequiredCount") || body.containsKey("outerRequiredCount")) {
            innerRequiredCount = intValue(body.get("innerRequiredCount"), 0);
            outerRequiredCount = intValue(body.get("outerRequiredCount"), 0);
        } else {
            // Keep accepting the first version of the API while deployed clients refresh.
            String articleType = Optional.ofNullable(stringValue(body.get("articleType")))
                    .map(value -> value.toUpperCase(Locale.ROOT)).orElse("INNER");
            if (!"INNER".equals(articleType) && !"OUTER".equals(articleType)) {
                return Result.error("文章范围必须为课内或课外");
            }
            int requiredCount = intValue(body.get("requiredCount"), 1);
            innerRequiredCount = "INNER".equals(articleType) ? requiredCount : 0;
            outerRequiredCount = "OUTER".equals(articleType) ? requiredCount : 0;
        }
        if (innerRequiredCount < 0 || innerRequiredCount > 100
                || outerRequiredCount < 0 || outerRequiredCount > 100) {
            return Result.error("课内和课外完成篇数必须在0到100之间");
        }
        if (innerRequiredCount + outerRequiredCount < 1) {
            return Result.error("课内和课外文章至少需要完成1篇");
        }

        if (body.get("classId") == null) return Result.error("请选择班级");
        Long classId;
        try {
            classId = Long.valueOf(body.get("classId").toString());
        } catch (NumberFormatException ex) {
            return Result.error("班级ID格式错误");
        }
        ensureClassAccess(classId, authentication);
        Set<Long> classStudents = studentClassMapper.selectStudentsByClassId(classId).stream()
                .map(StudentClass::getStudentId).collect(Collectors.toSet());
        if (!classStudents.containsAll(studentIds)) return Result.error("只能给所选班级中的学生布置作业");

        Assignment assignment = new Assignment();
        assignment.setTitle(title.trim());
        assignment.setClassId(null); // target list, never expose this as a whole-class task
        assignment.setCreatedBy(teacher.getId());
        try {
            assignment.setDeadline(parseDate(body.get("deadline")));
        } catch (RuntimeException ex) {
            return Result.error("截止时间格式错误");
        }
        AssignmentContent content = new AssignmentContent();
        content.setType("READING_FLEX");
        content.setNote(stringValue(body.get("note")));
        AssignmentContent.ContentConfig cfg = new AssignmentContent.ContentConfig();
        cfg.setInnerTextIds(Collections.emptyList());
        cfg.setOuterTextIds(Collections.emptyList());
        cfg.setInnerRequiredCount(innerRequiredCount);
        cfg.setOuterRequiredCount(outerRequiredCount);
        cfg.setInnerMode("FREE");
        cfg.setOuterMode("FREE");
        content.setContent(cfg);
        assignment.setAssignmentContent(content);
        assignmentService.createAssignment(assignment);
        assignmentStudentMapper.insertStudents(assignment.getId(), studentIds);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", assignment.getId());
        response.put("studentCount", studentIds.size());
        response.put("articleType", innerRequiredCount > 0 && outerRequiredCount > 0
                ? "BOTH" : (outerRequiredCount > 0 ? "OUTER" : "INNER"));
        response.put("innerRequiredCount", innerRequiredCount);
        response.put("outerRequiredCount", outerRequiredCount);
        return Result.success(response);
    }

    private User currentUser(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        if (user == null) throw new IllegalArgumentException("用户不存在");
        return user;
    }

    private void ensureClassAccess(Long classId, Authentication authentication) {
        if (authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) return;
        if (!classService.validateTeacherPermission(currentUser(authentication).getId(), classId)) {
            throw new IllegalArgumentException("无权访问该班级");
        }
    }

    private static String stringValue(Object value) { return value == null ? null : value.toString(); }
    private static List<Long> longList(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        return list.stream().filter(Objects::nonNull).map(item -> Long.valueOf(item.toString())).distinct().collect(Collectors.toList());
    }
    private static int intValue(Object value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(value.toString()); } catch (Exception ignored) { return fallback; }
    }
    private static LocalDateTime parseDate(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return LocalDateTime.parse(value.toString().replace("Z", "").substring(0, Math.min(19, value.toString().replace("Z", "").length())));
    }
}
