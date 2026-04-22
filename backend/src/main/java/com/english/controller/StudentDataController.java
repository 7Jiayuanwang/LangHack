package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.Result;
import com.english.entity.CourseSelection;
import com.english.entity.LevelExamScore;
import com.english.entity.StudentSchedule;
import com.english.mapper.CourseSelectionMapper;
import com.english.mapper.LevelExamScoreMapper;
import com.english.service.SchoolDataSyncService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/**
 * 学生个人数据查询控制器
 * <p>
 * 学生登录后查看自己的等级考试成绩和选课记录
 */
@RestController
@RequestMapping("/api/student")
public class StudentDataController {

    private static final Logger logger = LoggerFactory.getLogger(StudentDataController.class);

    @Autowired
    private LevelExamScoreMapper levelExamScoreMapper;

    @Autowired
    private CourseSelectionMapper courseSelectionMapper;

    @Autowired
    private SchoolDataSyncService syncService;

    @Autowired
    private UserService userService;

    /**
     * 获取当前学生的等级考试成绩
     * GET /api/student/scores?examType=CET4
     */
    @GetMapping("/scores")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<List<LevelExamScore>> getMyScores(
            @RequestParam(required = false) String examType,
            Authentication authentication) {
        try {
            String studentId = getStudentId(authentication);
            QueryWrapper<LevelExamScore> wrapper = new QueryWrapper<>();
            wrapper.eq("student_id", studentId);
            if (examType != null && !examType.isEmpty()) {
                wrapper.eq("exam_type", examType);
            }
            wrapper.orderByDesc("exam_date");
            return Result.success(levelExamScoreMapper.selectList(wrapper));
        } catch (Exception e) {
            logger.error("获取学生成绩失败", e);
            return Result.error("获取成绩失败，请稍后重试");
        }
    }

    /**
     * 获取当前学生的选课记录
     * GET /api/student/course-selections?academicYear=2025-2026&semester=1
     */
    @GetMapping("/course-selections")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<List<CourseSelection>> getMyCourseSelections(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            Authentication authentication) {
        try {
            String studentId = getStudentId(authentication);
            QueryWrapper<CourseSelection> wrapper = new QueryWrapper<>();
            wrapper.eq("student_id", studentId);
            if (academicYear != null && !academicYear.isEmpty()) {
                wrapper.eq("academic_year", academicYear);
            }
            if (semester != null) {
                wrapper.eq("semester", semester);
            }
            wrapper.orderByDesc("academic_year", "semester");
            return Result.success(courseSelectionMapper.selectList(wrapper));
        } catch (Exception e) {
            logger.error("获取选课记录失败", e);
            return Result.error("获取选课记录失败，请稍后重试");
        }
    }

    /**
     * 获取当前学生的等级考试成绩（Lazy 同步）
     * GET /api/student/level-exam-scores
     *
     * 与 /scores 的区别：此端点按需触发 CDSP 拉取并刷新本地数据（陈旧度阈值 1 天）。
     */
    @GetMapping("/level-exam-scores")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<List<LevelExamScore>> getMyLevelExamScores(Authentication authentication) {
        try {
            String studentId = getStudentId(authentication);
            List<LevelExamScore> scores = syncService.getOrSyncLevelExamScores(studentId, Duration.ofDays(1));
            return Result.success(scores);
        } catch (Exception e) {
            logger.error("获取学生四六级成绩失败", e);
            return Result.error("获取成绩失败，请稍后重试");
        }
    }

    private String getStudentId(Authentication authentication) {
        com.english.entity.User user = userService.getUserByUsername(authentication.getName());
        if (user == null) {
            throw new RuntimeException("用户信息不存在");
        }
        return user.getUsername();
    }
}
