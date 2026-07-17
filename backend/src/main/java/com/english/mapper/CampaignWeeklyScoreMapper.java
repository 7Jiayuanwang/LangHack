package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.CampaignWeeklyScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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
}
