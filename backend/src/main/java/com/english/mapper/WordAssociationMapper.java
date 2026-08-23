package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.WordAssociation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 单词联想 Mapper
 */
@Mapper
public interface WordAssociationMapper extends BaseMapper<WordAssociation> {

    /**
     * 根据单词和类型查询联想数据
     */
    @Select("SELECT * FROM word_associations WHERE LOWER(word) = LOWER(#{word}) AND type = #{type} AND deleted = 0")
    List<WordAssociation> findByWordAndType(@Param("word") String word, @Param("type") String type);

    /**
     * 根据单词查询所有联想数据（全部类型）
     */
    @Select("SELECT * FROM word_associations WHERE LOWER(word) = LOWER(#{word}) AND deleted = 0 ORDER BY type, id")
    List<WordAssociation> findByWord(@Param("word") String word);

    /**
     * 检查关联是否已存在
     */
    @Select("SELECT COUNT(*) FROM word_associations WHERE LOWER(word) = LOWER(#{word}) AND type = #{type} AND LOWER(associated_word) = LOWER(#{associatedWord}) AND deleted = 0")
    int existsByWordTypeAndAssociated(@Param("word") String word, @Param("type") String type, @Param("associatedWord") String associatedWord);
}
