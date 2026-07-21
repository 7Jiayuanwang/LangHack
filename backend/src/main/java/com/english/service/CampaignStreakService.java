package com.english.service;

import java.math.BigDecimal;

/**
 * 连续学习加成服务
 */
public interface CampaignStreakService {

    /**
     * 记录用户当日活跃并更新连续天数
     */
    void recordDailyActive(Long campaignId, Long userId);

    /**
     * 获取用户当前的积分加成系数
     */
    BigDecimal getCurrentMultiplier(Long campaignId, Long userId);

    /**
     * 获取用户本周连续天数
     */
    int getStreakDays(Long campaignId, Long userId);
}
