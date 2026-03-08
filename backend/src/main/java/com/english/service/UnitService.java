package com.english.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.english.entity.Unit;

import java.util.List;
import java.util.Map;

public interface UnitService extends IService<Unit> {

    /**
     * 分页查询单元列表
     */
    Map<String, Object> getUnitsByPage(Map<String, Object> params);

    /**
     * 根据课程ID查询单元列表
     */
    List<Unit> getUnitsByCourseId(Long courseId);

    /**
     * 根据ID获取单元详情
     */
    Unit getUnitById(Long id);

    /**
     * 创建单元
     */
    void createUnit(Unit unit);

    /**
     * 更新单元
     */
    void updateUnit(Unit unit);

    /**
     * 删除单元
     */
    void deleteUnit(Long id);
}