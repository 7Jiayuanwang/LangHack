package com.english.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.Question;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
