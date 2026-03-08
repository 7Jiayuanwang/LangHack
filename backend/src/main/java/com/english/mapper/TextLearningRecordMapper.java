package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.TextLearningRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章学习记录Mapper接口
 */
@Mapper
public interface TextLearningRecordMapper extends BaseMapper<TextLearningRecord> {
}
