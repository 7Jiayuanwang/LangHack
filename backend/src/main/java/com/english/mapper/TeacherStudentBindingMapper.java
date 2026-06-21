package com.english.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    /**
     * 分页查询绑定关系，并在内存层用 school_teacher / school_student 回填 teacher_name / student_name。
     * 注意：users 表是 utf8mb4_unicode_ci，与本表 utf8mb4_0900_ai_ci 不能直接 JOIN，因此 users 的回填
     * 由 Service 层在内存中完成（按用户名匹配）。
     */
    Page<TeacherStudentBinding> selectPageWithNames(Page<TeacherStudentBinding> page,
                                                    @Param("ew") Wrapper<TeacherStudentBinding> wrapper);
}
