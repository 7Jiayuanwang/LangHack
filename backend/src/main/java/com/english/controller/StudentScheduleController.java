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
     * GET /api/student/schedule?academicYear=2025-2026&semester=1&refresh=true
     * <p>
     * refresh=true 时从学校API重新拉取并同步，否则直接查询本地数据
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<List<StudentSchedule>> getMySchedule(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false, defaultValue = "false") boolean refresh,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            com.english.entity.User user = userService.getUserByUsername(username);
            if (user == null) {
                return Result.error("用户信息不存在");
            }

            // username 即为学号
            String studentId = user.getUsername();

            List<StudentSchedule> schedule;
            if (refresh) {
                // 从学校API拉取并同步
                schedule = syncService.syncStudentSchedule(studentId, academicYear, semester);
            } else {
                // 直接查询本地数据
                schedule = syncService.getStudentSchedule(studentId, academicYear, semester);
            }

            return Result.success(schedule);
        } catch (Exception e) {
            logger.error("获取学生课表失败", e);
            return Result.error("获取课表失败，请稍后重试");
        }
    }
}
