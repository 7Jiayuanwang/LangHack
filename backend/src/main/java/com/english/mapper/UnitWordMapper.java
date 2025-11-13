package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.UnitWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 单元单词关联Mapper接口
 */
@Mapper
public interface UnitWordMapper extends BaseMapper<UnitWord> {
}