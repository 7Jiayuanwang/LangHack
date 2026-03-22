package com.english.controller;

import com.english.dto.Result;
import com.english.entity.StudentExerciseAnswer;
import com.english.entity.TextExercise;
import com.english.entity.User;
import com.english.entity.Assignment;
import com.english.exception.BusinessException;
import com.english.service.AssignmentService;
import com.english.service.StudentClassesService;
import com.english.service.StudentExerciseService;
import com.english.service.TextExerciseService;
import com.english.service.UserService;
import com.english.service.ClassService;
import com.english.service.TextService;
import com.english.service.UnitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生习题答题控制器
 */
@RestController
@RequestMapping("/api/student/exercises")
@PreAuthorize("hasAnyRole('STUDENT', 'USER')")
public class StudentExerciseController {

    private static final Logger log = LoggerFactory.getLogger(StudentExerciseController.class);

    @Autowired
    private StudentExerciseService studentExerciseService;

    @Autowired
    private UserService userService;

    @Autowired
    private AssignmentService assignmentsService;

    @Autowired
    private StudentClassesService studentClassesService;

    @Autowired
    private TextExerciseService textExerciseService;

    @Autowired
    private ClassService classService;

    @Autowired
    private TextService textService;

    @Autowired
    private UnitService unitService;

    /**
     * 提交习题答案
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submitAnswer(@RequestBody Map<String, Object> requestData, Authentication authentication) {
        try {
            String username = authentication.getName();
            com.english.entity.User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) throw new BusinessException("用户不存在");
            Long studentId = currentUser.getId();

            StudentExerciseAnswer answer = new StudentExerciseAnswer();
            Object exerciseIdObj = requestData.get("exerciseId");
            Object answerContentObj = requestData.get("answerContent");
            Object timeSpentObj = requestData.get("timeSpentSeconds");
            if (exerciseIdObj == null || answerContentObj == null || timeSpentObj == null) {
                return Result.error("exerciseId、答案内容和用时不能为空");
            }

            // 答案内容长度校验（防止内存溢出）
            String answerContent = answerContentObj.toString();
            if (answerContent.length() > 50000) {
                return Result.error("答案内容过长，不能超过50000字符");
            }

            Long exerciseId = Long.valueOf(exerciseIdObj.toString());

            // BUG FIX #1: 验证习题存在
            TextExercise exercise = textExerciseService.getById(exerciseId);
            if (exercise == null) {
                throw new BusinessException("习题不存在");
            }

            answer.setExerciseId(exerciseId);
            answer.setStudentId(studentId);
            answer.setAnswerContent(answerContent);

            // 学习时长校验（防止整数溢出，限制在15小时内）
            int timeSpentSeconds;
            try {
                timeSpentSeconds = Integer.parseInt(timeSpentObj.toString());
                if (timeSpentSeconds < 0 || timeSpentSeconds > 54000) { // 15小时
                    return Result.error("学习时长不在有效范围(0-54000秒)");
                }
            } catch (NumberFormatException e) {
                return Result.error("用时参数格式错误");
            }
            answer.setTimeSpentSeconds(timeSpentSeconds);

            // 设置答题时间（现在由 Service 层处理，但这里也可以验证）
            // 如果前端没有提供，Service 层会自动设置当前时间

            Long assignmentId = null;
            if (requestData.get("assignmentId") != null) {
                assignmentId = Long.valueOf(requestData.get("assignmentId").toString());
                answer.setAssignmentId(assignmentId);

                // 检查学生是否有权访问此任务
                if (!canAccessAssignment(studentId, assignmentId)) {
                    return Result.error("无权访问此任务");
                }

                // 检查任务是否已截止
                Assignment assignment = assignmentsService.getAssignmentById(assignmentId);
                if (assignment == null) {
                    throw new BusinessException("任务不存在");
                }
                if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
                    throw new BusinessException("任务已截止，无法再提交答案");
                }

                // BUG FIX #2: 验证习题属于任务对应的文本
                java.util.List<Long> assignmentTextIds = assignment.getTextIds();
                if (exercise.getTextId() == null || assignmentTextIds == null || assignmentTextIds.isEmpty()
                        || !assignmentTextIds.contains(exercise.getTextId())) {
                    throw new BusinessException("习题不属于此任务");
                }
            } else {
                // 无 assignmentId 时，校验学生是否有权访问习题对应的课文
                if (exercise.getTextId() != null) {
                    assertStudentCanAccessText(studentId, exercise.getTextId());
                }
            }

            Map<String, Object> result = studentExerciseService.submitAnswer(answer);
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("提交答案失败", e);
            return Result.error("提交答案失败，请稍后重试");
        }
    }

    /**
     * 获取学生的课文答题记录
     */
    @GetMapping("/text/{textId}")
    public Result<List<StudentExerciseAnswer>> getStudentAnswersByText(
            @PathVariable Long textId,
            @RequestParam(required = false) Long assignmentId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            com.english.entity.User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) throw new com.english.exception.BusinessException("用户不存在");
            Long studentId = currentUser.getId();

            // 如果指定了任务ID，检查学生是否有权访问此任务
            if (assignmentId != null && !canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            List<StudentExerciseAnswer> answers = studentExerciseService.getStudentAnswersByText(
                studentId, textId, assignmentId);
            return Result.success(answers);
        } catch (com.english.exception.BusinessException e) {
            log.error("获取答题记录失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取答题记录失败", e);
            return Result.error("获取答题记录失败，请稍后重试");
        }
    }

    /**
     * 获取学生的任务答题记录
     */
    @GetMapping("/assignment/{assignmentId}")
    public Result<List<StudentExerciseAnswer>> getStudentAnswersByAssignment(@PathVariable Long assignmentId, Authentication authentication) {
        try {
            String username = authentication.getName();
            com.english.entity.User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) throw new com.english.exception.BusinessException("用户不存在");
            Long studentId = currentUser.getId();

            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            List<StudentExerciseAnswer> answers = studentExerciseService.getStudentAnswersByAssignment(
                studentId, assignmentId);
            return Result.success(answers);
        } catch (com.english.exception.BusinessException e) {
            log.error("获取任务答题记录失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取任务答题记录失败", e);
            return Result.error("获取任务答题记录失败，请稍后重试");
        }
    }

    /**
     * 获取学生的任务答题统计
     */
    @GetMapping("/assignment/{assignmentId}/stats")
    public Result<Map<String, Object>> getStudentAnswerStats(@PathVariable Long assignmentId, Authentication authentication) {
        try {
            String username = authentication.getName();
            com.english.entity.User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) throw new com.english.exception.BusinessException("用户不存在");
            Long studentId = currentUser.getId();

            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            Map<String, Object> stats = studentExerciseService.getStudentAnswerStats(studentId, assignmentId);
            return Result.success(stats);
        } catch (com.english.exception.BusinessException e) {
            log.error("获取答题统计失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取答题统计失败", e);
            return Result.error("获取答题统计失败，请稍后重试");
        }
    }

    /**
     * 检查课文习题完成状态
     */
    @GetMapping("/text/{textId}/completed")
    public Result<Map<String, Object>> checkTextExerciseCompletion(
            @PathVariable Long textId,
            @RequestParam(required = false) Long assignmentId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            com.english.entity.User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) throw new com.english.exception.BusinessException("用户不存在");
            Long studentId = currentUser.getId();

            Map<String, Object> result = studentExerciseService.getTextExerciseProgress(
                studentId, textId, assignmentId);
            return Result.success(result);
        } catch (com.english.exception.BusinessException e) {
            log.error("检查习题完成状态失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("检查习题完成状态失败", e);
            return Result.error("检查习题完成状态失败，请稍后重试");
        }
    }

    /**
     * 检查学生是否可以访问任务
     */
    private boolean canAccessAssignment(Long studentId, Long assignmentId) {
        try {
            java.util.List<Long> studentClassIds = studentClassesService.getClassIdsByStudentId(studentId);
            return assignmentsService.isAssignmentInClasses(assignmentId, studentClassIds);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验学生是否有权访问指定课文（通过班级->课程->单元->课文的归属链）
     */
    private void assertStudentCanAccessText(Long studentId, Long textId) {
        com.english.entity.Text text = textService.getTextById(textId);
        if (text == null) {
            throw new BusinessException("课文不存在");
        }
        if (text.getUnitId() != null) {
            com.english.entity.Unit unit = unitService.getUnitById(text.getUnitId());
            if (unit != null && unit.getCourseId() != null) {
                List<Long> classIds = studentClassesService.getClassIdsByStudentId(studentId);
                boolean hasAccess = false;
                if (classIds != null) {
                    for (Long classId : classIds) {
                        com.english.entity.Class classInfo = classService.getClassById(classId);
                        if (classInfo != null && unit.getCourseId().equals(classInfo.getCourseId())) {
                            hasAccess = true;
                            break;
                        }
                    }
                }
                if (!hasAccess) {
                    throw new BusinessException("无权访问该课文的习题");
                }
            }
        }
    }
}