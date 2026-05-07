package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.Unit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UnitMapper extends BaseMapper<Unit> {

    /**
     * 确保占位课程存在（用户上传内容需要）
     * INSERT IGNORE 保证幂等和并发安全
     */
    @Insert("INSERT IGNORE INTO courses (id, name, description, status, deleted) " +
            "VALUES (#{courseId}, '用户上传内容', '系统自动创建的占位课程，用于存放用户上传的内容', 'ACTIVE', 0)")
    void ensurePlaceholderCourseExists(@Param("courseId") Long courseId);

    /**
     * 确保占位单元存在（用户上传内容需要）
     * INSERT IGNORE 保证幂等和并发安全
     */
    @Insert("INSERT IGNORE INTO units (id, course_id, name, description, order_num, deleted) " +
            "VALUES (#{unitId}, #{courseId}, '用户上传', '系统自动创建的占位单元，用于存放用户上传的课文', 0, 0)")
    void ensurePlaceholderUnitExists(@Param("unitId") Long unitId, @Param("courseId") Long courseId);
}