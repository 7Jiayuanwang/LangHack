package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.Result;
import com.english.entity.TeacherStudentBinding;
import com.english.mapper.TeacherStudentBindingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 师生绑定关系管理控制器
 * <p>
 * 管理员/运维可以查看和管理师生绑定关系，支持按学院筛选
 */
@RestController
@RequestMapping("/api/admin/teacher-student-bindings")
public class TeacherStudentBindingController {

    private static final Logger logger = LoggerFactory.getLogger(TeacherStudentBindingController.class);

    @Autowired
    private TeacherStudentBindingMapper bindingMapper;

    /**
     * 获取师生绑定关系列表
     * GET /api/admin/teacher-student-bindings?page=1&size=20&college=外国语学院&teacherId=&studentId=
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Page<TeacherStudentBinding>> listBindings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String college,
            @RequestParam(required = false) String teacherId,
            @RequestParam(required = false) String studentId) {
        try {
            Page<TeacherStudentBinding> pageReq = new Page<>(page, size);
            QueryWrapper<TeacherStudentBinding> wrapper = new QueryWrapper<>();

            if (college != null && !college.trim().isEmpty()) {
                wrapper.eq("college_name", college.trim());
            }
            if (teacherId != null && !teacherId.trim().isEmpty()) {
                wrapper.eq("teacher_id", teacherId.trim());
            }
            if (studentId != null && !studentId.trim().isEmpty()) {
                wrapper.eq("student_id", studentId.trim());
            }

            wrapper.orderByDesc("created_at");

            Page<TeacherStudentBinding> result = bindingMapper.selectPage(pageReq, wrapper);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取师生绑定关系失败", e);
            return Result.error("获取绑定关系失败，请稍后重试");
        }
    }

    /**
     * 删除师生绑定关系
     * DELETE /api/admin/teacher-student-bindings/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Void> deleteBinding(@PathVariable Long id) {
        try {
            int deleted = bindingMapper.deleteById(id);
            if (deleted > 0) {
                return Result.success(null);
            } else {
                return Result.error("绑定关系不存在");
            }
        } catch (Exception e) {
            logger.error("删除师生绑定关系失败: id={}", id, e);
            return Result.error("删除失败，请稍后重试");
        }
    }

    /**
     * 批量删除师生绑定关系
     * POST /api/admin/teacher-student-bindings/batch-delete
     * Body: { "ids": [1, 2, 3] }
     */
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Void> batchDeleteBindings(@RequestBody java.util.Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Long> ids = (java.util.List<Long>) request.get("ids");
            if (ids == null || ids.isEmpty()) {
                return Result.error("请选择要删除的绑定关系");
            }

            int deleted = bindingMapper.deleteBatchIds(ids);
            logger.info("批量删除师生绑定关系: 删除{}条", deleted);
            return Result.success(null);
        } catch (Exception e) {
            logger.error("批量删除师生绑定关系失败", e);
            return Result.error("批量删除失败，请稍后重试");
        }
    }

    /**
     * 获取学院列表（用于筛选）
     * GET /api/admin/teacher-student-bindings/colleges
     */
    @GetMapping("/colleges")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<java.util.List<String>> listColleges() {
        try {
            java.util.List<String> colleges = bindingMapper.selectDistinctColleges();
            return Result.success(colleges);
        } catch (Exception e) {
            logger.error("获取学院列表失败", e);
            return Result.error("获取学院列表失败");
        }
    }
}
