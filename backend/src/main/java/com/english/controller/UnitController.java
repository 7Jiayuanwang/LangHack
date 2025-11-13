package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.Result;
import com.english.entity.Unit;
import com.english.service.UnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/units")
public class UnitController {

    @Autowired
    private UnitService unitService;

    /**
     * 分页查询单元列表
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    public Result<Map<String, Object>> getUnits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword) {

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("courseId", courseId);
            params.put("keyword", keyword);

            Map<String, Object> result = unitService.getUnitsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据课程ID查询单元列表
     */
    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    public Result<List<Unit>> getUnitsByCourseId(@PathVariable Long courseId) {
        try {
            List<Unit> units = unitService.getUnitsByCourseId(courseId);
            return Result.success(units);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据ID获取单元详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    public Result<Unit> getUnitById(@PathVariable Long id) {
        try {
            Unit unit = unitService.getUnitById(id);
            if (unit == null) {
                return Result.error("单元不存在");
            }
            return Result.success(unit);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 创建单元
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> createUnit(@RequestBody Unit unit) {
        try {
            unitService.createUnit(unit);
            return Result.success("单元创建成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新单元
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> updateUnit(@PathVariable Long id, @RequestBody Unit unit) {
        try {
            unit.setId(id);
            unitService.updateUnit(unit);
            return Result.success("单元更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除单元
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> deleteUnit(@PathVariable Long id) {
        try {
            unitService.deleteUnit(id);
            return Result.success("单元删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

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
            return Result.error(e.getMessage());
        }
    }
}