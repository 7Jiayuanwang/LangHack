//班级管理控制器

package com.english.controller;

import com.english.dto.Result;
import com.english.entity.Class;
import com.english.entity.StudentClass;
import com.english.service.ClassService;
import com.english.service.StudentClassService;
import com.english.service.UserService;
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
            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("keyword", keyword);
            params.put("courseId", courseId);

            // 获取当前用户角色
            String userRole = authentication.getAuthorities().iterator().next().getAuthority();
            String username = authentication.getName();

            // 教师只能查看自己管理的班级
            if ("ROLE_TEACHER".equals(userRole)) {
                Long currentTeacherId = userService.getUserByUsername(username).getId();
                params.put("teacherId", currentTeacherId);
            } else {
                // 管理员可以指定教师ID或不指定
                params.put("teacherId", teacherId);
            }

            Map<String, Object> result = classService.getClassesByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据ID获取班级详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Class> getClassById(@PathVariable Long id, Authentication authentication) {
        try {
            String userRole = userService.getUserByUsername(authentication.getName()).getRole();

            // 非管理员只能查看自己管理的班级或自己加入的班级
            if (!"ROLE_ADMIN".equals(userRole)) {
                Long userId = userService.getUserByUsername(authentication.getName()).getId();
                Class classInfo = classService.getClassById(id);

                if (classInfo == null) {
                    return Result.error("班级不存在");
                }

                if ("ROLE_TEACHER".equals(userRole) && !classInfo.getTeacherId().equals(userId)) {
                    return Result.error("没有权限查看此班级");
                }

                if ("ROLE_STUDENT".equals(userRole) && !studentClassService.isStudentInClass(userId, id)) {
                    return Result.error("没有权限查看此班级");
                }
            }

            Class classInfo = classService.getClassById(id);
            return Result.success(classInfo);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 创建班级
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> createClass(@RequestBody Class classInfo, Authentication authentication) {
        try {
            // 教师只能创建自己管理的班级
            if ("ROLE_TEACHER".equals(authentication.getAuthorities().iterator().next().getAuthority())) {
                Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
                classInfo.setTeacherId(teacherId);
            }

            classService.createClass(classInfo);
            return Result.success("班级创建成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新班级
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> updateClass(@PathVariable Long id, @RequestBody Class classInfo, Authentication authentication) {
        try {
            classInfo.setId(id);

            // 非管理员只能更新自己管理的班级
            if (!"ROLE_ADMIN".equals(authentication.getAuthorities().iterator().next().getAuthority())) {
                Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
                if (!classService.validateTeacherPermission(teacherId, id)) {
                    return Result.error("没有权限修改此班级");
                }
                // 教师不能修改班级的负责人
                classInfo.setTeacherId(teacherId);
            }

            classService.updateClass(classInfo);
            return Result.success("班级更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
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
            if (!"ROLE_ADMIN".equals(authentication.getAuthorities().iterator().next().getAuthority())) {
                Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
                if (!classService.validateTeacherPermission(teacherId, id)) {
                    return Result.error("没有权限删除此班级");
                }
            }

            classService.deleteClass(id);
            return Result.success("班级删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
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
            return Result.error(e.getMessage());
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
            if (!"ADMIN".equals(authentication.getAuthorities().iterator().next().getAuthority())) {
                Long currentTeacherId = userService.getUserByUsername(authentication.getName()).getId();
                if (!currentTeacherId.equals(teacherId)) {
                    return Result.error("没有权限查看其他教师的班级");
                }
            }

            List<Class> classes = classService.getClassesByTeacherId(teacherId);
            return Result.success(classes);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 当前学生获取自己的班级列表
     */
    @GetMapping("/student/me")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<Class>> getMyClasses(Authentication authentication) {
        try {
            Long studentId = userService.getUserByUsername(authentication.getName()).getId();
            List<Class> classes = classService.getClassesByStudentId(studentId);
            return Result.success(classes);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据学生ID获取班级列表
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    public Result<List<Class>> getClassesByStudentId(@PathVariable Long studentId, Authentication authentication) {
        try {
            // 学生只能查看自己的班级
            if ("ROLE_STUDENT".equals(authentication.getAuthorities().iterator().next().getAuthority())) {
                Long currentStudentId = userService.getUserByUsername(authentication.getName()).getId();
                if (!currentStudentId.equals(studentId)) {
                    return Result.error("没有权限查看其他学生的班级");
                }
            }

            List<Class> classes = classService.getClassesByStudentId(studentId);
            return Result.success(classes);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 搜索班级
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    public Result<List<Class>> searchClasses(@RequestParam String keyword) {
        try {
            List<Class> classes = classService.searchClasses(keyword);
            return Result.success(classes);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 学生加入班级
     */
    @PostMapping("/join")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<String> joinClass(@RequestBody Map<String, Long> params, Authentication authentication) {
        try {
            Long studentId = userService.getUserByUsername(authentication.getName()).getId();
            Long classId = params.get("classId");

            studentClassService.joinClass(studentId, classId);
            return Result.success("成功加入班级");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 学生退出班级
     */
    @PostMapping("/leave")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<String> leaveClass(@RequestBody Map<String, Long> params, Authentication authentication) {
        try {
            Long studentId = userService.getUserByUsername(authentication.getName()).getId();
            Long classId = params.get("classId");

            studentClassService.leaveClass(studentId, classId);
            return Result.success("成功退出班级");
        } catch (Exception e) {
            return Result.error(e.getMessage());
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
            if (!"ROLE_ADMIN".equals(authentication.getAuthorities().iterator().next().getAuthority())) {
                Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
                if (!classService.validateTeacherPermission(teacherId, classId)) {
                    return Result.error("没有权限查看此班级的学生");
                }
            }

            List<StudentClass> students = studentClassService.getStudentsByClassId(classId);
            return Result.success(students);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 教师添加学生到班级
     */
    @PostMapping("/{classId}/students")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<String> addStudentToClass(@PathVariable Long classId, @RequestBody Map<String, Long> params, Authentication authentication) {
        try {
            Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
            Long studentId = params.get("studentId");

            studentClassService.addStudentToClass(teacherId, classId, studentId);
            return Result.success("学生添加成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 教师移除学生出班级
     */
    @DeleteMapping("/{classId}/students/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<String> removeStudentFromClass(@PathVariable Long classId, @PathVariable Long studentId, Authentication authentication) {
        try {
            Long teacherId = userService.getUserByUsername(authentication.getName()).getId();

            studentClassService.removeStudentFromClass(teacherId, classId, studentId);
            return Result.success("学生移除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量添加学生到班级
     */
    @PostMapping("/{classId}/students/batch")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<String> batchAddStudentsToClass(@PathVariable Long classId, @RequestBody Map<String, List<Long>> params, Authentication authentication) {
        try {
            Long teacherId = userService.getUserByUsername(authentication.getName()).getId();
            List<Long> studentIds = params.get("studentIds");

            // 验证教师权限
            if (!classService.validateTeacherPermission(teacherId, classId)) {
                return Result.error("没有权限管理此班级");
            }

            studentClassService.batchAddStudentsToClass(classId, studentIds);
            return Result.success("批量添加学生成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}