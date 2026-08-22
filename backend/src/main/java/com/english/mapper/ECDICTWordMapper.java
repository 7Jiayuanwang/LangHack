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

    /**
     * 按前缀查询单词（用于同前缀词联想）
     */
    @Select("SELECT word, translation, pos FROM ecdict_words " +
            "WHERE LOWER(word) LIKE CONCAT(LOWER(#{prefix}), '%') " +
            "AND translation IS NOT NULL AND translation != '' " +
            "AND LENGTH(word) > LENGTH(#{prefix}) + 1 " +
            "ORDER BY LENGTH(word), word LIMIT #{limit}")
    List<ECDICTWord> findByPrefix(@Param("prefix") String prefix, @Param("limit") int limit);

    /**
     * 按后缀查询单词（用于同后缀词联想）
     */
    @Select("SELECT word, translation, pos FROM ecdict_words " +
            "WHERE LOWER(word) LIKE CONCAT('%', LOWER(#{suffix})) " +
            "AND translation IS NOT NULL AND translation != '' " +
            "AND LENGTH(word) > LENGTH(#{suffix}) + 1 " +
            "ORDER BY LENGTH(word), word LIMIT #{limit}")
    List<ECDICTWord> findBySuffix(@Param("suffix") String suffix, @Param("limit") int limit);

    /**
     * 按词根查询单词（用于同根词联想，单词中包含词根）
     */
    @Select("SELECT word, translation, pos FROM ecdict_words " +
            "WHERE LOWER(word) LIKE CONCAT('%', LOWER(#{root}), '%') " +
            "AND translation IS NOT NULL AND translation != '' " +
            "AND LOWER(word) != LOWER(#{excludeWord}) " +
            "ORDER BY LENGTH(word), word LIMIT #{limit}")
    List<ECDICTWord> findByRoot(@Param("root") String root,
                                 @Param("excludeWord") String excludeWord,
                                 @Param("limit") int limit);
}
