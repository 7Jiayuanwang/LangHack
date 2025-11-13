//任务（作业）管理控制器
//还包含教师专属的统计和班级相关功能

package com.english.controller;

import com.english.dto.Result;
import com.english.entity.Assignment;
import com.english.service.AssignmentService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    @Autowired
    private AssignmentService assignmentService;
    // 注入任务相关业务处理服务

    @Autowired
    private UserService userService;
    // 注入用户相关服务（用于获取当前登录用户信息）

    /**
     * 分页查询任务列表
     */
    // 业务逻辑：
    // 1. 封装分页、筛选参数到map中
    // 2. 关键权限控制：如果是教师（ROLE_TEACHER），只能查询自己创建的任务（添加teacherId筛选）
    // 3. 管理员不受限制，能查看所有任务
    // 4. 调用assignmentService分页查询，返回结果（总条数、当前页任务列表等
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> getAssignments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long classId,// 可选参数：按班级ID筛选任务
            @RequestParam(required = false) String keyword,// 可选参数：按关键词（如任务名称）搜索
            Authentication authentication) {

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("classId", classId);
            params.put("keyword", keyword);

            // 教师只能查看自己创建的任务
            String userRole = authentication.getAuthorities().iterator().next().getAuthority();
            if ("ROLE_TEACHER".equals(userRole)) {
                Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
                params.put("teacherId", teacherId);
            }

            Map<String, Object> result = assignmentService.getAssignmentsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据ID获取任务详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Assignment> getAssignmentById(@PathVariable Long id, Authentication authentication) {
        try {
            String userRole = authentication.getAuthorities().iterator().next().getAuthority();

            // 非管理员只能查看自己创建的任务
            if (!"ROLE_ADMIN".equals(userRole)) {
                Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
                if (!assignmentService.validateTeacherPermission(teacherId, id)) {
                    return Result.error("没有权限查看此任务");
                }
            }

            Assignment assignment = assignmentService.getAssignmentById(id);
            if (assignment == null) {
                return Result.error("任务不存在");
            }
            return Result.success(assignment);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 创建任务
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> createAssignment(@RequestBody Assignment assignment, Authentication authentication) {
        try {
            // 教师只能创建自己管理的班级的任务
            if ("ROLE_TEACHER".equals(authentication.getAuthorities().iterator().next().getAuthority())) {
                Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
                assignment.setCreatedBy(teacherId);
            }

            assignmentService.createAssignment(assignment);
            return Result.success("任务创建成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
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
            if (!"ROLE_ADMIN".equals(authentication.getAuthorities().iterator().next().getAuthority())) {
                Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
                if (!assignmentService.validateTeacherPermission(teacherId, id)) {
                    return Result.error("没有权限修改此任务");
                }
                // 教师不能修改任务的负责人
                assignment.setCreatedBy(teacherId);
            }

            assignmentService.updateAssignment(assignment);
            return Result.success("任务更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
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
            if (!"ROLE_ADMIN".equals(authentication.getAuthorities().iterator().next().getAuthority())) {
                Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
                if (!assignmentService.validateTeacherPermission(teacherId, id)) {
                    return Result.error("没有权限删除此任务");
                }
            }

            assignmentService.deleteAssignment(id);
            return Result.success("任务删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

  
    /**
     * 获取教师统计信息（用于仪表板）
     */
    @GetMapping("/teacher/stats")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Map<String, Object>> getTeacherStats(Authentication authentication) {
        try {
            Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
            Map<String, Object> stats = assignmentService.getTeacherStats(teacherId);
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取教师的班级列表（用于仪表板）
     */
    @GetMapping("/teacher/classes")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<List<Map<String, Object>>> getTeacherClasses(Authentication authentication) {
        try {
            Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
            List<Map<String, Object>> classes = assignmentService.getTeacherClassesWithStats(teacherId);
            return Result.success(classes);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取教师的最近任务列表（用于仪表板）
     */
    @GetMapping("/teacher/recent")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<List<Assignment>> getRecentAssignments(
            @RequestParam(defaultValue = "5") int limit,
            Authentication authentication) {
        try {
            Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
            List<Assignment> assignments = assignmentService.getRecentAssignments(teacherId, limit);
            return Result.success(assignments);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}