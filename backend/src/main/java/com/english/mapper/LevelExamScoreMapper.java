package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.LevelExamScore;
import org.apache.ibatis.annotations.Mapper;

/**
 * 等级考试成绩Mapper接口
 */
@Mapper
public interface LevelExamScoreMapper extends BaseMapper<LevelExamScore> {

    /**
     * 按student_id+exam_type+exam_date唯一键UPSERT
     */
    void upsertByStudentExam(LevelExamScore entity);
}
