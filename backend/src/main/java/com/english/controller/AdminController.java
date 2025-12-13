//管理员专用的控制器

package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.UserService;
import com.english.service.CourseService;
import com.english.service.WordService;
import com.english.service.ClassService;
import com.english.service.impl.DataCleanupServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
//REST接口控制器，返回JSON数据
@RequestMapping("/api/admin")
//所有接口的基础路径，前端访问需以/api/admin开头
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

//注入各种，服务层对象（用户，课程等）
    @Autowired private UserService userService;
    @Autowired private CourseService courseService;
    @Autowired private WordService wordService;
    @Autowired private ClassService classService;
    @Autowired private DataCleanupServiceImpl dataCleanupService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")//仅admin角色可以访问
    public Result<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            //页码默认第一页
            @RequestParam(defaultValue = "10") int size,
            //每页条数，默认十条
            @RequestParam(required = false) String role,
            // 可选参数：按角色筛选（如STUDENT/TEACHER）
            @RequestParam(required = false) String keyword) {
            // 可选参数：按关键词搜索（如用户名）
            //管理员可以分页查看系统中的所有用户，
            // 支持按角色筛选（比如只看教师）或关键词搜索（比如搜用户名包含 “张” 的用户）
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("role", role);
            params.put("keyword", keyword);

            Map<String, Object> result = userService.getUsersByPage(params);

            // 不返回密码
            List<User> users = (List<User>) result.get("records");
            users.forEach(user -> user.setPassword(null));

            return Result.success(result);
        } catch (IllegalArgumentException e) {
            logger.warn("获取用户列表参数错误: {}", e.getMessage());
            return Result.error("参数错误");
        } catch (Exception e) {
            logger.error("获取用户列表失败", e);
            return Result.error("获取用户列表失败，请稍后重试");
        }
    }

    @PostMapping("/users")
    //创建新用户
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> createUser(@RequestBody User user) {
        // 从请求体获取用户信息（用户名、密码、角色等）
        try {
            if (userService.getUserByUsername(user.getUsername()) != null) {
                return Result.error("用户名已存在");
            }

            if (user.getRole() == null || user.getRole().isEmpty()) {
                user.setRole("STUDENT");
            }

            userService.createUser(user);
            return Result.success("用户创建成功");
        } catch (IllegalArgumentException e) {
            logger.warn("创建用户参数错误: {}", e.getMessage());
            return Result.error("参数错误");
        } catch (Exception e) {
            logger.error("创建用户失败", e);
            return Result.error("创建用户失败，请稍后重试");
        }
    }


    // 业务逻辑：
    // 1. 检查用户是否存在（避免更新不存在的用户）
    // 2. 如果请求中没传新密码，保持原密码不变（避免误清空密码）
    // 3. 调用userService更新用户信息（如角色、姓名等）
    @PutMapping("/users/{id}")
    // 接口路径：/api/admin/users/{用户ID}，PUT请求
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateUser(@PathVariable Long id,
                                     //从url路径获取要更新的用户ID
                                     @RequestBody User user) {
                                     //从请求体获取新的用户信息
        try {
            user.setId(id);

            // 如果有新密码，需要加密；否则不更新密码
            User existingUser = userService.getUserById(id);
            if (existingUser == null) {
                return Result.error("用户不存在");
            }

            // 保持原密码，除非明确提供新密码
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                user.setPassword(existingUser.getPassword());
            }

            userService.updateUser(user);
            return Result.success("用户更新成功");
        } catch (IllegalArgumentException e) {
            logger.warn("更新用户参数错误: {}", e.getMessage());
            return Result.error("参数错误");
        } catch (Exception e) {
            logger.error("更新用户失败，用户ID: {}", id, e);
            return Result.error("更新用户失败，请稍后重试");
        }
    }

    // 业务逻辑：
    // 1. 禁止删除ID为1的默认管理员（防止误删超级管理员）
    // 2. 调用userService删除指定用户

    //作用：给前端提供可选的角色列表（比如创建 / 编辑用户时，下拉框展示可选角色）
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteUser(@PathVariable Long id) {
        try {
            if (id == 1) {
                return Result.error("不能删除默认管理员账户");
            }

            userService.deleteUser(id);
            // 返回系统支持的角色：STUDENT（学生）、TEACHER（教师）、ADMIN（管理员）
            return Result.success("用户删除成功");
        } catch (IllegalArgumentException e) {
            logger.warn("删除用户参数错误: {}", e.getMessage());
            return Result.error("参数错误");
        } catch (Exception e) {
            logger.error("删除用户失败，用户ID: {}", id, e);
            return Result.error("删除用户失败，请稍后重试");
        }
    }

    //get请求
    // 业务逻辑：
    // 1. 调用各服务获取统计数据：
    //    - 用户统计（总用户数、各角色用户数等）
    //    - 课程统计（总课程数、已发布/未发布课程数等）
    //    - 单词统计（总单词量、各词库单词数等）
    //    - 班级统计（总班级数、各班级人数等）
    // 2. 汇总所有统计数据返回
    @GetMapping("/users/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<String>> getUserRoles() {
        List<String> roles = List.of("STUDENT", "TEACHER", "ADMIN");
        return Result.success(roles);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 获取用户统计
            Map<String, Object> userStats = userService.getUserStats();
            stats.putAll(userStats);

            // 获取课程统计
            Map<String, Object> courseStats = courseService.getCourseStats();
            stats.putAll(courseStats);

            // 获取单词统计
            Map<String, Object> wordStats = wordService.getWordStats();
            stats.putAll(wordStats);

            // 获取班级统计
            Map<String, Object> classStats = classService.getClassStats();
            stats.putAll(classStats);

            return Result.success(stats);
        } catch (IllegalArgumentException e) {
            logger.warn("获取统计数据参数错误: {}", e.getMessage());
            return Result.error("参数错误");
        } catch (Exception e) {
            logger.error("获取统计数据失败", e);
            return Result.error("获取统计数据失败，请稍后重试");
        }
    }

    /**
     * 清理不一致的数据
     */
    @PostMapping("/cleanup-data")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> cleanupData() {
        try {
            // 手动触发数据清理
            dataCleanupService.cleanupInconsistentData();
            return Result.success("数据清理完成，请查看控制台输出获取详细信息");
        } catch (IllegalStateException e) {
            logger.warn("数据清理失败: {}", e.getMessage());
            return Result.error("数据清理失败: " + e.getMessage());
        } catch (Exception e) {
            logger.error("数据清理失败", e);
            return Result.error("数据清理失败，请稍后重试");
        }
    }

    /**
     * 打印详细数据统计信息
     */
    @GetMapping("/data-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> printDataStatistics() {
        try {
            dataCleanupService.printDataStatistics();
            return Result.success("数据统计信息已打印到控制台");
        } catch (IllegalStateException e) {
            logger.warn("获取数据统计失败: {}", e.getMessage());
            return Result.error("获取数据统计失败: " + e.getMessage());
        } catch (Exception e) {
            logger.error("获取数据统计失败", e);
            return Result.error("获取数据统计失败，请稍后重试");
        }
    }
}