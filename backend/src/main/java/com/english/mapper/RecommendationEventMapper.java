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

    @Select("SELECT DISTINCT text_id FROM recommendation_events " +
            "WHERE user_id = #{userId} AND event_type = 'DISMISS' " +
            "AND source IN ('FEED_FOR_YOU', 'FEED_EXPLORE', 'FEED_CATEGORY') " +
            "AND created_time >= DATE_SUB(NOW(), INTERVAL 90 DAY) " +
            "LIMIT 500")
    List<Long> selectRecentFeedDismissals(@Param("userId") Long userId);

    @Select("SELECT " +
            "COUNT(DISTINCT CASE WHEN e.event_type = 'IMPRESSION' " +
            "THEN CONCAT(e.user_id, ':', e.text_id) END) AS exposedPairs, " +
            "COUNT(DISTINCT CASE WHEN e.event_type = 'CLICK' " +
            "THEN CONCAT(e.user_id, ':', e.text_id) END) AS clickedPairs, " +
            "COUNT(DISTINCT CASE WHEN e.event_type = 'CLICK' AND EXISTS (" +
            "SELECT 1 FROM text_learning_records r " +
            "WHERE r.student_id = e.user_id AND r.text_id = e.text_id " +
            "AND r.is_completed = 1 AND r.end_time >= e.created_time " +
            "AND r.end_time <= DATE_ADD(e.created_time, INTERVAL 7 DAY)) " +
            "THEN CONCAT(e.user_id, ':', e.text_id) END) AS completedPairs " +
            "FROM recommendation_events e " +
            "WHERE e.source IN ('FEED_FOR_YOU', 'FEED_EXPLORE', 'FEED_CATEGORY') " +
            "AND e.created_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    Map<String, Object> selectFeedLearningMetrics(@Param("days") int days);
}
