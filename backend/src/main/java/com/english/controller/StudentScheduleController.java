package com.english.controller;

import com.english.dto.Result;
import com.english.entity.StudentSchedule;
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
 * 学生课表控制器
 * <p>
 * 学生登录后查看自己的课表，支持从学校API同步数据到本地
 */
@RestController
@RequestMapping("/api/student/schedule")
public class StudentScheduleController {

    private static final Logger logger = LoggerFactory.getLogger(StudentScheduleController.class);

    @Autowired
    private SchoolDataSyncService syncService;

    @Autowired
    private UserService userService;

    /**
     * 获取当前登录学生的课表
     * GET /api/student/schedule?academicYear=2025&semester=秋&refresh=true
     * <p>
     * academicYear: CDSP 学年起始年份，如 "2025"（对应 2025-2026 学年）
     * semester: "秋"（第一学期）或 "春"（第二学期），省略则返回该学年全部课表
     * refresh=true 强制从学校API重新拉取；否则使用 Lazy 策略（陈旧度阈值6小时）
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<List<StudentSchedule>> getMySchedule(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false, defaultValue = "false") boolean refresh,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            com.english.entity.User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户信息不存在");
            }

            String studentId = user.getUsername();
            Integer semesterInt = toIntSemester(semester);
            Duration staleness = refresh ? Duration.ZERO : Duration.ofHours(6);
            List<StudentSchedule> schedule = syncService.getOrSyncStudentSchedule(
                    studentId, academicYear, semesterInt, staleness);

            return Result.success(schedule);
        } catch (Exception e) {
            logger.error("获取学生课表失败", e);
            return Result.error("获取课表失败，请稍后重试");
        }
    }

    private Integer toIntSemester(String semester) {
        if (semester == null || semester.isEmpty()) return null;
        if ("秋".equals(semester) || "1".equals(semester)) return 1;
        if ("春".equals(semester) || "2".equals(semester)) return 2;
        try { return Integer.parseInt(semester); } catch (NumberFormatException e) { return null; }
    }
}
