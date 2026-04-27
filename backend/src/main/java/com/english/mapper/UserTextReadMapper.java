package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.UserTextRead;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserTextReadMapper extends BaseMapper<UserTextRead> {

    @Select("SELECT text_id FROM user_text_reads WHERE user_id = #{userId}")
    List<Long> selectReadTextIds(@Param("userId") Long userId);

    @Delete("DELETE FROM user_text_reads WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 统计用户已读文章的难度众数（只返回出现最多的那一个 difficulty）。
     * 比加载全部 Text 行再在内存里分组便宜得多（content 字段是长文本）。
     */
    @Select("SELECT t.difficulty FROM texts t " +
            "INNER JOIN user_text_reads r ON r.text_id = t.id " +
            "WHERE r.user_id = #{userId} AND t.difficulty IS NOT NULL AND t.difficulty <> '' " +
            "GROUP BY t.difficulty ORDER BY COUNT(*) DESC LIMIT 1")
    String selectMostReadDifficulty(@Param("userId") Long userId);
}

