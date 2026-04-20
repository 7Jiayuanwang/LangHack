package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.Result;
import com.english.entity.User;
import com.english.entity.Class;
import com.english.entity.Unit;
import com.english.entity.Word;
import com.english.entity.Text;
import com.english.entity.UnitWord;
import com.english.entity.ArticleFavorite;
import com.english.mapper.ClassMapper;
import com.english.mapper.UnitMapper;
import com.english.mapper.UnitWordMapper;
import com.english.mapper.WordMapper;
import com.english.mapper.TextMapper;
import com.english.entity.TextLearningRecord;
import com.english.mapper.TextLearningRecordMapper;
import com.english.mapper.ArticleFavoriteMapper;
import com.english.exception.BusinessException;
import com.english.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生学习控制器
 */
@RestController
@RequestMapping("/api/student/learning")
public class StudentLearningController {

    private static final Logger logger = LoggerFactory.getLogger(StudentLearningController.class);

    @Autowired
    private StudentClassesService studentClassesService;

    @Autowired
    private VocabularyBooksService vocabularyBooksService;

    @Autowired
    private LearningRecordsService learningRecordsService;

    @Autowired
    private UserService userService;

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private ScoreRecordService scoreRecordService;

    @Autowired
    private ClassMapper classMapper;

    @Autowired
    private UnitMapper unitMapper;

    @Autowired
    private WordMapper wordMapper;

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private UnitWordMapper unitWordMapper;

    @Autowired
    private TextLearningRecordMapper textLearningRecordMapper;

    @Autowired
    private WordNetService wordNetService;

    @Autowired
    private ArticleVersionService articleVersionService;

    @Autowired
    private ClassService classService;

    @Autowired
    private UnitService unitService;

    @Autowired
    private ArticleFavoriteMapper articleFavoriteMapper;

    /**
     * 获取学生仪表板数据
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> getDashboardData(Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> dashboard = new HashMap<>();

            // 统计课程数量 - 基于student_classes
            dashboard.put("enrolledCourses", studentClassesService.countByStudentId(studentId));

            // 统计生词数量 - 基于vocabulary_books
            dashboard.put("vocabularyCount", vocabularyBooksService.countByStudentId(studentId));

            // 统计待完成任务 - 基于assignments和student_classes
            dashboard.put("pendingAssignments", getPendingAssignmentsCount(studentId));

            // 统计今日学习时长 - 基于learning_records
            dashboard.put("todayStudyTime", learningRecordsService.getTodayStudyTime(studentId));

            // 班级列表 - 基于student_classes关联classes和courses
            dashboard.put("myClasses", studentClassesService.getStudentClassesWithDetails(studentId));

            // 待办任务 - 基于assignments
            dashboard.put("pendingAssignmentsList", getPendingAssignments(studentId));

            // 学习推荐 - 从学生课程中随机推荐内容
            dashboard.put("learningRecommendation", getLearningRecommendation(studentId));

            // 获取学生平均分数（加权平均分）
            Map<String, Object> scoreStats = scoreRecordService.getStudentAverageScore(studentId, null, null);
            if (scoreStats == null) scoreStats = new HashMap<>();

            // 使用加权平均分，如果没有则使用简单平均分
            Object weightedAvg = scoreStats.get("weightedAverageScore");
            Object simpleAvg = scoreStats.get("averageScore");
            Object totalScore = scoreStats.get("totalScore");
            Object totalCount = scoreStats.get("totalCount");

            // 优先使用加权平均分
            double finalAverageScore = 0.0;
            if (weightedAvg != null) {
                finalAverageScore = ((Number) weightedAvg).doubleValue();
            } else if (simpleAvg != null) {
                finalAverageScore = ((Number) simpleAvg).doubleValue();
            }

            dashboard.put("averageScore", Math.round(finalAverageScore * 100.0) / 100.0); // 使用加权平均分，保留2位小数
            dashboard.put("scoreStats", scoreStats); // 包含完整的分数统计信息

            // 添加调试日志
            logger.debug("Dashboard分数统计 - 学生ID: {}, 加权平均分: {}, 简单平均分: {}, 累加分数: {}, 记录数: {}",
                    studentId, weightedAvg, simpleAvg, totalScore, totalCount);

            // 如果分数统计为空或记录数为0，记录警告
            int totalCountInt = totalCount instanceof Number ? ((Number) totalCount).intValue() : 0;
            if (totalCount == null || totalCountInt == 0) {
                logger.debug("学生 {} 暂无分数记录", studentId);
            }

            return Result.success(dashboard);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取仪表板数据失败", e);

            return Result.error("获取仪表板数据失败，请稍后重试");
        }
    }

    /**
     * 获取学生的课程列表
     */
    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> getStudentCourses(Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> result = new HashMap<>();
            // 查询学生加入的班级及其关联的课程
            result.put("courses", studentClassesService.getStudentCoursesWithProgress(studentId));

            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取课程列表失败", e);

            return Result.error("获取课程列表失败，请稍后重试");
        }
    }

    /**
     * 获取课程下的单元列表
     */
    @GetMapping("/courses/{courseId}/units")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> getCourseUnits(
            @PathVariable Long courseId,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            // 校验学生是否有权访问该课程
            assertStudentCanAccessCourse(studentId, courseId);

            Map<String, Object> result = new HashMap<>();
            // 查询课程下的所有单元
            result.put("units", learningRecordsService.getUnitsWithLearningStats(studentId, courseId));

            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取单元列表失败", e);

            return Result.error("获取单元列表失败，请稍后重试");
        }
    }

    /**
     * 获取单元详细内容（课文和单词）
     */
    @GetMapping("/units/{unitId}/content")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> getUnitContent(
            @PathVariable Long unitId,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            // 校验单元所属课程是否可被学生访问
            Unit unit = unitService.getUnitById(unitId);
            if (unit == null) {
                return Result.error("单元不存在");
            }
            if (unit.getCourseId() != null) {
                assertStudentCanAccessCourse(studentId, unit.getCourseId());
            }

            Map<String, Object> content = learningRecordsService.getUnitContentWithLearningStates(studentId, unitId);

            return Result.success(content);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取单元内容失败", e);

            return Result.error("获取单元内容失败，请稍后重试");
        }
    }

    /**
     * 获取课文详细内容
     */
    @GetMapping("/texts/{textId}")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> getTextDetail(
            @PathVariable Long textId,
            @RequestParam(required = false) Long assignmentId,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            // 校验学生是否有权访问该课文
            assertStudentCanRecord(studentId, textId, null, assignmentId);

            // 触发异步版本生成（如果缺失）
            articleVersionService.checkAndGenerateVersions(textId);

            Map<String, Object> detail = learningRecordsService.getTextDetailWithAnnotation(studentId, textId,
                    assignmentId);

            // 移除自动记录逻辑，学习记录由前端学习页面管理
            // 这样可以避免每次刷新页面都增加学习时长

            return Result.success(detail);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取课文详情失败", e);

            return Result.error("获取课文详情失败，请稍后重试");
        }
    }

    /**
     * 记录课文学习
     */
    @PostMapping("/record/text-view")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<String> recordTextView(@RequestBody Map<String, Object> params, Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            Object textIdObj = params.get("textId");
            if (textIdObj == null) {
                return Result.error("textId不能为空");
            }
            Long textId;
            try {
                textId = Long.valueOf(textIdObj.toString());
            } catch (NumberFormatException e) {
                return Result.error("textId格式错误");
            }
            if (textId <= 0) {
                return Result.error("textId无效");
            }
            
            // 参数转换：assignmentId
            Long assignmentId = null;
            if (params.get("assignmentId") != null) {
                try {
                    assignmentId = Long.valueOf(params.get("assignmentId").toString());
                } catch (NumberFormatException e) {
                    return Result.error("assignmentId格式错误");
                }
            }

            // 校验学生是否有权记录该课文的学习
            assertStudentCanRecord(studentId, textId, null, assignmentId);

            // 参数转换：duration
            Integer duration = 15;
            if (params.get("duration") != null) {
                try {
                    duration = Integer.valueOf(params.get("duration").toString());
                } catch (NumberFormatException e) {
                    return Result.error("duration格式错误");
                }
            }
            // 学习时长校验：不允许负数，上限3小时，防止客户端伪造数据
            if (duration < 0) return Result.error("学习时长不能为负数");
            if (duration > 10800) return Result.error("学习时长超过合理范围");

            learningRecordsService.recordTextView(studentId, textId, assignmentId, duration);

            return Result.success("课文学习记录已保存");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("记录课文学习失败", e);

            return Result.error("记录课文学习失败，请稍后重试");
        }
    }

    /**
     * 开始阅读课文
     */
    @PostMapping("/texts/{textId}/start")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Long> startReading(
            @PathVariable Long textId,
            @RequestParam(required = false) Long assignmentId,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            // 校验学生是否有权访问该课文
            assertStudentCanRecord(studentId, textId, null, assignmentId);

            logger.info("开始阅读课文调用: studentId={}, textId={}, assignmentId={}", studentId, textId, assignmentId);
            Long recordId = learningRecordsService.startReadingSession(studentId, textId, assignmentId);
            logger.info("阅读会话创建成功: recordId={}", recordId);
            return Result.success(recordId);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("开始阅读失败", e);

            return Result.error("开始阅读失败，请稍后重试");
        }
    }

    /**
     * 完成阅读课文（手动提交）
     */
    @PostMapping("/records/{recordId}/complete")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<String> completeReading(
            @PathVariable Long recordId,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            if (recordId == null || recordId <= 0) {
                return Result.error("recordId无效");
            }
            TextLearningRecord record = textLearningRecordMapper.selectById(recordId);
            if (record == null) {
                return Result.error("阅读记录不存在");
            }
            // IDOR防护：确保学生只能操作自己的记录
            if (record.getStudentId() == null || !studentId.equals(record.getStudentId())) {
                return Result.error("无权操作此记录");
            }
            logger.info("完成阅读课文调用: recordId={}, studentId={}", recordId, studentId);
            learningRecordsService.completeReadingSession(recordId);
            return Result.success("阅读已完成");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("完成阅读失败", e);

            return Result.error("完成阅读失败，请稍后重试");
        }
    }

    /**
     * 标记课文为已完成
     */
    @PostMapping("/texts/{textId}/complete")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> markTextAsCompleted(
            @PathVariable Long textId,
            @RequestParam(required = false) Long assignmentId,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            // 校验学生是否有权访问该课文
            assertStudentCanRecord(studentId, textId, null, assignmentId);

            logger.info("标记课文完成调用: studentId={}, textId={}, assignmentId={}", studentId, textId, assignmentId);

            // 验证课文是否存在
            Text text = textMapper.selectById(textId);
            if (text == null) {
                return Result.error("课文不存在");
            }

            // 查找该学生对该课文最近一条未完成的阅读记录
            QueryWrapper<TextLearningRecord> qw = new QueryWrapper<>();
            qw.eq("student_id", studentId)
              .eq("text_id", textId)
              .eq("is_completed", 0)
              .orderByDesc("created_time")
              .last("LIMIT 1");
            if (assignmentId != null) {
                qw.eq("assignment_id", assignmentId);
            }
            TextLearningRecord record = textLearningRecordMapper.selectOne(qw);

            LocalDateTime now = LocalDateTime.now();
            if (record != null) {
                // 标记已有记录为完成
                record.setEndTime(now);
                record.setIsCompleted(1);
                long duration = record.getStartTime() != null
                        ? java.time.Duration.between(record.getStartTime(), now).getSeconds() : 0;
                record.setDurationSeconds((int) Math.min(duration, Integer.MAX_VALUE));
                record.setUpdatedTime(now);
                textLearningRecordMapper.updateById(record);
            } else {
                // 没有未完成记录，创建一条已完成的记录
                record = new TextLearningRecord();
                record.setStudentId(studentId);
                record.setTextId(textId);
                record.setAssignmentId(assignmentId);
                record.setStartTime(now);
                record.setEndTime(now);
                record.setIsCompleted(1);
                record.setDurationSeconds(0);
                record.setCreatedTime(now);
                record.setUpdatedTime(now);
                textLearningRecordMapper.insert(record);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("message", "课文已标记为完成");
            result.put("recordId", record.getId());

            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("标记课文完成失败", e);

            return Result.error("标记课文完成失败，请稍后重试");
        }
    }

    /**
     * 通用学习记录API（支持各种学习活动）
     */
    @PostMapping("/record/learning")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<String> recordLearning(@RequestBody Map<String, Object> params, Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            Object typeObj = params.get("type");
            if (typeObj == null) {
                return Result.error("type不能为空");
            }
            String type = typeObj.toString().trim();

            // 参数验证：type
            if (type.isEmpty()) {
                return Result.error("type不能为空");
            }
            
            // 参数转换：wordId
            Long wordId = null;
            if (params.get("wordId") != null) {
                try {
                    wordId = Long.valueOf(params.get("wordId").toString());
                    if (wordId <= 0) {
                        return Result.error("wordId必须大于0");
                    }
                } catch (NumberFormatException e) {
                    return Result.error("wordId格式错误");
                }
            }
            
            // 参数转换：duration
            Integer duration = null;
            if (params.get("duration") != null) {
                try {
                    duration = Integer.valueOf(params.get("duration").toString());
                } catch (NumberFormatException e) {
                    return Result.error("duration格式错误");
                }
            }
            
            // 学习时长校验：不允许负数，上限3小时，防止客户端伪造数据
            if (duration != null && duration < 0) return Result.error("学习时长不能为负数");
            if (duration != null && duration > 10800) return Result.error("学习时长超过合理范围");
            
            // 参数转换：textId
            Long textId = null;
            if (params.get("textId") != null) {
                try {
                    textId = Long.valueOf(params.get("textId").toString());
                    if (textId <= 0) {
                        return Result.error("textId必须大于0");
                    }
                } catch (NumberFormatException e) {
                    return Result.error("textId格式错误");
                }
            }
            
            // 参数转换：assignmentId
            Long assignmentId = null;
            if (params.get("assignmentId") != null) {
                try {
                    assignmentId = Long.valueOf(params.get("assignmentId").toString());
                } catch (NumberFormatException e) {
                    return Result.error("assignmentId格式错误");
                }
            }

            // 校验学生是否有权记录该学习活动
            assertStudentCanRecord(studentId, textId, wordId, assignmentId);

            learningRecordsService.recordLearning(studentId, type, wordId, duration, textId, assignmentId);

            logger.debug("记录学习活动: 学生={}, 类型={}, 时长={}秒{}",
                    studentId, type, duration, assignmentId != null ? ", 任务ID=" + assignmentId : "");

            return Result.success("学习记录已保存");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("记录学习失败", e);

            return Result.error("记录学习失败，请稍后重试");
        }
    }

    /**
     * 记录单词学习
     */
    @PostMapping("/record/word-view")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<String> recordWordView(@RequestBody Map<String, Object> params, Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            Object wordIdObj0 = params.get("wordId");
            if (wordIdObj0 == null) {
                return Result.error("wordId不能为空");
            }
            Long wordId;
            try {
                wordId = Long.valueOf(wordIdObj0.toString());
            } catch (NumberFormatException e) {
                return Result.error("wordId格式错误");
            }
            if (wordId <= 0) {
                return Result.error("wordId无效");
            }
            
            // 参数转换：assignmentId
            Long assignmentId = null;
            if (params.get("assignmentId") != null) {
                try {
                    assignmentId = Long.valueOf(params.get("assignmentId").toString());
                } catch (NumberFormatException e) {
                    return Result.error("assignmentId格式错误");
                }
            }

            // 校验学生是否有权记录该单词的学习
            assertStudentCanRecord(studentId, null, wordId, assignmentId);

            // 参数转换：duration
            Integer duration = 15;
            if (params.get("duration") != null) {
                try {
                    duration = Integer.valueOf(params.get("duration").toString());
                } catch (NumberFormatException e) {
                    return Result.error("duration格式错误");
                }
            }
            // 学习时长校验：不允许负数，上限3小时，防止客户端伪造数据
            if (duration < 0) return Result.error("学习时长不能为负数");
            if (duration > 10800) return Result.error("学习时长超过合理范围");

            learningRecordsService.recordWordView(studentId, wordId, assignmentId, duration);

            return Result.success("单词学习记录已保存");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("记录单词学习失败", e);

            return Result.error("记录单词学习失败，请稍后重试");
        }
    }

    /**
     * 添加单词到生词本
     */
    @PostMapping("/vocabulary/add")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<String> addToVocabulary(@RequestBody Map<String, Object> params, Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            Object wordIdObj1 = params.get("wordId");
            if (wordIdObj1 == null) {
                return Result.error("wordId不能为空");
            }
            Long wordId;
            try {
                wordId = Long.valueOf(wordIdObj1.toString());
            } catch (NumberFormatException e) {
                return Result.error("wordId格式错误");
            }
            if (wordId <= 0) {
                return Result.error("wordId无效");
            }

            if (vocabularyBooksService.existsByStudentAndWord(studentId, wordId)) {
                // 如果已存在，不改变掌握程度
                return Result.success("单词已在生词本中");
            }

            // 新加入生词本，初始状态为 NEW
            vocabularyBooksService.addToVocabularyWithLevel(studentId, wordId, "NEW");

            return Result.success("单词已添加到生词本");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("添加到生词本失败", e);

            return Result.error("添加到生词本失败，请稍后重试");
        }
    }

    /**
     * 获取当前学生ID
     */
    private Long getCurrentStudentId(Authentication authentication) {
        String username = authentication.getName();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user.getId();
    }

    /**
     * 校验学生是否有权访问指定课程
     * 逻辑：学生所在班级 -> 班级关联的课程 -> 是否包含目标courseId
     */
    private void assertStudentCanAccessCourse(Long studentId, Long courseId) {
        // 放行管理员和教师
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_TEACHER") || a.getAuthority().equals("ROLE_OPS"))) {
            return;
        }

        List<Long> classIds = studentClassesService.getClassIdsByStudentId(studentId);
        if (classIds == null || classIds.isEmpty()) {
            throw new BusinessException("您未加入任何班级，无法访问该课程");
        }
        boolean hasAccess = false;
        for (Long classId : classIds) {
            com.english.entity.Class classInfo = classService.getClassById(classId);
            if (classInfo != null && courseId.equals(classInfo.getCourseId())) {
                hasAccess = true;
                break;
            }
        }
        if (!hasAccess) {
            throw new BusinessException("无权访问该课程");
        }
    }

    /**
     * 校验学生是否有权记录学习（校验 textId/wordId/assignmentId 归属）
     */
    private void assertStudentCanRecord(Long studentId, Long textId, Long wordId, Long assignmentId) {
        // 管理员、教师、运维角色拥有所有文章的访问权，直接放行
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_TEACHER")
                        || a.getAuthority().equals("ROLE_OPS"))) {
            return;
        }

        // 如果有 assignmentId，校验学生是否有权访问该任务
        if (assignmentId != null) {
            List<Long> classIds = studentClassesService.getClassIdsByStudentId(studentId);
            if (classIds == null || classIds.isEmpty()
                    || !assignmentService.isAssignmentInClasses(assignmentId, classIds)) {
                throw new BusinessException("无权访问该任务");
            }
        }

        // 如果有 textId，校验课文属于学生可访问的课程
        if (textId != null) {
            Text text = textMapper.selectById(textId);
            if (text == null) {
                throw new BusinessException("课文不存在");
            }
            
            // 允许作者访问自己上传的课文
            if (text.getUploadedBy() != null && text.getUploadedBy().equals(studentId)) {
                return;
            }
            
            // 允许访问别人上传的公开课文
            if (text.getIsPublic() != null && text.getIsPublic() == 1) {
                return;
            }

            // 用户上传专区课文（通常挂在单元999下），如果既非作者又不公开则不能访问
            if (text.getUnitId() != null && text.getUnitId() == 999L) {
                throw new BusinessException("无权访问该课文");
            }

            // 推荐文章（isFeatured=1）作为公开发现内容，跳过课程注册校验
            boolean isFeatured = text.getIsFeatured() != null && text.getIsFeatured() == 1;
            // 如果课文关联了单元，且非推荐文章，则校验单元所属课程
            if (!isFeatured && text.getUnitId() != null) {
                Unit unit = unitService.getUnitById(text.getUnitId());
                if (unit != null && unit.getCourseId() != null) {
                    assertStudentCanAccessCourse(studentId, unit.getCourseId());
                }
            }
        }
    }

    /**
     * 获取待完成任务数量
     */
    private int getPendingAssignmentsCount(Long studentId) {
        try {
            // 获取学生的班级ID列表
            List<Long> classIds = studentClassesService.getClassIdsByStudentId(studentId);
            logger.debug("获取待完成任务: 学生ID={}, 加入的班级IDs={}", studentId, classIds);

            if (classIds.isEmpty()) {
                logger.debug("学生未加入任何班级");
                return 0;
            }

            // 使用AssignmentService获取学生任务
            Map<String, Object> assignmentsData = assignmentService.getStudentAssignmentsWithStatus(classIds, studentId,
                    null);
            Object assignmentsObj = assignmentsData.get("assignments");
            List<Map<String, Object>> assignments = (assignmentsObj instanceof List)
                    ? (List<Map<String, Object>>) assignmentsObj : null;

            if (assignments == null || assignments.isEmpty()) {
                logger.debug("没有找到任何任务");
                return 0;
            }

            logger.debug("获取到的任务数量: {}", assignments.size());

            // 过滤未过期且未完成的任务
            long pendingCount = assignments.stream()
                    .filter(assignment -> {
                        // 首先检查任务状态和进度，过滤已完成的任务（这是最重要的判断）
                        Object status = assignment.get("status");
                        Object progressObj = assignment.get("progress");

                        // 优先使用状态判断，如果状态是completed，则过滤掉
                        if (status != null) {
                            String statusString = status.toString().trim();
                            if ("completed".equalsIgnoreCase(statusString)) {
                                logger.debug("任务 {} 已完成（状态={}），不包含在待办任务中", assignment.get("title"), statusString);
                                return false;
                            }
                        }

                        // 如果没有状态字段或状态不是completed，使用进度判断（进度>=100视为完成）
                        if (progressObj != null) {
                            try {
                                // 处理不同类型的进度值（Integer, Long, Double, String等）
                                double progress;
                                if (progressObj instanceof Number) {
                                    progress = ((Number) progressObj).doubleValue();
                                } else {
                                    progress = Double.parseDouble(progressObj.toString());
                                }
                                if (progress >= 100.0) {
                                    logger.debug("任务 {} 已完成（进度={}>=100），不包含在待办任务中", assignment.get("title"), progress);
                                    return false;
                                }
                            } catch (NumberFormatException e) {
                                logger.warn("任务 {} 进度解析失败: {}, 错误: {}", assignment.get("title"), progressObj,
                                        e.getMessage());
                                // 解析失败，继续使用其他判断
                            }
                        }

                        // 如果任务未完成，再检查是否过期
                        Object deadline = assignment.get("deadline");
                        if (deadline == null) {
                            logger.debug("任务 {} 未完成且无截止时间，包含在待办任务中", assignment.get("title"));
                            return true;
                        }
                        try {
                            LocalDateTime deadlineTime = LocalDateTime.parse(deadline.toString());
                            boolean isNotExpired = LocalDateTime.now().isBefore(deadlineTime);
                            logger.debug("任务 {} 截止时间: {}, 是否未过期: {}", assignment.get("title"), deadlineTime,
                                    isNotExpired);
                            return isNotExpired;
                        } catch (Exception e) {
                            logger.warn("解析任务截止时间失败: {}, 错误: {}", deadline, e.getMessage());
                            return true; // 如果解析失败，算作未过期
                        }
                    })
                    .count();

            logger.debug("最终待完成任务数量: {}", pendingCount);
            return (int) pendingCount;
        } catch (Exception e) {
            logger.error("获取待完成任务数量失败", e);
            return 0;
        }
    }

    /**
     * 获取待完成任务列表
     */
    private List<Map<String, Object>> getPendingAssignments(Long studentId) {
        try {
            // 获取学生的班级ID列表
            List<Long> classIds = studentClassesService.getClassIdsByStudentId(studentId);
            logger.debug("获取待完成任务列表: 学生ID={}, 加入的班级IDs={}", studentId, classIds);

            if (classIds.isEmpty()) {
                logger.debug("学生未加入任何班级");
                return new ArrayList<>();
            }

            // 使用AssignmentService获取学生任务
            Map<String, Object> assignmentsData = assignmentService.getStudentAssignmentsWithStatus(classIds, studentId,
                    null);
            Object assignmentsObj2 = assignmentsData.get("assignments");
            List<Map<String, Object>> assignments = (assignmentsObj2 instanceof List)
                    ? (List<Map<String, Object>>) assignmentsObj2 : null;

            logger.debug("获取到的任务数量: {}", assignments != null ? assignments.size() : 0);

            if (assignments == null || assignments.isEmpty()) {
                logger.debug("没有找到任何任务");
                return new ArrayList<>();
            }

            // 过滤未过期且未完成的任务并按截止时间排序
            List<Map<String, Object>> pendingAssignments = assignments.stream()
                    .filter(assignment -> {
                        // 首先检查任务状态和进度，过滤已完成的任务（这是最重要的判断）
                        Object status = assignment.get("status");
                        Object progressObj = assignment.get("progress");

                        // 优先使用状态判断，如果状态是completed，则过滤掉
                        if (status != null) {
                            String statusString = status.toString().trim();
                            if ("completed".equalsIgnoreCase(statusString)) {
                                logger.debug("任务 {} 已完成（状态={}），不包含在待办列表中", assignment.get("title"), statusString);
                                return false;
                            }
                        }

                        // 如果没有状态字段或状态不是completed，使用进度判断（进度>=100视为完成）
                        if (progressObj != null) {
                            try {
                                // 处理不同类型的进度值（Integer, Long, Double, String等）
                                double progress;
                                if (progressObj instanceof Number) {
                                    progress = ((Number) progressObj).doubleValue();
                                } else {
                                    progress = Double.parseDouble(progressObj.toString());
                                }
                                if (progress >= 100.0) {
                                    logger.debug("任务 {} 已完成（进度={}>=100），不包含在待办列表中", assignment.get("title"), progress);
                                    return false;
                                }
                            } catch (NumberFormatException e) {
                                logger.warn("任务 {} 进度解析失败: {}, 错误: {}", assignment.get("title"), progressObj,
                                        e.getMessage());
                                // 解析失败，继续使用其他判断
                            }
                        }

                        // 如果任务未完成，再检查是否过期
                        Object deadline = assignment.get("deadline");
                        if (deadline == null) {
                            logger.debug("任务 {} 无截止时间，包含在待办列表中", assignment.get("title"));
                            return true;
                        }
                        try {
                            LocalDateTime deadlineTime = LocalDateTime.parse(deadline.toString());
                            boolean isNotExpired = LocalDateTime.now().isBefore(deadlineTime);
                            logger.debug("任务 {} 截止时间: {}, 是否未过期: {}", assignment.get("title"), deadlineTime,
                                    isNotExpired);
                            return isNotExpired;
                        } catch (Exception e) {
                            logger.warn("解析任务截止时间失败: {}, 错误: {}, 包含在待办列表中", deadline, e.getMessage());
                            return true; // 如果解析失败，算作未过期
                        }
                    })
                    .sorted((a, b) -> {
                        try {
                            LocalDateTime deadlineA = a.get("deadline") != null
                                    ? LocalDateTime.parse(a.get("deadline").toString())
                                    : LocalDateTime.MAX;
                            LocalDateTime deadlineB = b.get("deadline") != null
                                    ? LocalDateTime.parse(b.get("deadline").toString())
                                    : LocalDateTime.MAX;
                            return deadlineA.compareTo(deadlineB);
                        } catch (Exception e) {
                            return 0; // 排序失败时保持原顺序
                        }
                    })
                    .limit(10)
                    .collect(Collectors.toList());

            logger.debug("最终返回的待办任务数量: {}", pendingAssignments.size());
            return pendingAssignments;
        } catch (Exception e) {
            logger.error("获取待完成任务列表失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取学习推荐 - 从学生课程中随机推荐内容
     */
    private Map<String, Object> getLearningRecommendation(Long studentId) {
        try {
            // 获取学生的课程列表
            List<Map<String, Object>> studentCourses = studentClassesService.getStudentCoursesWithProgress(studentId);
            if (studentCourses == null || studentCourses.isEmpty()) {
                return new HashMap<>();
            }

            // 随机选择一个课程
            Random random = new Random();
            Map<String, Object> randomCourse = studentCourses.get(random.nextInt(studentCourses.size()));
            Object courseIdObj = randomCourse.get("courseId");
            if (courseIdObj == null) {
                return new HashMap<>(); // 课程数据不完整，返回空 map 而非 null
            }
            
            Long courseId;
            try {
                courseId = Long.valueOf(courseIdObj.toString());
            } catch (NumberFormatException e) {
                logger.warn("courseId格式错误: {}", courseIdObj);
                return new HashMap<>();
            }

            // 随机选择推荐类型：单词或课文
            boolean recommendWord = random.nextBoolean();

            Map<String, Object> recommendation = new HashMap<>();
            recommendation.put("type", recommendWord ? "word" : "text");
            recommendation.put("courseId", courseId);
            recommendation.put("courseName", randomCourse.get("courseName"));

            if (recommendWord) {
                // 推荐单词：从课程中随机选择一个单词
                return getRecommendedWord(courseId, recommendation);
            } else {
                // 推荐课文：从课程中随机选择一篇课文
                return getRecommendedText(courseId, recommendation);
            }

        } catch (Exception e) {
            logger.error("获取学习推荐失败", e);
            return new HashMap<>();
        }
    }

    /**
     * 获取推荐的单词
     */
    private Map<String, Object> getRecommendedWord(Long courseId, Map<String, Object> recommendation) {
        try {
            // 查询课程下的所有单元
            QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
            unitWrapper.eq("course_id", courseId);
            List<Unit> units = unitMapper.selectList(unitWrapper);
            if (units == null) units = new ArrayList<>();

            if (units.isEmpty()) {
                return new HashMap<>(recommendation);
            }

            // 随机选择一个单元
            Random random = new Random();
            Unit randomUnit = units.get(random.nextInt(units.size()));

            // 查询单元下的单词
            QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
            unitWordWrapper.eq("unit_id", randomUnit.getId());
            List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);
            if (unitWords == null) unitWords = new ArrayList<>();

            if (unitWords.isEmpty()) {
                return new HashMap<>(recommendation);
            }

            // 随机选择一个单词
            UnitWord randomUnitWord = unitWords.get(random.nextInt(unitWords.size()));
            Word word = wordMapper.selectById(randomUnitWord.getWordId());

            if (word == null) {
                return new HashMap<>(recommendation);
            }
            // 过滤已删除的单词（Word 无 @TableLogic，需手动检查）
            if (word.getDeleted() != null && word.getDeleted() == 1) {
                return new HashMap<>(recommendation);
            }

            // 构建推荐数据
            recommendation.put("id", word.getId());
            recommendation.put("title", word.getWord());
            recommendation.put("content", word.getDefinition());
            recommendation.put("pronunciation", word.getPronunciation());
            recommendation.put("unitId", randomUnit.getId());
            recommendation.put("unitName", randomUnit.getName());

            return recommendation;

        } catch (Exception e) {
            logger.error("获取推荐单词失败", e);
            return new HashMap<>(recommendation);
        }
    }

    /**
     * 获取推荐的课文
     */
    private Map<String, Object> getRecommendedText(Long courseId, Map<String, Object> recommendation) {
        try {
            // 查询课程下的所有单元
            QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
            unitWrapper.eq("course_id", courseId);
            List<Unit> units = unitMapper.selectList(unitWrapper);
            if (units == null) units = new ArrayList<>();

            if (units.isEmpty()) {
                return new HashMap<>(recommendation);
            }

            // 随机选择一个单元
            Random random = new Random();
            Unit randomUnit = units.get(random.nextInt(units.size()));

            // 查询单元下的课文
            QueryWrapper<Text> textWrapper = new QueryWrapper<>();
            textWrapper.eq("unit_id", randomUnit.getId());
            textWrapper.eq("deleted", 0);
            List<Text> texts = textMapper.selectList(textWrapper);
            if (texts == null) texts = new ArrayList<>();

            if (texts.isEmpty()) {
                return new HashMap<>(recommendation);
            }

            // 随机选择一篇课文
            Random textRandom = new Random();
            Text randomText = texts.get(textRandom.nextInt(texts.size()));

            // 构建推荐数据
            recommendation.put("id", randomText.getId());
            recommendation.put("title", randomText.getTitle());
            String content = randomText.getContent() != null ? randomText.getContent() : "";
            recommendation.put("content", content.length() > 100 ? content.substring(0, 100) + "..." : content);
            recommendation.put("unitId", randomUnit.getId());
            recommendation.put("unitName", randomUnit.getName());

            return recommendation;

        } catch (Exception e) {
            logger.error("获取推荐课文失败", e);
            return new HashMap<>(recommendation);
        }
    }

    // ==================== 新增的生词本相关API ====================

    /**
     * 获取生词本（带分页和筛选）
     */
    @GetMapping("/vocabulary")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> getVocabulary(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String masteryLevel,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String difficulty,
            Authentication authentication) {
        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            // 关键词长度限制
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("keyword", keyword);
            params.put("masteryLevel", masteryLevel);
            params.put("sortBy", sortBy);
            params.put("difficulty", difficulty);
            params.put("studentId", studentId);

            Map<String, Object> result = vocabularyBooksService.getVocabularyWithPagination(params);
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取生词本失败", e);

            return Result.error("获取生词本失败，请稍后重试");
        }
    }

    /**
     * 检查单词是否在生词本中
     */
    @GetMapping("/vocabulary/check")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Boolean> checkVocabularyStatus(
            @RequestParam String word,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            // 查询单词ID
            QueryWrapper<Word> wordWrapper = new QueryWrapper<>();
            wordWrapper.eq("word", word.toLowerCase());
            wordWrapper.eq("deleted", 0);
            Word wordEntity = wordMapper.selectOne(wordWrapper);

            if (wordEntity == null) {
                return Result.success(false);
            }

            boolean exists = vocabularyBooksService.existsByStudentAndWord(studentId, wordEntity.getId());
            return Result.success(exists);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("检查生词本状态失败", e);

            return Result.error("检查生词本状态失败，请稍后重试");
        }
    }

    /**
     * 通过单词添加到生词本（支持WordNet词汇）
     */
    @PostMapping("/vocabulary")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<String> addToVocabularyByWord(@RequestBody Map<String, Object> params,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            Object wordObj = params.get("word");
            if (wordObj == null) {
                return Result.error("word不能为空");
            }
            String word = wordObj.toString().toLowerCase().trim();
            if (word.isEmpty()) {
                return Result.error("word不能为空");
            }
            if (word.length() > 100) {
                return Result.error("单词长度不能超过100字符");
            }
            
            // 参数转换：unitId
            Long unitId = null;
            if (params.get("unitId") != null) {
                try {
                    unitId = Long.valueOf(params.get("unitId").toString());
                } catch (NumberFormatException e) {
                    return Result.error("unitId格式错误");
                }
            }

            // 查询单词是否存在
            QueryWrapper<Word> wordWrapper = new QueryWrapper<>();
            wordWrapper.eq("word", word);
            wordWrapper.eq("deleted", 0);
            Word wordEntity = wordMapper.selectOne(wordWrapper);

            if (wordEntity == null) {
                // 如果数据库中没有该单词，尝试从WordNet/ECDICT获取并自动创建
                Map<String, Object> wordNetInfo = wordNetService.getOrCreateWord(word);
                if (Boolean.TRUE.equals(wordNetInfo.get("found"))) {
                    // 优先使用 getOrCreateWord 返回的 wordId（避免重复查询和 deleted 过滤遗漏）
                    Object wordIdObj = wordNetInfo.get("wordId");
                    if (wordIdObj != null) {
                        try {
                            Long createdWordId = Long.valueOf(wordIdObj.toString());
                            wordEntity = wordMapper.selectById(createdWordId);
                        } catch (NumberFormatException e) {
                            logger.warn("getOrCreateWord 返回的 wordId 格式无效: {}", wordIdObj);
                        }
                    }
                    // 兜底：如果 wordId 不可用，按 word + deleted=0 重新查询
                    if (wordEntity == null) {
                        wordWrapper = new QueryWrapper<>();
                        wordWrapper.eq("word", word);
                        wordWrapper.eq("deleted", 0);
                        wordEntity = wordMapper.selectOne(wordWrapper);
                    }
                }

                if (wordEntity == null) {
                    logger.warn("单词 [{}] 无法在数据库中找到或创建", word);
                    return Result.error("单词不存在，无法添加到生词本");
                }
            }

            // 检查是否已在生词本中
            if (vocabularyBooksService.existsByStudentAndWord(studentId, wordEntity.getId())) {
                return Result.success("单词已在生词本中");
            }

            // 添加到生词本，初始状态为 NEW
            vocabularyBooksService.addToVocabularyWithLevel(studentId, wordEntity.getId(), "NEW");

            return Result.success("单词已添加到生词本");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("添加到生词本失败", e);

            return Result.error("添加到生词本失败，请稍后重试");
        }
    }

    /**
     * 从生词本移除单词
     */
    @DeleteMapping("/vocabulary/{wordId}")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<String> removeFromVocabulary(@PathVariable Long wordId, Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            vocabularyBooksService.removeFromVocabulary(studentId, wordId);
            return Result.success("单词已从生词本移除");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("移除生词失败", e);

            return Result.error("移除生词失败，请稍后重试");
        }
    }

    /**
     * 更新单词掌握程度
     */
    @PutMapping("/vocabulary/{wordId}/mastery")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<String> updateMasteryLevel(
            @PathVariable Long wordId,
            @RequestBody Map<String, Object> params,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            Object masteryLevelObj = params.get("masteryLevel");
            String masteryLevel = masteryLevelObj != null ? masteryLevelObj.toString() : null;

            // 验证masteryLevel枚举值
            if (masteryLevel == null || !java.util.Set.of("NEW", "LEARNING", "FAMILIAR", "MASTERED").contains(masteryLevel)) {
                return Result.error("无效的掌握程度");
            }

            vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, params);
            return Result.success("掌握程度已更新");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新掌握程度失败", e);

            return Result.error("更新掌握程度失败，请稍后重试");
        }
    }

    /**
     * 获取生词本统计信息
     */
    @GetMapping("/vocabulary/stats")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> getVocabularyStats(Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            Map<String, Object> stats = vocabularyBooksService.getVocabularyStats(studentId);
            return Result.success(stats);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取生词本统计失败", e);

            return Result.error("获取生词本统计失败，请稍后重试");
        }
    }

    /**
     * 标记单词为已完成
     */
    @PostMapping("/words/{wordId}/complete")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> markWordAsCompleted(
            @PathVariable Long wordId,
            @RequestParam(required = false) Long assignmentId,
            @RequestParam(required = false, defaultValue = "30") Integer duration,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            // 学习时长校验：不允许负数，上限3小时
            if (duration != null && duration < 0) return Result.error("学习时长不能为负数");
            if (duration != null && duration > 10800) return Result.error("学习时长超过合理范围");

            // 验证单词是否存在
            Word word = wordMapper.selectById(wordId);
            if (word == null || (word.getDeleted() != null && word.getDeleted() == 1)) {
                return Result.error("单词不存在");
            }

            // 记录完成单词的学习记录，支持任务关联
            learningRecordsService.recordWordView(studentId, wordId);

            // 如果有任务ID，记录任务关联的学习活动
            if (assignmentId != null) {
                learningRecordsService.recordLearning(studentId, "WORD_COMPLETE", wordId, duration, null, assignmentId);
            }

            logger.debug("单词完成标记 - 学生={}, 单词ID={}{}, 时长={}秒",
                    studentId, wordId,
                    assignmentId != null ? ", 任务ID=" + assignmentId : "",
                    duration);

            Map<String, Object> result = new HashMap<>();
            result.put("message", "单词已标记为完成");

            // 如果有任务ID，返回更新后的任务进度
            if (assignmentId != null) {
                Map<String, Object> progress = assignmentService.getAssignmentProgress(assignmentId, studentId);
                if (progress != null) {
                    result.put("progress", progress);
                    Object completionRateObj = progress.get("completionRate");
                    result.put("completionRate", completionRateObj != null ? completionRateObj : 0.0);

                    logger.debug("任务进度更新 - 任务ID={}, 完成率={}%", assignmentId, completionRateObj != null ? completionRateObj : 0.0);
                }
            }

            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("标记单词完成失败", e);

            return Result.error("标记单词完成失败，请稍后重试");
        }
    }

    // ==================== 文章收藏 ====================

    /**
     * 收藏/取消收藏文章（toggle）
     */
    @PostMapping("/favorites/{textId}")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> toggleFavorite(@PathVariable Long textId, Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            if (textId == null || textId <= 0) {
                return Result.error("文章ID无效");
            }

            // 权限校验：首先确保学生能访问这篇课文
            assertStudentCanRecord(studentId, textId, null, null);

            // 业务校验：只有公开文章或系统课程文章可收藏，禁止收藏个人非公开上传
            Text text = textMapper.selectById(textId);
            if (text == null) return Result.error("文章不存在");
            
            boolean isPublic = (text.getIsPublic() != null && text.getIsPublic() == 1);
            boolean isFeatured = (text.getIsFeatured() != null && text.getIsFeatured() == 1);
            boolean isCourseText = (text.getUnitId() != null && text.getUnitId() != 999);
            
            if (!isPublic && !isFeatured && !isCourseText) {
                return Result.error("该类型的文章暂不支持收藏");
            }

            QueryWrapper<ArticleFavorite> wrapper = new QueryWrapper<>();
            wrapper.eq("student_id", studentId).eq("text_id", textId);
            ArticleFavorite existing = articleFavoriteMapper.selectOne(wrapper);

            Map<String, Object> result = new HashMap<>();
            if (existing != null) {
                articleFavoriteMapper.deleteById(existing.getId());
                result.put("isFavorite", false);
                result.put("message", "已取消收藏");
            } else {
                ArticleFavorite fav = new ArticleFavorite();
                fav.setStudentId(studentId);
                fav.setTextId(textId);
                fav.setCreatedTime(LocalDateTime.now());
                articleFavoriteMapper.insert(fav);
                result.put("isFavorite", true);
                result.put("message", "已添加到收藏");
            }
            return Result.success(result);
        } catch (Exception e) {
            logger.error("收藏操作失败", e);
            return Result.error("收藏操作失败，请稍后重试");
        }
    }

    /**
     * 检查文章是否已收藏
     */
    @GetMapping("/favorites/{textId}/check")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> checkFavorite(@PathVariable Long textId, Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            QueryWrapper<ArticleFavorite> wrapper = new QueryWrapper<>();
            wrapper.eq("student_id", studentId).eq("text_id", textId);
            Map<String, Object> result = new HashMap<>();
            result.put("isFavorite", articleFavoriteMapper.selectCount(wrapper) > 0);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("检查收藏状态失败", e);
            return Result.error("检查收藏状态失败");
        }
    }

    /**
     * 获取收藏文章列表
     */
    @GetMapping("/favorites")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> getFavoriteArticles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 50));

            QueryWrapper<ArticleFavorite> wrapper = new QueryWrapper<>();
            wrapper.eq("student_id", studentId).orderByDesc("created_time");

            com.baomidou.mybatisplus.extension.plugins.pagination.Page<ArticleFavorite> pageParam =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<ArticleFavorite> favPage =
                    articleFavoriteMapper.selectPage(pageParam, wrapper);

            List<Map<String, Object>> records = new ArrayList<>();
            for (ArticleFavorite fav : favPage.getRecords()) {
                Text text = textMapper.selectById(fav.getTextId());
                if (text == null || (text.getDeleted() != null && text.getDeleted() == 1)) continue;

                Map<String, Object> item = new HashMap<>();
                item.put("id", fav.getId());
                item.put("textId", text.getId());
                item.put("title", text.getTitle());
                item.put("difficulty", text.getDifficulty());
                item.put("contentType", text.getContentType());
                item.put("favoritedAt", fav.getCreatedTime());
                // 计算阅读时长
                String snippet = text.getContent();
                if (snippet != null && snippet.length() > 120) {
                    snippet = snippet.substring(0, 120) + "...";
                }
                item.put("snippet", snippet);
                item.put("wordCount", text.getContent() != null ? text.getContent().split("\\s+").length : 0);
                records.add(item);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("records", records);
            result.put("total", favPage.getTotal());
            result.put("current", favPage.getCurrent());
            result.put("pages", favPage.getPages());
            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取收藏列表失败", e);
            return Result.error("获取收藏列表失败，请稍后重试");
        }
    }

    // ==================== 阅读记录统计 ====================

    /**
     * 获取阅读记录列表（含时长统计）
     */
    @GetMapping("/reading-history")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> getReadingHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 50));

            QueryWrapper<TextLearningRecord> wrapper = new QueryWrapper<>();
            wrapper.eq("student_id", studentId).orderByDesc("start_time");

            com.baomidou.mybatisplus.extension.plugins.pagination.Page<TextLearningRecord> pageParam =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<TextLearningRecord> recordPage =
                    textLearningRecordMapper.selectPage(pageParam, wrapper);

            // 去重：同一篇文章只取最新一条记录，并累计时长
            Map<Long, Map<String, Object>> textRecordMap = new LinkedHashMap<>();
            for (TextLearningRecord record : recordPage.getRecords()) {
                Long textId = record.getTextId();
                Map<String, Object> existing = textRecordMap.get(textId);
                if (existing == null) {
                    Text text = textMapper.selectById(textId);
                    if (text == null) continue;

                    Map<String, Object> item = new HashMap<>();
                    item.put("textId", textId);
                    item.put("title", text.getTitle());
                    item.put("difficulty", text.getDifficulty());
                    item.put("totalDuration", record.getDurationSeconds() != null ? record.getDurationSeconds() : 0);
                    item.put("isCompleted", record.getIsCompleted() != null && record.getIsCompleted() == 1);
                    item.put("lastReadAt", record.getStartTime());
                    item.put("readCount", 1);
                    textRecordMap.put(textId, item);
                } else {
                    int prevDuration = (int) existing.getOrDefault("totalDuration", 0);
                    existing.put("totalDuration", prevDuration + (record.getDurationSeconds() != null ? record.getDurationSeconds() : 0));
                    existing.put("readCount", (int) existing.getOrDefault("readCount", 0) + 1);
                    if (record.getIsCompleted() != null && record.getIsCompleted() == 1) {
                        existing.put("isCompleted", true);
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("records", new ArrayList<>(textRecordMap.values()));
            result.put("total", recordPage.getTotal());

            // 阅读统计概览
            QueryWrapper<TextLearningRecord> statsWrapper = new QueryWrapper<>();
            statsWrapper.eq("student_id", studentId);
            long totalRecords = textLearningRecordMapper.selectCount(statsWrapper);

            statsWrapper = new QueryWrapper<>();
            statsWrapper.eq("student_id", studentId).eq("is_completed", 1);
            long completedCount = textLearningRecordMapper.selectCount(statsWrapper);

            QueryWrapper<ArticleFavorite> favWrapper = new QueryWrapper<>();
            favWrapper.eq("student_id", studentId);
            long favoriteCount = articleFavoriteMapper.selectCount(favWrapper);

            Map<String, Object> overview = new HashMap<>();
            overview.put("totalArticlesRead", totalRecords);
            overview.put("completedArticles", completedCount);
            overview.put("favoriteCount", favoriteCount);
            result.put("overview", overview);

            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取阅读记录失败", e);
            return Result.error("获取阅读记录失败，请稍后重试");
        }
    }

}