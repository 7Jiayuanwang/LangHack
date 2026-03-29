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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("keyword", keyword);
            params.put("courseId", courseId);

            String userRole = getCurrentRole(authentication);
            if ("ROLE_TEACHER".equals(userRole)) {
                params.put("teacherId", getCurrentUserId(authentication));
            } else {
                params.put("teacherId", teacherId);
            }

            return Result.success(classService.getClassesByPage(params));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取班级列表失败", e);
            return Result.error("获取班级列表失败，请稍后重试");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'USER', 'STUDENT')")
    public Result<Class> getClassById(@PathVariable Long id, Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            Class classInfo = classService.getClassById(id);
            if (classInfo == null) {
                return Result.error("班级不存在");
            }

            if (!Set.of("ADMIN", "TEACHER").contains(currentUser.getRole())
                    && !studentClassService.isStudentInClass(currentUser.getId(), id)) {
                return Result.error("没有权限查看此班级");
            }

            return Result.success(classInfo);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取班级详情失败", e);
            return Result.error("获取班级详情失败，请稍后重试");
        }
    }

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

            if ("ROLE_TEACHER".equals(getCurrentRole(authentication))) {
                classInfo.setTeacherId(getCurrentUserId(authentication));
            }

            classService.createClass(classInfo);
            return Result.success("班级创建成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("创建班级失败", e);
            return Result.error("创建班级失败，请稍后重试");
        }
    }

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

            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))) {
                Long teacherId = getCurrentUserId(authentication);
                if (!classService.validateTeacherPermission(teacherId, id)) {
                    return Result.error("没有权限修改此班级");
                }
                classInfo.setTeacherId(teacherId);
            }

            classService.updateClass(classInfo);
            return Result.success("班级更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新班级失败", e);
            return Result.error("更新班级失败，请稍后重试");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> deleteClass(@PathVariable Long id, Authentication authentication) {
        try {
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
            logger.error("删除班级失败", e);
            return Result.error("删除班级失败，请稍后重试");
        }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getClassStats() {
        try {
            return Result.success(classService.getClassStats());
        } catch (Exception e) {
            logger.error("获取班级统计失败", e);
            return Result.error("获取班级统计失败，请稍后重试");
        }
    }

    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<List<Class>> getClassesByTeacherId(@PathVariable Long teacherId, Authentication authentication) {
        try {
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))
                    && !getCurrentUserId(authentication).equals(teacherId)) {
                return Result.error("没有权限查看其他教师的班级");
            }
            return Result.success(classService.getClassesByTeacherId(teacherId));
        } catch (Exception e) {
            logger.error("获取教师班级失败", e);
            return Result.error("获取教师班级失败，请稍后重试");
        }
    }

    @GetMapping("/student/me")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<List<Class>> getMyClasses(Authentication authentication) {
        try {
            Long studentId = getCurrentUserId(authentication);
            return Result.success(classService.getClassesByStudentId(studentId));
        } catch (Exception e) {
            logger.error("获取我的班级失败", e);
            return Result.error("获取我的班级失败，请稍后重试");
        }
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
    public Result<List<Class>> getClassesByStudentId(@PathVariable Long studentId, Authentication authentication) {
        try {
            String role = getCurrentRole(authentication);
            Long currentUserId = getCurrentUserId(authentication);

            if (!"ROLE_ADMIN".equals(role) && !"ROLE_TEACHER".equals(role) && !currentUserId.equals(studentId)) {
                return Result.error("没有权限查看其他学生的班级");
            }

            List<Class> studentClasses = classService.getClassesByStudentId(studentId);
            if ("ROLE_TEACHER".equals(role)) {
                Set<Long> teacherClassIds = classService.getClassesByTeacherId(currentUserId).stream()
                        .map(Class::getId)
                        .collect(Collectors.toSet());
                studentClasses = studentClasses.stream()
                        .filter(cls -> teacherClassIds.contains(cls.getId()))
                        .collect(Collectors.toList());
                if (studentClasses.isEmpty()) {
                    return Result.error("该学生不在您管理的班级中");
                }
            }

            return Result.success(studentClasses);
        } catch (Exception e) {
            logger.error("获取学生班级失败", e);
            return Result.error("获取学生班级失败，请稍后重试");
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
    public Result<List<Class>> searchClasses(@RequestParam(required = false) String keyword, Authentication authentication) {
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                keyword = null;
            }
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            return Result.success(classService.searchClasses(keyword));
        } catch (Exception e) {
            logger.error("搜索班级失败", e);
            return Result.error("搜索班级失败，请稍后重试");
        }
    }

    @PostMapping("/join")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<String> joinClass(@RequestBody Map<String, Long> params, Authentication authentication) {
        try {
            if (params == null || params.get("classId") == null) {
                return Result.error("班级ID不能为空");
            }
            studentClassService.joinClass(getCurrentUserId(authentication), params.get("classId"));
            return Result.success("成功加入班级");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("加入班级失败", e);
            return Result.error("加入班级失败，请稍后重试");
        }
    }

    @PostMapping("/leave")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<String> leaveClass(@RequestBody Map<String, Long> params, Authentication authentication) {
        try {
            if (params == null || params.get("classId") == null) {
                return Result.error("班级ID不能为空");
            }
            studentClassService.leaveClass(getCurrentUserId(authentication), params.get("classId"));
            return Result.success("成功退出班级");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("退出班级失败", e);
            return Result.error("退出班级失败，请稍后重试");
        }
    }

    @GetMapping("/{classId}/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<List<StudentClass>> getStudentsByClassId(@PathVariable Long classId, Authentication authentication) {
        try {
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))
                    && !classService.validateTeacherPermission(getCurrentUserId(authentication), classId)) {
                return Result.error("没有权限查看此班级的学生");
            }
            return Result.success(studentClassService.getStudentsByClassId(classId));
        } catch (Exception e) {
            logger.error("获取班级学生失败", e);
            return Result.error("获取班级学生失败，请稍后重试");
        }
    }

    @PostMapping("/{classId}/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> addStudentToClass(@PathVariable Long classId,
                                            @RequestBody Map<String, Long> params,
                                            Authentication authentication) {
        try {
            Long studentId = params != null ? params.get("studentId") : null;
            if (studentId == null) {
                return Result.error("学生ID不能为空");
            }
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))
                    && !classService.validateTeacherPermission(getCurrentUserId(authentication), classId)) {
                return Result.error("没有权限管理此班级");
            }
            studentClassService.joinClass(studentId, classId);
            return Result.success("学生添加成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("添加学生失败", e);
            return Result.error("添加学生失败，请稍后重试");
        }
    }

    @DeleteMapping("/{classId}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> removeStudentFromClass(@PathVariable Long classId,
                                                 @PathVariable Long studentId,
                                                 Authentication authentication) {
        try {
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))
                    && !classService.validateTeacherPermission(getCurrentUserId(authentication), classId)) {
                return Result.error("没有权限管理此班级");
            }
            studentClassService.leaveClass(studentId, classId);
            return Result.success("学生移除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("移除学生失败", e);
            return Result.error("移除学生失败，请稍后重试");
        }
    }

    @PostMapping("/{classId}/students/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> batchAddStudentsToClass(@PathVariable Long classId,
                                                  @RequestBody Map<String, List<Long>> params,
                                                  Authentication authentication) {
        try {
            List<Long> studentIds = params != null ? params.get("studentIds") : null;
            if (studentIds == null || studentIds.isEmpty()) {
                return Result.error("学生ID列表不能为空");
            }
            if (studentIds.size() > 1000) {
                return Result.error("批量添加学生数量不能超过1000");
            }
            if (!"ROLE_ADMIN".equals(getCurrentRole(authentication))
                    && !classService.validateTeacherPermission(getCurrentUserId(authentication), classId)) {
                return Result.error("没有权限管理此班级");
            }
            studentClassService.batchAddStudentsToClass(classId, studentIds);
            return Result.success("批量添加学生成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("批量添加学生失败", e);
            return Result.error("批量添加学生失败，请稍后重试");
        }
    }

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException("用户未认证");
        }
        User user = userService.getUserByUsername(authentication.getName());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private Long getCurrentUserId(Authentication authentication) {
        return requireCurrentUser(authentication).getId();
    }

    private String getCurrentRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().isEmpty()) {
            return null;
        }

        boolean hasAdmin = false;
        boolean hasOps = false;
        boolean hasTeacher = false;
        boolean hasStudent = false;
        boolean hasUser = false;
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
                case "ROLE_STUDENT":
                    hasStudent = true;
                    break;
                case "ROLE_USER":
                    hasUser = true;
                    break;
                default:
                    break;
            }
        }

        if (hasAdmin) return "ROLE_ADMIN";
        if (hasOps) return "ROLE_OPS";
        if (hasTeacher) return "ROLE_TEACHER";
        if (hasStudent) return "ROLE_STUDENT";
        if (hasUser) return "ROLE_USER";
        return authentication.getAuthorities().iterator().next().getAuthority();
    }
}
