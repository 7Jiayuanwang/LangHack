package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.CampaignWeeklyScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 活动每周积分 Mapper
 */
@Mapper
public interface CampaignWeeklyScoreMapper extends BaseMapper<CampaignWeeklyScore> {

    /**
     * 累加周积分（INSERT ... ON DUPLICATE KEY UPDATE）
     */
    @Update("INSERT INTO campaign_weekly_scores (campaign_id, user_id, week_start, score) " +
            "VALUES (#{campaignId}, #{userId}, #{weekStart}, #{scoreIncrement}) " +
            "ON DUPLICATE KEY UPDATE score = score + #{scoreIncrement}")
    int addWeeklyScore(@Param("campaignId") Long campaignId,
                       @Param("userId") Long userId,
                       @Param("weekStart") java.time.LocalDate weekStart,
                       @Param("scoreIncrement") int scoreIncrement);

    /**
     * 有周积分记录的周起始日列表（周一，降序）
     */
    @Select("SELECT DATE_FORMAT(week_start, '%Y-%m-%d') FROM campaign_weekly_scores " +
            "WHERE campaign_id = #{campaignId} GROUP BY week_start ORDER BY week_start DESC")
    List<String> selectWeekStarts(@Param("campaignId") Long campaignId);
}
