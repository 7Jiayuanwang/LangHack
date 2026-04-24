package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.SchoolCourse;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学校课程Mapper接口
 */
@Mapper
public interface SchoolCourseMapper extends BaseMapper<SchoolCourse> {

    /**
     * 按course_code唯一键UPSERT
     */
    void upsertByCourseCode(SchoolCourse entity);
}
