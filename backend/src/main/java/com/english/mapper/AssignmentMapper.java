package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.Assignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AssignmentMapper extends BaseMapper<Assignment> {

    /**
     * 分页查询任务列表（包含关联的班级、单元信息）
     */
    IPage<Assignment> selectAssignmentsWithDetails(Page<Assignment> page,
                                                   @Param("teacherId") Long teacherId,
                                                   @Param("classId") Long classId,
                                                   @Param("status") String status,
                                                   @Param("keyword") String keyword);

    /**
     * 根据ID查询任务详情（包含关联信息）
     */
    Assignment selectAssignmentWithDetails(@Param("id") Long id);

    /**
     * 查询教师的任务统计信息
     */
    java.util.Map<String, Object> selectTeacherStats(@Param("teacherId") Long teacherId);

    /**
     * 获取教师的班级列表（用于仪表板）
     */
    List<java.util.Map<String, Object>> selectTeacherClassesWithStats(@Param("teacherId") Long teacherId);

    /**
     * 获取教师的最近任务列表
     */
    List<Assignment> selectRecentAssignments(@Param("teacherId") Long teacherId,
                                             @Param("limit") Integer limit);
}