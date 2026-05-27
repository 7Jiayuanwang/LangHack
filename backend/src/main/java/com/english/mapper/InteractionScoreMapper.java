package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.InteractionScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface InteractionScoreMapper extends BaseMapper<InteractionScore> {

    @Select("SELECT " +
            "  CASE " +
            "    WHEN interaction_score < 1 THEN '0-1' " +
            "    WHEN interaction_score < 3 THEN '1-3' " +
            "    WHEN interaction_score < 5 THEN '3-5' " +
            "    WHEN interaction_score < 8 THEN '5-8' " +
            "    ELSE '8+' END AS score_range, " +
            "  COUNT(*) AS cnt " +
            "FROM v_user_text_interaction_scores " +
            "GROUP BY score_range " +
            "ORDER BY MIN(interaction_score)")
    List<Map<String, Object>> selectScoreDistribution();
}
