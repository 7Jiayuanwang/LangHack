package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.TeachingPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教学计划Mapper接口
 */
@Mapper
public interface TeachingPlanMapper extends BaseMapper<TeachingPlan> {

    /**
     * 按plan_id唯一键UPSERT
     */
    void upsertByPlanId(TeachingPlan entity);
}
