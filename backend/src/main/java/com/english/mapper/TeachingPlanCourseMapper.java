package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.TeachingPlanCourse;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教学计划课程明细Mapper接口
 */
@Mapper
public interface TeachingPlanCourseMapper extends BaseMapper<TeachingPlanCourse> {

    /**
     * 按detail_id唯一键UPSERT
     */
    void upsertByDetailId(TeachingPlanCourse entity);
}
