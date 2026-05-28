package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.RecommendationEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface RecommendationEventMapper extends BaseMapper<RecommendationEvent> {

    @Select("SELECT source, " +
            "COUNT(CASE WHEN event_type='IMPRESSION' THEN 1 END) AS impressions, " +
            "COUNT(CASE WHEN event_type='CLICK' THEN 1 END) AS clicks " +
            "FROM recommendation_events " +
            "WHERE created_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "GROUP BY source")
    List<Map<String, Object>> selectCtrBySource(@Param("days") int days);

    @Select("SELECT persona_tags, " +
            "COUNT(CASE WHEN event_type='IMPRESSION' THEN 1 END) AS impressions, " +
            "COUNT(CASE WHEN event_type='CLICK' THEN 1 END) AS clicks " +
            "FROM recommendation_events " +
            "WHERE persona_tags IS NOT NULL AND persona_tags != '' " +
            "AND created_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "GROUP BY persona_tags")
    List<Map<String, Object>> selectCtrByPersonaTag(@Param("days") int days);
}
