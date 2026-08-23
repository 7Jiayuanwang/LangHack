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
}