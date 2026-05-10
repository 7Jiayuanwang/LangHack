package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.TeacherStudentBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 教师-学生绑定 Mapper。
 */
@Mapper
public interface TeacherStudentBindingMapper extends BaseMapper<TeacherStudentBinding> {

    /**
     * 按唯一键 UPSERT（ON DUPLICATE KEY UPDATE sync_time）。
     */
    void upsertBinding(TeacherStudentBinding entity);

    /**
     * 查询某教师的所有学生（可按学年/学期筛选，NULL 视为不筛）。
     */
    List<TeacherStudentBinding> selectByTeacher(@Param("teacherId") String teacherId,
                                                @Param("academicYear") String academicYear,
                                                @Param("semester") String semester);

    /**
     * 查询某学生的所有任课教师。
     */
    List<TeacherStudentBinding> selectByStudent(@Param("studentId") String studentId,
                                                @Param("academicYear") String academicYear,
                                                @Param("semester") String semester);

    /**
     * 查询所有不重复的学院名称（用于筛选下拉）。
     */
    List<String> selectDistinctColleges();
}
