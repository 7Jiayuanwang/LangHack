package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.AiFeedback;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface AiFeedbackMapper extends BaseMapper<AiFeedback> {

    Map<String, Object> selectFeedbackSummary();
}
