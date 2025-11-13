package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.VocabularyBook;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生词本Mapper接口
 */
@Mapper
public interface VocabularyBookMapper extends BaseMapper<VocabularyBook> {
}