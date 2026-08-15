package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    @Select("SELECT * FROM courses WHERE school_course_code = #{code} AND deleted = 1 LIMIT 1")
    Course selectDeletedBySchoolCourseCode(@Param("code") String code);

    @Update("UPDATE courses SET deleted = 0, name = #{name}, status = 'ACTIVE' WHERE id = #{id}")
    int restoreById(@Param("id") Long id, @Param("name") String name);
}