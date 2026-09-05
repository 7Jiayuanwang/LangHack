package com.english.controller;

import com.english.dto.AssignmentContent;
import com.english.dto.ArticleFilterDTO;
import com.english.dto.Result;
import com.english.entity.Assignment;
import com.english.entity.Text;
import com.english.entity.User;
import com.english.entity.Word;
import com.english.entity.Unit;
import com.english.entity.UnitWord;
import com.english.exception.BusinessException;
import com.english.service.AssignmentService;
import com.english.service.TextService;
import com.english.service.WordService;
import com.english.service.UserService;
import com.english.service.ClassService;
import com.english.service.UnitService;
import com.english.mapper.TextMapper;
import com.english.mapper.UnitWordMapper;
import com.english.mapper.WordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private static final Logger logger = LoggerFactory.getLogger(AssignmentController.class);

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private UserService userService;

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private WordMapper wordMapper;

    @Autowired
    private UnitWordMapper unitWordMapper;
    
    @Autowired
    private ClassService classService;

    @Autowired
    private UnitService unitService;

    @Autowired
    private TextService textService;

    @GetMapping("/articles")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<?> getArticleOptions(@ModelAttribute ArticleFilterDTO filter) {
        // OUTER 类型使用分页查询（数据量大）
        if (filter != null && filter.getArticleType() == com.english.entity.ArticleType.OUTER
                && filter.getBookVolume() == null && filter.getContentSection() == null && filter.getUnitNum() == null) {
            return Result.success(textService.getArticlesPaged(filter));
        }
        // INNER 类型或带层级过滤的继续用全量返回（数据量小）
        return Result.success(textService.getArticles(filter));
    }

    /**
     * 分页查询任务列表
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> getAssignments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {

        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            // 关键词长度限制
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("classId", classId);
            params.put("keyword", keyword);

            // 教师只能查看自己创建的任务
            String userRole = getCurrentRole(authentication);
            if ("ROLE_TEACHER".equals(userRole)) {
                Long teacherId = getCurrentUserId(authentication);
                params.put("teacherId", teacherId);
            }

            Map<String, Object> result = assignmentService.getAssignmentsByPage(params);
            return Result.success(result);
        } catch (com.english.exception.BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 根据ID获取任务详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Assignment> getAssignmentById(@PathVariable Long id, Authentication authentication) {
        try {
            String userRole = getCurrentRole(authentication);

            // 非管理员只能查看自己创建的任务
            if (!"ROLE_ADMIN".equals(userRole)) {
                Long teacherId = getCurrentUserId(authentication);
                if (!assignmentService.validateTeacherPermission(teacherId, id)) {
                    return Result.error("没有权限查看此任务");
                }
            }

            Assignment assignment = assignmentService.getAssignmentById(id);
            if (assignment == null) {
                return Result.error("任务不存在");
            }
            return Result.success(assignment);
        } catch (com.english.exception.BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 创建任务（兼容原有接口）
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> createAssignment(@RequestBody Assignment assignment, Authentication authentication) {
        try {
            String userRole = getCurrentRole(authentication);

            // 教师只能创建自己管理的班级的任务
            if ("ROLE_TEACHER".equals(userRole)) {
                Long teacherId = getCurrentUserId(authentication);
                assignment.setCreatedBy(teacherId);

                // 验证教师是否有权限管理该班级
                if (assignment.getClassId() != null) {
                    var classInfo = classService.getClassById(assignment.getClassId());
                    if (classInfo == null || !teacherId.equals(classInfo.getTeacherId())) {
                        return Result.error("没有权限为该班级创建任务");
                    }
                }
            } else {
                // 管理员也需要设置 createdBy
                assignment.setCreatedBy(getCurrentUserId(authentication));
            }

            // 设置默认值
            if (assignment.getWeight() == null) {
                assignment.setWeight(1.0);
            }
            if (assignment.getIdealStudyTimeMinutes() == null) {
                assignment.setIdealStudyTimeMinutes(60);
            }

            assignmentService.createAssignment(assignment);
            return Result.success("任务创建成功");
        } catch (com.english.exception.BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 创建指定内容的任务
     */
    @PostMapping("/create-with-content")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> createAssignmentWithContent(@RequestBody Map<String, Object> requestBody, Authentication authentication) {
        try {
            if (requestBody.get("title") == null || requestBody.get("assignmentType") == null) {
                return Result.error("必填字段不能为空：title、assignmentType");
            }

            // 支持多班级：优先读 classIds 数组，兼容旧的单个 classId
            List<Long> classIds = new ArrayList<>();
            if (requestBody.get("classIds") instanceof List) {
                for (Object id : (List<?>) requestBody.get("classIds")) {
                    classIds.add(Long.valueOf(id.toString()));
                }
            } else if (requestBody.get("classId") != null) {
                classIds.add(Long.valueOf(requestBody.get("classId").toString()));
            }
            if (classIds.isEmpty()) {
                return Result.error("请至少选择一个班级");
            }

            Long firstClassId = classIds.get(0);

            String title = requestBody.get("title").toString();
            String description = requestBody.get("description") != null ? requestBody.get("description").toString() : "";
            String assignmentType = requestBody.get("assignmentType").toString();
            Long unitId = null;
            if (requestBody.get("unitId") != null && !requestBody.get("unitId").toString().isBlank()) {
                try {
                    unitId = Long.valueOf(requestBody.get("unitId").toString());
                } catch (NumberFormatException e) {
                    logger.error("unitId格式错误", e);
                    return Result.error("unitId格式错误，必须为数字");
                }
            }
            if (!"TEXTS".equals(assignmentType) && unitId == null) {
                return Result.error("UNIT和WORDS类型任务必须选择单元");
            }

            // 指定课文由文章选择器决定内容，不依赖平台课程或单元。
            com.english.entity.Class classInfoForValidation = classService.getClassById(firstClassId);
            if (classInfoForValidation == null) {
                return Result.error("班级不存在");
            }
            if (unitId != null) {
                Unit unitInfoForValidation = unitService.getUnitById(unitId);
                if (unitInfoForValidation == null) {
                    return Result.error("单元不存在");
                }
                if (classInfoForValidation.getCourseId() == null || !classInfoForValidation.getCourseId().equals(unitInfoForValidation.getCourseId())) {
                    return Result.error("单元与班级所属课程不一致");
                }
            }

            // 创建任务对象
            Assignment assignment = new Assignment();
            assignment.setClassId(firstClassId);
            assignment.setUnitId(unitId);
            assignment.setTitle(title);

            // 设置任务权重
            if (requestBody.get("weight") != null) {
                try {
                    assignment.setWeight(Double.valueOf(requestBody.get("weight").toString()));
                } catch (NumberFormatException e) {
                    logger.error("权重格式错误", e);
                    return Result.error("权重格式错误，必须为数字");
                }
            } else {
                assignment.setWeight(1.0); // 默认权重为1.0
            }

            // 设置理想学习时长
            if (requestBody.get("idealStudyTimeMinutes") != null) {
                try {
                    assignment.setIdealStudyTimeMinutes(Integer.valueOf(requestBody.get("idealStudyTimeMinutes").toString()));
                } catch (NumberFormatException e) {
                    logger.error("学习时长格式错误", e);
                    return Result.error("学习时长格式错误，必须为整数");
                }
            } else {
                assignment.setIdealStudyTimeMinutes(60); // 默认60分钟
            }

            // 设置截止时间
            if (requestBody.get("deadline") != null && !requestBody.get("deadline").toString().isEmpty()) {
                try {
                    String deadlineStr = requestBody.get("deadline").toString();
                    java.time.LocalDateTime deadline = parseFlexibleDateTime(deadlineStr);
                    assignment.setDeadline(deadline);
                } catch (Exception e) {
                    logger.error("解析截止时间失败: {}", requestBody.get("deadline"), e);
                    return Result.error("截止时间格式错误，请使用正确的日期时间格式（yyyy-MM-dd HH:mm:ss）");
                }
            }

            // 设置任务描述
            assignment.setDescription(description);

            // 设置教师ID，并验证教师对班级的权限
            String userRole = getCurrentRole(authentication);
            Long teacherId = null;
            if ("ROLE_TEACHER".equals(userRole)) {
                teacherId = getCurrentUserId(authentication);
                assignment.setCreatedBy(teacherId);
            } else {
                assignment.setCreatedBy(getCurrentUserId(authentication));
            }

            // 设置任务内容
            AssignmentContent content = new AssignmentContent();
            content.setType(assignmentType);
            AssignmentContent.ContentConfig config = new AssignmentContent.ContentConfig();

            switch (assignmentType) {
                case "TEXTS":
                    List<?> textIds = requestBody.get("textIds") instanceof List ? (List<?>) requestBody.get("textIds") : null;
                    if (textIds == null || textIds.isEmpty()) {
                        return Result.error("TEXTS类型任务必须指定课文列表");
                    }
                    config.setTextIds(textIds.stream().map(id -> Long.valueOf(id.toString())).collect(java.util.stream.Collectors.toList()));
                    break;
                case "WORDS":
                    List<?> wordIds = requestBody.get("wordIds") instanceof List ? (List<?>) requestBody.get("wordIds") : null;
                    if (wordIds == null || wordIds.isEmpty()) {
                        return Result.error("WORDS类型任务必须指定单词列表");
                    }
                    config.setWordIds(wordIds.stream().map(id -> Long.valueOf(id.toString())).collect(java.util.stream.Collectors.toList()));
                    break;
                case "UNIT":
                    // 单元任务，包含所有内容
                    config.setIncludeAllTexts(true);
                    config.setIncludeAllWords(true);
                    break;
            }

            content.setContent(config);
            assignment.setAssignmentContent(content);

            // 多班级批量创建：为每个班级生成独立的任务
            int created = 0;
            List<String> failedClasses = new ArrayList<>();
            for (Long cid : classIds) {
                try {
                    com.english.entity.Class cInfo = classService.getClassById(cid);
                    if (cInfo == null) {
                        failedClasses.add("班级ID " + cid + " 不存在");
                        continue;
                    }
                    if ("ROLE_TEACHER".equals(userRole) && !teacherId.equals(cInfo.getTeacherId())) {
                        failedClasses.add("无权限: " + (cInfo.getName() != null ? cInfo.getName() : cid));
                        continue;
                    }
                    Assignment a = new Assignment();
                    a.setClassId(cid);
                    a.setUnitId(assignment.getUnitId());
                    a.setTitle(assignment.getTitle());
                    a.setWeight(assignment.getWeight());
                    a.setIdealStudyTimeMinutes(assignment.getIdealStudyTimeMinutes());
                    a.setDeadline(assignment.getDeadline());
                    a.setDescription(assignment.getDescription());
                    a.setCreatedBy(assignment.getCreatedBy());
                    a.setAssignmentContent(content);
                    assignmentService.createAssignment(a);
                    created++;
                } catch (Exception e) {
                    logger.warn("为班级{}创建任务失败", cid, e);
                    failedClasses.add("班级ID " + cid + " 创建失败");
                }
            }
            if (created == 0) {
                return Result.error("所有班级创建失败: " + String.join("; ", failedClasses));
            }
            String msg = "成功为 " + created + " 个班级创建任务";
            if (!failedClasses.isEmpty()) {
                msg += "，部分失败: " + String.join("; ", failedClasses);
            }
            return Result.success(msg);
        } catch (com.english.exception.BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("任务创建失败", e);

            return Result.error("任务创建失败，请稍后重试");
        }
    }

    /**
     * 更新任务
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> updateAssignment(@PathVariable Long id, @RequestBody Assignment assignment, Authentication authentication) {
        try {
            assignment.setId(id);

            // 非管理员只能更新自己创建的任务
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))) {
                Long teacherId = getCurrentUserId(authentication);
                if (!assignmentService.validateTeacherPermission(teacherId, id)) {
                    return Result.error("没有权限修改此任务");
                }
                // 教师不能修改任务的负责人
                assignment.setCreatedBy(teacherId);
                // 教师修改classId时需验证对目标班级的权限
                if (assignment.getClassId() != null) {
                    var classInfo = classService.getClassById(assignment.getClassId());
                    if (classInfo == null || !teacherId.equals(classInfo.getTeacherId())) {
                        return Result.error("没有权限将任务分配到该班级");
                    }
                }
            }

            assignmentService.updateAssignment(assignment);
            return Result.success("任务更新成功");
        } catch (com.english.exception.BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> deleteAssignment(@PathVariable Long id, Authentication authentication) {
        try {
            // 非管理员只能删除自己创建的任务
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))) {
                Long teacherId = getCurrentUserId(authentication);
                if (!assignmentService.validateTeacherPermission(teacherId, id)) {
                    return Result.error("没有权限删除此任务");
                }
            }

            assignmentService.deleteAssignment(id);
            return Result.success("任务删除成功");
        } catch (com.english.exception.BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

  
    /**
     * 获取教师统计信息（用于仪表板）
     */
    @GetMapping("/teacher/stats")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Map<String, Object>> getTeacherStats(Authentication authentication) {
        try {
            Long teacherId = getCurrentUserId(authentication);
            Map<String, Object> stats = assignmentService.getTeacherStats(teacherId);
            return Result.success(stats);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 获取教师的班级列表（用于仪表板）
     */
    @GetMapping("/teacher/classes")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<List<Map<String, Object>>> getTeacherClasses(Authentication authentication) {
        try {
            Long teacherId = getCurrentUserId(authentication);
            List<Map<String, Object>> classes = assignmentService.getTeacherClassesWithStats(teacherId);
            return Result.success(classes);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 获取教师的最近任务列表（用于仪表板）
     */
    @GetMapping("/teacher/recent")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<List<Map<String, Object>>> getRecentAssignments(
            @RequestParam(defaultValue = "5") int limit,
            Authentication authentication) {
        try {
            limit = Math.max(1, Math.min(limit, 50));
            Long teacherId = getCurrentUserId(authentication);
            List<Map<String, Object>> assignments = assignmentService.getRecentAssignmentsWithStats(teacherId, limit);
            return Result.success(assignments);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取最近任务失败", e);

            return Result.error("获取最近任务失败，请稍后重试");
        }
    }

    /**
     * 获取任务进度统计信息
     */
    @GetMapping("/{assignmentId}/progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<List<Map<String, Object>>> getAssignmentProgress(
            @PathVariable Long assignmentId,
            Authentication authentication) {
        try {
            // 验证教师权限
            String userRole = getCurrentRole(authentication);
            if (!"ROLE_ADMIN".equals(userRole)) {
                Long teacherId = getCurrentUserId(authentication);
                if (!assignmentService.validateTeacherPermission(teacherId, assignmentId)) {
                    return Result.error("没有权限查看此任务的进度");
                }
            }

            List<Map<String, Object>> progress = assignmentService.getAssignmentProgress(assignmentId);
            return Result.success(progress);
        } catch (com.english.exception.BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 获取单元内容（用于创建任务时选择课文和单词）
     */
    @GetMapping("/unit/{unitId}/content")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> getUnitContent(@PathVariable Long unitId, Authentication authentication) {
        try {
            // 校验单元存在
            Unit unitForAccess = unitService.getUnitById(unitId);
            if (unitForAccess == null) {
                return Result.error("单元不存在");
            }

            // 教师只能查看自己班级关联课程的单元
            String userRole = getCurrentRole(authentication);
            if ("ROLE_TEACHER".equals(userRole)) {
                Long teacherId = getCurrentUserId(authentication);
                List<com.english.entity.Class> teacherClasses = classService.getClassesByTeacherId(teacherId);
                boolean hasAccess = false;
                if (teacherClasses != null && unitForAccess.getCourseId() != null) {
                    for (com.english.entity.Class cls : teacherClasses) {
                        if (unitForAccess.getCourseId().equals(cls.getCourseId())) {
                            hasAccess = true;
                            break;
                        }
                    }
                }
                if (!hasAccess) {
                    return Result.error("无权访问该单元内容");
                }
            }

            Map<String, Object> content = new HashMap<>();

            // 获取单元课文
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Text> textWrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            textWrapper.eq("unit_id", unitId).eq("deleted", 0).orderByAsc("order_num");
            List<Text> texts = textMapper.selectList(textWrapper);
            if (texts == null) {
                texts = new ArrayList<>();
            }

            // 获取单元单词
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UnitWord> unitWordWrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            unitWordWrapper.eq("unit_id", unitId);
            List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);
            if (unitWords == null) {
                unitWords = new ArrayList<>();
            }

            List<Long> wordIds = unitWords.stream().map(UnitWord::getWordId).collect(Collectors.toList());
            List<Word> words = new ArrayList<>();
            if (!wordIds.isEmpty()) {
                com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Word> wordWrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
                wordWrapper.in("id", wordIds);
                wordWrapper.eq("deleted", 0);
                List<Word> queryResult = wordMapper.selectList(wordWrapper);
                if (queryResult != null) {
                    words = queryResult;
                }
            }

            content.put("texts", texts);
            content.put("words", words);

            return Result.success(content);
        } catch (Exception e) {
            logger.error("获取单元内容失败", e);

            return Result.error("获取单元内容失败，请稍后重试");
        }
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        if (user == null) {
            throw new com.english.exception.BusinessException("用户不存在");
        }
        return user.getId();
    }

    private String getCurrentRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().isEmpty()) {
            throw new com.english.exception.BusinessException("认证信息缺失");
        }
        boolean hasAdmin = false;
        boolean hasOps = false;
        boolean hasTeacher = false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            switch (authority.getAuthority()) {
                case "ROLE_ADMIN":
                    hasAdmin = true;
                    break;
                case "ROLE_OPS":
                    hasOps = true;
                    break;
                case "ROLE_TEACHER":
                    hasTeacher = true;
                    break;
                default:
                    break;
            }
        }
        if (hasAdmin) return "ROLE_ADMIN";
        if (hasOps) return "ROLE_OPS";
        if (hasTeacher) return "ROLE_TEACHER";
        return authentication.getAuthorities().iterator().next().getAuthority();
    }

    /**
     * 获取单个学生ID（学生端使用）
     */
    private Long getCurrentStudentId(Authentication authentication) {
        return getCurrentUserId(authentication);
    }

    private java.time.LocalDateTime parseFlexibleDateTime(String str) {
        // 标准格式: yyyy-MM-dd HH:mm:ss
        try {
            return java.time.LocalDateTime.parse(str, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {}
        // ISO 格式: 2026-07-20T23:59:59 或带毫秒
        try {
            return java.time.LocalDateTime.parse(str, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {}
        // ISO 带时区: 2026-07-20T23:59:59.000Z
        try {
            return java.time.ZonedDateTime.parse(str).withZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        } catch (Exception ignored) {}
        // ISO Instant 格式
        try {
            return java.time.LocalDateTime.ofInstant(java.time.Instant.parse(str), java.time.ZoneId.of("Asia/Shanghai"));
        } catch (Exception ignored) {}
        throw new IllegalArgumentException("无法解析日期时间: " + str);
    }
}
