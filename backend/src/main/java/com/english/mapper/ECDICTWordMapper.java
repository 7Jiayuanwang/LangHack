package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.ECDICTWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ECDICTWordMapper extends BaseMapper<ECDICTWord> {

    /**
     * 根据单词查询（不区分大小写）
     */
    @Select("SELECT * FROM ecdict_words WHERE LOWER(word) = LOWER(#{word}) LIMIT 1")
    ECDICTWord findByWord(@Param("word") String word);

    /**
     * 模糊搜索单词
     */
    @Select("SELECT * FROM ecdict_words WHERE LOWER(word) LIKE CONCAT('%', LOWER(#{keyword}), '%') " +
            "OR translation LIKE CONCAT('%', #{keyword}, '%') " +
            "OR definition LIKE CONCAT('%', #{keyword}, '%') " +
            "LIMIT #{limit}")
    List<ECDICTWord> searchWords(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 获取词汇总数
     */
    @Select("SELECT COUNT(*) FROM ecdict_words")
    long getTotalCount();
}
