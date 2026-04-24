package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.CourseSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 课程安排Mapper接口
 */
@Mapper
public interface CourseScheduleMapper extends BaseMapper<CourseSchedule> {

    /**
     * 按class_id+academic_year+semester删除课程安排
     */
    void deleteByClassIdAndSemester(@Param("classId") String classId,
                                    @Param("academicYear") String academicYear,
                                    @Param("semester") Integer semester);
}
