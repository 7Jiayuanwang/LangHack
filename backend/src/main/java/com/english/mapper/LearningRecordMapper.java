package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.LearningRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习记录Mapper接口
 */
@Mapper
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {
}