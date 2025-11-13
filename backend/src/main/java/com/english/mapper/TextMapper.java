package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.Text;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TextMapper extends BaseMapper<Text> {
}