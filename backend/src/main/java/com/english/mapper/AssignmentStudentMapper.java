package com.english.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AssignmentStudentMapper {
    @Select("SELECT COUNT(*) FROM assignment_students WHERE assignment_id = #{assignmentId} AND student_id = #{studentId}")
    int countAssignmentForStudent(@Param("assignmentId") Long assignmentId, @Param("studentId") Long studentId);

    @Select("SELECT student_id FROM assignment_students WHERE assignment_id = #{assignmentId}")
    List<Long> selectStudentIds(@Param("assignmentId") Long assignmentId);

    @Select("SELECT assignment_id FROM assignment_students WHERE student_id = #{studentId}")
    List<Long> selectAssignmentIdsForStudent(@Param("studentId") Long studentId);

    @Insert({"<script>", "INSERT IGNORE INTO assignment_students (assignment_id, student_id) VALUES", "<foreach collection='studentIds' item='studentId' separator=','>(#{assignmentId}, #{studentId})</foreach>", "</script>"})
    int insertStudents(@Param("assignmentId") Long assignmentId, @Param("studentIds") List<Long> studentIds);

    @Delete("DELETE FROM assignment_students WHERE assignment_id = #{assignmentId}")
    int deleteStudents(@Param("assignmentId") Long assignmentId);
}
