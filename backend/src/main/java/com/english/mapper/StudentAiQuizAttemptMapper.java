package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.StudentAiQuizAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface StudentAiQuizAttemptMapper extends BaseMapper<StudentAiQuizAttempt> {

    /**
     * 管理员端分页查询做题尝试（含学生姓名、文章标题）
     * 任一过滤条件为 null 即忽略
     */
    List<Map<String, Object>> queryAttemptsForAdmin(@Param("studentId") Long studentId,
                                                    @Param("textId") Long textId,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    /**
     * 管理员端分页总数
     */
    long countAttemptsForAdmin(@Param("studentId") Long studentId,
                               @Param("textId") Long textId,
                               @Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);
}
