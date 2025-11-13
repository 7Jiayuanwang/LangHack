package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.StudentClass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StudentClassMapper extends BaseMapper<StudentClass> {

    /**
     * 分页查询学生班级关联列表（包含学生和班级信息）
     */
    IPage<StudentClass> selectStudentClassesWithDetails(Page<StudentClass> page, @Param("classId") Long classId, @Param("studentId") Long studentId, @Param("keyword") String keyword);

    /**
     * 根据班级ID查询学生列表
     */
    List<StudentClass> selectStudentsByClassId(@Param("classId") Long classId);

    /**
     * 根据学生ID查询班级列表
     */
    List<StudentClass> selectClassesByStudentId(@Param("studentId") Long studentId);

    /**
     * 检查学生是否已在班级中
     */
    Integer checkStudentInClass(@Param("studentId") Long studentId, @Param("classId") Long classId);

    /**
     * 获取班级学生数量
     */
    Long selectStudentCountByClassId(@Param("classId") Long classId);

    /**
     * 获取学生加入的班级数量
     */
    Long selectClassCountByStudentId(@Param("studentId") Long studentId);
}