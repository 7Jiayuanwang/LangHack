package com.english.controller;

import com.english.dto.Result;
import com.english.entity.Class;
import com.english.entity.StudentClass;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.ClassService;
import com.english.service.StudentClassService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private static final Logger logger = LoggerFactory.getLogger(ClassController.class);

    @Autowired
    private ClassService classService;

    @Autowired
    private StudentClassService studentClassService;

    @Autowired
    private UserService userService;

    /**
     * 分页查询班级列表
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> getClasses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long teacherId,
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
            params.put("keyword", keyword);
            params.put("courseId", courseId);

            // 获取当前用户角色
            String userRole = getCurrentRole(authentication);

            // 教师只能查看自己管理的班级
            if ("ROLE_TEACHER".equals(userRole)) {
                Long currentTeacherId = getCurrentUserId(authentication);
                params.put("teacherId", currentTeacherId);
            } else {
                // 管理员可以指定教师ID或不指定
                params.put("teacherId", teacherId);
            }

            Map<String, Object> result = classService.getClassesByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 根据ID获取班级详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'USER', 'STUDENT')")
    public Result<Class> getClassById(@PathVariable Long id, Authentication authentication) {
        try {
            User currentUser = userService.getUserByUsername(authentication.getName());
            if (currentUser == null) {
                return Result.error("用户不存在");
            }

            Class classInfo = classService.getClassById(id);
            if (classInfo == null) {
                return Result.error("班级不存在");
            }

            String userRole = currentUser.getRole();
            Long userId = currentUser.getId();

            // 白名单模式：ADMIN 和 TEACHER 无限制，其他角色必须是班级学生
            if (!"ADMIN".equals(userRole) && !"TEACHER".equals(userRole)) {
                if (!studentClassService.isStudentInClass(userId, id)) {
                    return Result.error("没有权限查看此班级");
                }
            }

            return Result.success(classInfo);
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 创建班级
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> createClass(@RequestBody Class classInfo, Authentication authentication) {
        try {
            if (classInfo == null || classInfo.getName() == null || classInfo.getName().trim().isEmpty()) {
                return Result.error("班级名称不能为空");
            }
            if (classInfo.getName().length() > 100) {
                classInfo.setName(classInfo.getName().substring(0, 100));
            }
            if (classInfo.getCourseId() == null) {
                return Result.error("课程ID不能为空");
            }
            // 教师只能创建自己管理的班级
            if ("ROLE_TEACHER".equals(getCurrentRole(authentication))) {
                Long teacherId = getCurrentUserId(authentication);
                classInfo.setTeacherId(teacherId);
            }

            classService.createClass(classInfo);
            return Result.success("班级创建成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 更新班级
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> updateClass(@PathVariable Long id, @RequestBody Class classInfo, Authentication authentication) {
        try {
            if (classInfo == null || classInfo.getName() == null || classInfo.getName().trim().isEmpty()) {
                return Result.error("班级名称不能为空");
            }
            if (classInfo.getName().length() > 100) {
                classInfo.setName(classInfo.getName().substring(0, 100));
            }
            classInfo.setId(id);

            // 非管理员只能更新自己管理的班级
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))) {
                Long teacherId = getCurrentUserId(authentication);
                if (!classService.validateTeacherPermission(teacherId, id)) {
                    return Result.error("没有权限修改此班级");
                }
                // 教师不能修改班级的负责人
                classInfo.setTeacherId(teacherId);
            }

            classService.updateClass(classInfo);
            return Result.success("班级更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 删除班级
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> deleteClass(@PathVariable Long id, Authentication authentication) {
        try {
            // 非管理员只能删除自己管理的班级
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))) {
                Long teacherId = getCurrentUserId(authentication);
                if (!classService.validateTeacherPermission(teacherId, id)) {
                    return Result.error("没有权限删除此班级");
                }
            }

            classService.deleteClass(id);
            return Result.success("班级删除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 获取班级统计信息
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getClassStats() {
        try {
            Map<String, Object> stats = classService.getClassStats();
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 根据教师ID获取班级列表
     */
    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<List<Class>> getClassesByTeacherId(@PathVariable Long teacherId, Authentication authentication) {
        try {
            // 非管理员只能查看自己的班级
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))) {
                Long currentTeacherId = getCurrentUserId(authentication);
                if (!currentTeacherId.equals(teacherId)) {
                    return Result.error("没有权限查看其他教师的班级");
                }
            }

            List<Class> classes = classService.getClassesByTeacherId(teacherId);
            return Result.success(classes);
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 当前学生获取自己的班级列表
     */
    @GetMapping("/student/me")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<List<Class>> getMyClasses(Authentication authentication) {
        try {
            Long studentId = getCurrentUserId(authentication);
            List<Class> classes = classService.getClassesByStudentId(studentId);
            return Result.success(classes);
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 根据学生ID获取班级列表
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
    public Result<List<Class>> getClassesByStudentId(@PathVariable Long studentId, Authentication authentication) {
        try {
            // 学生和普通用户只能查看自己的班级
            String authority = getCurrentRole(authentication);
            if (!"ROLE_ADMIN".equals(authority) && !"ROLE_TEACHER".equals(authority)) {
                Long currentStudentId = getCurrentUserId(authentication);
                if (!currentStudentId.equals(studentId)) {
                    return Result.error("没有权限查看其他学生的班级");
                }
            }

            List<Class> classes = classService.getClassesByStudentId(studentId);
            return Result.success(classes);
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 搜索班级
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
    public Result<List<Class>> searchClasses(@RequestParam String keyword) {
        try {
            // 验证关键词长度
            if (keyword == null || keyword.trim().isEmpty()) {
                return Result.error("搜索关键词不能为空");
            }
            if (keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            List<Class> classes = classService.searchClasses(keyword);
            return Result.success(classes);
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 学生加入班级
     */
    @PostMapping("/join")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<String> joinClass(@RequestBody Map<String, Long> params, Authentication authentication) {
        try {
            Long studentId = getCurrentUserId(authentication);
            Long classId = params.get("classId");
            if (classId == null) {
                return Result.error("班级ID不能为空");
            }
            studentClassService.joinClass(studentId, classId);
            return Result.success("成功加入班级");
        } catch (BusinessException e) {
            logger.error("操作失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 学生退出班级
     */
    @PostMapping("/leave")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<String> leaveClass(@RequestBody Map<String, Long> params, Authentication authentication) {
        try {
            Long studentId = getCurrentUserId(authentication);
            Long classId = params.get("classId");
            if (classId == null) {
                return Result.error("班级ID不能为空");
            }
            studentClassService.leaveClass(studentId, classId);
            return Result.success("成功退出班级");
        } catch (BusinessException e) {
            logger.error("操作失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 获取班级学生列表
     */
    @GetMapping("/{classId}/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<List<StudentClass>> getStudentsByClassId(@PathVariable Long classId, Authentication authentication) {
        try {
            // 非管理员只能查看自己管理班级的学生
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))) {
                Long teacherId = getCurrentUserId(authentication);
                if (!classService.validateTeacherPermission(teacherId, classId)) {
                    return Result.error("没有权限查看此班级的学生");
                }
            }

            List<StudentClass> students = studentClassService.getStudentsByClassId(classId);
            return Result.success(students);
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 教师添加学生到班级
     */
    @PostMapping("/{classId}/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> addStudentToClass(@PathVariable Long classId, @RequestBody Map<String, Long> params, Authentication authentication) {
        try {
            Long studentId = params.get("studentId");
            if (studentId == null) {
                return Result.error("学生ID不能为空");
            }

            // 教师权限校验：教师只能为自己管理的班级添加学生
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))) {
                Long teacherId = getCurrentUserId(authentication);
                if (!classService.validateTeacherPermission(teacherId, classId)) {
                    return Result.error("没有权限管理此班级");
                }
                studentClassService.addStudentToClass(teacherId, classId, studentId);
            } else {
                // 管理员直接添加学生
                studentClassService.joinClass(studentId, classId);
            }
            return Result.success("学生添加成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 教师移除学生出班级
     */
    @DeleteMapping("/{classId}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> removeStudentFromClass(@PathVariable Long classId, @PathVariable Long studentId, Authentication authentication) {
        try {
            // 教师权限校验：教师只能为自己管理的班级移除学生
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))) {
                Long teacherId = getCurrentUserId(authentication);
                if (!classService.validateTeacherPermission(teacherId, classId)) {
                    return Result.error("没有权限管理此班级");
                }
                studentClassService.removeStudentFromClass(teacherId, classId, studentId);
            } else {
                // 管理员直接移除学生
                studentClassService.leaveClass(studentId, classId);
            }
            return Result.success("学生移除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 批量添加学生到班级
     */
    @PostMapping("/{classId}/students/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> batchAddStudentsToClass(@PathVariable Long classId, @RequestBody Map<String, List<Long>> params, Authentication authentication) {
        try {
            Long teacherId = getCurrentUserId(authentication);
            List<Long> studentIds = params.get("studentIds");
            if (studentIds == null || studentIds.isEmpty()) {
                return Result.error("学生ID列表不能为空");
            }
            if (studentIds.size() > 1000) {
                return Result.error("批量添加学生数量不能超过1000");
            }

            // 教师权限校验：教师只能为自己管理的班级添加学生
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))) {
                if (!classService.validateTeacherPermission(teacherId, classId)) {
                    return Result.error("没有权限管理此班级");
                }
            }

            studentClassService.batchAddStudentsToClass(classId, studentIds);
            return Result.success("批量添加学生成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("操作失败", e);

            return Result.error("操作失败，请稍后重试");
        }
    }

    private Long getCurrentUserId(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user.getId();
    }

    private String getCurrentRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().isEmpty()) {
            return null;
        }
        return authentication.getAuthorities().iterator().next().getAuthority();
    }
}