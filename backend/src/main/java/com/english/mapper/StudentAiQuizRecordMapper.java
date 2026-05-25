package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.StudentAiQuizRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StudentAiQuizRecordMapper extends BaseMapper<StudentAiQuizRecord> {

    /**
     * 按 attempt 查全部明细（按 question_order 排序）
     */
    List<StudentAiQuizRecord> listByAttemptId(@Param("attemptId") Long attemptId);
}
