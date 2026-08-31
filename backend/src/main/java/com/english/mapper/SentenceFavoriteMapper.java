package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.SentenceFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SentenceFavoriteMapper extends BaseMapper<SentenceFavorite> {
    @Select("SELECT sf.id, sf.user_id, sf.text_id, sf.sentence, sf.created_time, " +
            "t.title AS source_title FROM sentence_favorites sf " +
            "JOIN texts t ON t.id = sf.text_id AND t.deleted = 0 " +
            "WHERE sf.user_id = #{userId} ORDER BY sf.created_time DESC, sf.id DESC")
    IPage<SentenceFavorite> selectPageWithSource(Page<SentenceFavorite> page, @Param("userId") Long userId);
}
