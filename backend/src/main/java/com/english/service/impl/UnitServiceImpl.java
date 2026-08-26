package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Assignment;
import com.english.entity.Unit;
import com.english.mapper.AssignmentMapper;
import com.english.mapper.TextMapper;
import com.english.mapper.TextUnitMapper;
import com.english.mapper.UnitMapper;
import com.english.service.UnitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.english.exception.BusinessException;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UnitServiceImpl extends ServiceImpl<UnitMapper, Unit> implements UnitService {

    private static final Logger logger = LoggerFactory.getLogger(UnitServiceImpl.class);

    @Autowired
    private AssignmentMapper assignmentMapper;

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private TextUnitMapper textUnitMapper;

    @Override
    public Map<String, Object> getUnitsByPage(Map<String, Object> params) {
        int page = params.get("page") != null ? ((Number) params.get("page")).intValue() : 1;
        int size = params.get("size") != null ? ((Number) params.get("size")).intValue() : 10;
        // 分页参数边界校验
        page = Math.max(1, page);
        size = Math.max(1, Math.min(size, 100));
        Long courseId = null;
        if (params.get("courseId") != null) {
            try {
                courseId = Long.valueOf(params.get("courseId").toString());
            } catch (NumberFormatException e) {
                throw new BusinessException("courseId 格式错误");
            }
        }
        String keyword = (String) params.get("keyword");

        QueryWrapper<Unit> queryWrapper = new QueryWrapper<>();

        if (courseId != null) {
            queryWrapper.eq("course_id", courseId);
        }

        if (keyword != null && !keyword.isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like("name", keyword)
                    .or()
                    .like("description", keyword));
        }

        queryWrapper.orderByAsc("order_num").orderByDesc("created_time");

        Page<Unit> pageParam = new Page<>(page, size);
        IPage<Unit> pageResult = page(pageParam, queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    public List<Unit> getUnitsByCourseId(Long courseId) {
        QueryWrapper<Unit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId);
        queryWrapper.orderByAsc("order_num").orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public Unit getUnitById(Long id) {
        return getById(id);
    }

    @Override
    @Transactional
    public void createUnit(Unit unit) {
        save(unit);
    }

    @Override
    @Transactional
    public void updateUnit(Unit unit) {
        updateById(unit);
    }

    @Override
    @Transactional
    public void deleteUnit(Long id) {
        // 1. 验证单元存在
        Unit unit = getById(id);
        if (unit == null) {
            throw new BusinessException("单元不存在");
        }

        // 2. 检查是否有活跃的未删除任务（防止误删正在使用的单元）
        // 活跃任务：deleted=0 且 status 为 PUBLISHED 或 DRAFT（未完全关闭）
        QueryWrapper<Assignment> activeCheck = new QueryWrapper<>();
        activeCheck.eq("unit_id", id).eq("deleted", 0)
                .and(w -> w.eq("status", "PUBLISHED").or().eq("status", "DRAFT"));
        Long activeCount = assignmentMapper.selectCount(activeCheck);
        if (activeCount != null && activeCount > 0) {
            throw new BusinessException("该单元下有 " + activeCount + " 个活跃的任务，请先关闭或删除这些任务后再删除单元");
        }

        // 3. 软删除该单元下未删除的任务（Assignment 无 @TableLogic，手动设置 deleted=1）
        assignmentMapper.update(null, new UpdateWrapper<Assignment>()
                .eq("unit_id", id).eq("deleted", 0)
                .set("deleted", 1));

        // 4. 断开所有任务的 unit_id FK 引用（包括已删除的任务），防止 FK 约束阻止删除单元
        assignmentMapper.update(null, new UpdateWrapper<Assignment>()
                .eq("unit_id", id)
                .setSql("unit_id = NULL"));

        // 5. 软删除该单元下未删除的课文（使用自定义 SQL 绕过 @TableLogic 的 SELECT 过滤）
        int deletedCount = textMapper.softDeleteByUnitId(id);
        logger.debug("软删除单位 {} 下的课文：{} 篇", id, deletedCount);

        // 6. 断开所有课文的 unit_id FK 引用（包括已软删除的课文），
        //    防止 DB 层 ON DELETE CASCADE 物理删除已软删除的课文
        int clearedCount = textMapper.clearUnitReference(id);
        textUnitMapper.deleteAllRelationsByUnitId(id);
        logger.debug("清除单位 {} 下课文的单位引用：{} 篇", id, clearedCount);

        // 7. 删除单元本身（unit_words 由 DB ON DELETE CASCADE 自动清理）
        removeById(id);

        logger.info("单元删除完成: unitId={}, 关联数据已安全清理", id);
    }

    /** 用户上传专用课程ID */
    private static final Long USER_UPLOAD_COURSE_ID = 999L;

    @Override
    public void ensureUserUploadUnitExists(Long unitId) {
        // 先快速检查是否已存在，避免每次上传都执行 SQL
        Unit existing = getById(unitId);
        if (existing != null) {
            return;
        }

        // 不存在时，使用 INSERT IGNORE 原子创建（并发安全）
        // 先确保占位课程存在（units.course_id 有外键约束到 courses）
        try {
            baseMapper.ensurePlaceholderCourseExists(USER_UPLOAD_COURSE_ID);
            baseMapper.ensurePlaceholderUnitExists(unitId, USER_UPLOAD_COURSE_ID);
            logger.info("用户上传专用单元已自动创建: unitId={}", unitId);
        } catch (Exception e) {
            logger.warn("创建用户上传专用单元时出现异常（可能已被并发创建）: {}", e.getMessage());
            // 再次检查是否已存在
            if (getById(unitId) == null) {
                throw new BusinessException("系统初始化失败，请联系管理员");
            }
        }
    }
}
