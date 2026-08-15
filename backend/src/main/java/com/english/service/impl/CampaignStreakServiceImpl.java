package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.CampaignActivity;
import com.english.entity.CampaignStreakRecord;
import com.english.mapper.CampaignStreakRecordMapper;
import com.english.service.CampaignService;
import com.english.service.CampaignStreakService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class CampaignStreakServiceImpl implements CampaignStreakService {

    @Autowired
    private CampaignStreakRecordMapper streakRecordMapper;

    @Autowired
    @Lazy
    private CampaignService campaignService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void recordDailyActive(Long campaignId, Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate periodStart = calculatePeriodStart(campaignId, today);

        CampaignStreakRecord record = getOrCreateRecord(campaignId, userId, periodStart);

        List<String> activeDateList = parseActiveDates(record.getActiveDates());
        String todayStr = today.toString();
        if (activeDateList.contains(todayStr)) {
            return;
        }

        activeDateList.add(todayStr);
        activeDateList.sort(String::compareTo);

        int streakDays = calculateStreakDays(activeDateList, today);
        BigDecimal multiplier = calculateMultiplier(campaignId, streakDays);

        record.setActiveDates(toJson(activeDateList));
        record.setStreakDays(streakDays);
        record.setCurrentMultiplier(multiplier);
        record.setLastActiveDate(today);

        if (record.getId() == null) {
            streakRecordMapper.insert(record);
        } else {
            streakRecordMapper.updateById(record);
        }
    }

    @Override
    public BigDecimal getCurrentMultiplier(Long campaignId, Long userId) {
        CampaignActivity campaign = campaignService.getCampaignById(campaignId);
        if (campaign == null || !Boolean.TRUE.equals(campaign.getStreakEnabled())) {
            return BigDecimal.ONE;
        }

        LocalDate today = LocalDate.now();
        LocalDate periodStart = calculatePeriodStart(campaignId, today);

        QueryWrapper<CampaignStreakRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        wrapper.eq("week_start", periodStart);
        CampaignStreakRecord record = streakRecordMapper.selectOne(wrapper);

        if (record == null) {
            return BigDecimal.ONE;
        }

        List<String> activeDateList = parseActiveDates(record.getActiveDates());
        int streakDays = calculateStreakDays(activeDateList, today);
        return calculateMultiplier(campaignId, streakDays);
    }

    @Override
    public int getStreakDays(Long campaignId, Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate periodStart = calculatePeriodStart(campaignId, today);

        QueryWrapper<CampaignStreakRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        wrapper.eq("week_start", periodStart);
        CampaignStreakRecord record = streakRecordMapper.selectOne(wrapper);

        if (record == null) {
            return 0;
        }
        return record.getStreakDays() != null ? record.getStreakDays() : 0;
    }

    /**
     * 根据活动配置的重置周期计算当前周期起始日。
     * 以活动开始时间为锚点，每隔 streakResetDays 天为一个周期。
     * 若活动无开始时间则按自然周（周一）计算。
     */
    private LocalDate calculatePeriodStart(Long campaignId, LocalDate today) {
        CampaignActivity campaign = campaignService.getCampaignById(campaignId);
        int resetDays = 7;
        if (campaign != null && campaign.getStreakResetDays() != null && campaign.getStreakResetDays() > 0) {
            resetDays = campaign.getStreakResetDays();
        }

        // 优先使用管理员设定的加成起始日期，其次活动开始时间，最后本周一
        LocalDate anchor;
        if (campaign != null && campaign.getStreakStartDate() != null) {
            anchor = campaign.getStreakStartDate();
        } else if (campaign != null && campaign.getStartTime() != null) {
            anchor = campaign.getStartTime().toLocalDate();
        } else {
            return today.with(DayOfWeek.MONDAY);
        }

        long daysSinceAnchor = today.toEpochDay() - anchor.toEpochDay();
        if (daysSinceAnchor < 0) {
            return today;
        }
        long periodsElapsed = daysSinceAnchor / resetDays;
        return anchor.plusDays(periodsElapsed * resetDays);
    }

    private CampaignStreakRecord getOrCreateRecord(Long campaignId, Long userId, LocalDate weekStart) {
        QueryWrapper<CampaignStreakRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        wrapper.eq("week_start", weekStart);
        CampaignStreakRecord record = streakRecordMapper.selectOne(wrapper);

        if (record == null) {
            record = new CampaignStreakRecord();
            record.setCampaignId(campaignId);
            record.setUserId(userId);
            record.setWeekStart(weekStart);
            record.setStreakDays(0);
            record.setCurrentMultiplier(BigDecimal.ONE);
            record.setActiveDates("[]");
        }
        return record;
    }

    /**
     * 计算连续天数（含容错：断签1天回退一级，断签2天+归零）
     */
    private int calculateStreakDays(List<String> activeDateList, LocalDate today) {
        if (activeDateList.isEmpty()) {
            return 0;
        }

        int streak = 0;
        LocalDate checkDate = today;

        for (int i = activeDateList.size() - 1; i >= 0; i--) {
            LocalDate activeDate = LocalDate.parse(activeDateList.get(i));
            long gap = checkDate.toEpochDay() - activeDate.toEpochDay();

            if (gap == 0) {
                streak++;
                checkDate = activeDate.minusDays(1);
            } else if (gap == 1 && streak > 0) {
                // 容错：允许断签1天，但不增加连续天数
                checkDate = activeDate.minusDays(1);
                // 不增加 streak，但继续往前追溯
                i++;
                break;
            } else {
                break;
            }
        }

        return streak;
    }

    /**
     * 根据活动配置的阶梯计算积分系数
     */
    private BigDecimal calculateMultiplier(Long campaignId, int streakDays) {
        if (streakDays <= 1) {
            return BigDecimal.ONE;
        }

        CampaignActivity campaign = campaignService.getCampaignById(campaignId);
        if (campaign == null || !Boolean.TRUE.equals(campaign.getStreakEnabled())) {
            return BigDecimal.ONE;
        }

        BigDecimal maxMultiplier = campaign.getStreakMaxMultiplier() != null
                ? campaign.getStreakMaxMultiplier()
                : new BigDecimal("1.30");

        List<StreakTier> tiers = parseStreakConfig(campaign.getStreakConfig());

        if (tiers.isEmpty()) {
            // 默认阶梯：每天 +0.05，从第2天开始
            BigDecimal multiplier = BigDecimal.ONE.add(
                    new BigDecimal("0.05").multiply(BigDecimal.valueOf(streakDays - 1))
            );
            return multiplier.min(maxMultiplier);
        }

        // 按天数降序查找匹配的阶梯
        tiers.sort(Comparator.comparingInt(StreakTier::getDay).reversed());
        for (StreakTier tier : tiers) {
            if (streakDays >= tier.getDay()) {
                return new BigDecimal(tier.getMultiplier()).min(maxMultiplier);
            }
        }

        return BigDecimal.ONE;
    }

    private List<StreakTier> parseStreakConfig(String config) {
        if (config == null || config.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(config, new TypeReference<List<StreakTier>>() {});
        } catch (Exception e) {
            log.warn("解析 streak_config 失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<String> parseActiveDates(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    @lombok.Data
    private static class StreakTier {
        private int day;
        private String multiplier;
    }
}
