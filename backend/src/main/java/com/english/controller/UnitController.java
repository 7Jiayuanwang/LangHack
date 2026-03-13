package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.Result;
import com.english.entity.Unit;
import com.english.entity.User;
import com.english.entity.Class;
import com.english.exception.BusinessException;
import com.english.service.UnitService;
import com.english.service.UserService;
import com.english.service.ClassService;
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
@RequestMapping("/api/units")
public class UnitController {

    private static final Logger log = LoggerFactory.getLogger(UnitController.class);

    @Autowired
    private UnitService unitService;

    @Autowired
    private UserService userService;

    @Autowired
    private ClassService classService;

    /**
     * 获取单元总数
     */
    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Long> getUnitCount() {
        try {
            long count = unitService.count();
            return Result.success(count);
        } catch (Exception e) {
            log.error("获取单元总数失败", e);
            return Result.error("获取单元总数失败，请稍后重试");
        }
    }

    /**
     * 根据课程ID查询单元列表
     */
    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
    public Result<List<Unit>> getUnitsByCourseId(@PathVariable Long courseId) {
        try {
            List<Unit> units = unitService.getUnitsByCourseId(courseId);
            return Result.success(units);
        } catch (Exception e) {
            log.error("获取课程单元失败", e);
            return Result.error("获取课程单元失败，请稍后重试");
        }
    }

    /**
     * 根据ID获取单元详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
    public Result<Unit> getUnitById(@PathVariable Long id) {
        try {
            Unit unit = unitService.getUnitById(id);
            if (unit == null) {
                return Result.error("单元不存在");
            }
            return Result.success(unit);
        } catch (Exception e) {
            log.error("获取单元详情失败", e);
            return Result.error("获取单元详情失败，请稍后重试");
        }
    }

    /**
     * 分页查询单元列表
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
    public Result<Map<String, Object>> getUnits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword) {

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
            params.put("courseId", courseId);
            params.put("keyword", keyword);

            Map<String, Object> result = unitService.getUnitsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取单元列表失败", e);
            return Result.error("获取单元列表失败，请稍后重试");
        }
    }

    /**
     * 创建单元
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> createUnit(@RequestBody Unit unit, Authentication authentication) {
        try {
            // 验证单元名称不能为空
            if (unit == null || unit.getName() == null || unit.getName().trim().isEmpty()) {
                throw new BusinessException("单元名称不能为空");
            }

            // 获取用户角色
            User currentUser = userService.getUserByUsername(authentication.getName());
            if (currentUser == null) {
                throw new BusinessException("用户不存在");
            }
            String userRole = currentUser.getRole();
            // 检查用户ID是否为null（防御性编程）
            if (currentUser.getId() == null) {
                throw new BusinessException("用户ID无效");
            }

            // 教师需验证课程所有权
            if ("TEACHER".equals(userRole)) {
                Long courseId = unit.getCourseId();
                if (courseId == null) {
                    throw new BusinessException("课程ID不能为空");
                }
                List<Class> classes = classService.getClassesByCourseId(courseId);
                boolean hasPermission = classes != null && classes.stream()
                        .anyMatch(c -> currentUser.getId().equals(c.getTeacherId()));
                if (!hasPermission) {
                    throw new BusinessException("没有权限操作此课程的单元");
                }
            }

            unitService.createUnit(unit);
            return Result.success("单元创建成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("创建单元失败", e);
            return Result.error("创建单元失败，请稍后重试");
        }
    }

    /**
     * 更新单元
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> updateUnit(@PathVariable Long id, @RequestBody Unit unit, Authentication authentication) {
        try {
            // 检查单元是否存在
            Unit existingUnit = unitService.getUnitById(id);
            if (existingUnit == null) {
                throw new BusinessException("单元不存在");
            }

            // 获取用户角色
            User currentUser = userService.getUserByUsername(authentication.getName());
            if (currentUser == null) {
                throw new BusinessException("用户不存在");
            }
            String userRole = currentUser.getRole();
            // 检查用户ID是否为null（防御性编程）
            if (currentUser.getId() == null) {
                throw new BusinessException("用户ID无效");
            }

            // 教师需验证课程所有权
            if ("TEACHER".equals(userRole)) {
                Long courseId = existingUnit.getCourseId();
                if (courseId == null) {
                    throw new BusinessException("课程ID不能为空");
                }
                List<Class> classes = classService.getClassesByCourseId(courseId);
                boolean hasPermission = classes != null && classes.stream()
                        .anyMatch(c -> currentUser.getId().equals(c.getTeacherId()));
                if (!hasPermission) {
                    throw new BusinessException("没有权限操作此课程的单元");
                }
            }

            // 禁止修改courseId，保留原课程归属
            unit.setCourseId(existingUnit.getCourseId());
            unit.setId(id);
            unitService.updateUnit(unit);
            return Result.success("单元更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新单元失败", e);
            return Result.error("更新单元失败，请稍后重试");
        }
    }

    /**
     * 删除单元
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> deleteUnit(@PathVariable Long id, Authentication authentication) {
        try {
            // 检查单元是否存在
            Unit unit = unitService.getUnitById(id);
            if (unit == null) {
                throw new BusinessException("单元不存在");
            }

            // 获取用户角色
            User currentUser = userService.getUserByUsername(authentication.getName());
            if (currentUser == null) {
                throw new BusinessException("用户不存在");
            }
            // 检查用户ID是否为null（防御性编程）
            if (currentUser.getId() == null) {
                throw new BusinessException("用户ID无效");
            }
            String userRole = currentUser.getRole();

            // 教师需验证课程所有权
            if ("TEACHER".equals(userRole)) {
                Long courseId = unit.getCourseId();
                if (courseId == null) {
                    throw new BusinessException("课程ID不能为空");
                }
                List<Class> classes = classService.getClassesByCourseId(courseId);
                boolean hasPermission = classes != null && classes.stream()
                        .anyMatch(c -> currentUser.getId().equals(c.getTeacherId()));
                if (!hasPermission) {
                    throw new BusinessException("没有权限操作此课程的单元");
                }
            }

            unitService.deleteUnit(id);
            return Result.success("单元删除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除单元失败", e);
            return Result.error("删除单元失败，请稍后重试");
        }
    }

}