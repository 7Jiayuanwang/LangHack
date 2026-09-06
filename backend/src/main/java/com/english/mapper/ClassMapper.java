package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.Class;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ClassMapper extends BaseMapper<Class> {

    /**
     * 按 school_class_id 查找已删除的班级（绕过 @TableLogic）
     */
    @Select("SELECT * FROM classes WHERE school_class_id = #{schoolClassId} AND deleted = 1 LIMIT 1")
    Class selectDeletedBySchoolClassId(@Param("schoolClassId") String schoolClassId);

    @Update("UPDATE classes SET deleted = 0, name = #{name}, course_id = #{courseId}, teacher_id = #{teacherId}, description = #{description} WHERE id = #{id}")
    int restoreClass(@Param("id") Long id, @Param("name") String name, @Param("courseId") Long courseId, @Param("teacherId") Long teacherId, @Param("description") String description);

    /**
     * 分页查询班级列表（包含关联的课程和教师信息）
     */
    IPage<Class> selectClassesWithDetails(Page<Class> page, @Param("courseId") Long courseId, @Param("teacherId") Long teacherId, @Param("keyword") String keyword);

    /**
     * 根据ID查询班级详情（包含关联的课程和教师信息）
     */
    Class selectClassWithDetails(@Param("id") Long id);

    /**
     * 根据教师ID查询班级列表
     */
    List<Class> selectClassesByTeacherId(@Param("teacherId") Long teacherId);

    /**
     * 根据学生ID查询班级列表（包含加入时间）
     */
    List<Class> selectClassesByStudentIdWithJoinTime(@Param("studentId") Long studentId);

    /**
     * 查询班级统计信息
     */
    Long selectClassCount();

    @Select("SELECT DISTINCT SUBSTRING_INDEX(name, ' - ', 1) AS label FROM classes WHERE deleted = 0 ORDER BY label")
    List<String> selectDistinctCourseLabels();

    @Select("SELECT c.id, c.name, u.real_name AS teacherName, tc.course_sequence AS courseSeq " +
            "FROM classes c LEFT JOIN users u ON c.teacher_id = u.id AND u.deleted = 0 " +
            "LEFT JOIN teaching_class tc ON c.school_class_id = tc.class_id COLLATE utf8mb4_unicode_ci " +
            "WHERE c.deleted = 0 AND (c.name LIKE CONCAT(#{label}, ' - ', '%') OR c.name = #{label}) " +
            "ORDER BY c.name")
    List<Map<String, Object>> selectClassesByLabel(@Param("label") String label);
}