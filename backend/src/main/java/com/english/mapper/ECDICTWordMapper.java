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

    /**
     * 随机获取干扰项（全库）
     * 用于MCQ测验生成干扰选项
     */
    @Select("SELECT id, word, definition, translation, pos FROM ecdict_words " +
            "WHERE translation IS NOT NULL AND translation != '' " +
            "AND id != #{excludeId} " +
            "ORDER BY RAND() LIMIT #{count}")
    List<ECDICTWord> findRandomDistractors(@Param("excludeId") Long excludeId,
                                            @Param("count") int count);

    /**
     * 按词性随机获取干扰项
     * 优先从相同词性中抽取，提高干扰项质量
     */
    @Select("SELECT id, word, definition, translation, pos FROM ecdict_words " +
            "WHERE translation IS NOT NULL AND translation != '' " +
            "AND pos = #{pos} AND id != #{excludeId} " +
            "ORDER BY RAND() LIMIT #{count}")
    List<ECDICTWord> findRandomDistractorsByPos(@Param("pos") String pos,
                                                 @Param("excludeId") Long excludeId,
                                                 @Param("count") int count);
}
