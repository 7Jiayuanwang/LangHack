package com.english.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.entity.CampaignActivity;
import com.english.enums.CampaignStatus;
import com.english.mapper.CampaignActivityMapper;
import com.english.service.CampaignService;
import com.english.service.CampaignStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动数据采集定时任务
 */
@Slf4j
@Component
public class CampaignScheduledTasks {

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private CampaignStatsService statsService;

    @Autowired
    private CampaignActivityMapper campaignActivityMapper;

    @Autowired
    @Qualifier("campaignCacheManager")
    private CacheManager campaignCacheManager;

    /**
     * 每小时自动采集活跃活动的数据
     * cron表达式: 每小时的第3分钟执行（避开整点高峰）
     */
    @Scheduled(cron = "0 3 * * * ?")
    public void collectActiveCampaignData() {
        log.info("开始自动采集活跃活动数据");

        try {
            // 获取所有进行中的活动
            List<CampaignActivity> activeCampaigns = campaignService.getActiveCampaigns();

            if (activeCampaigns.isEmpty()) {
                log.info("当前没有进行中的活动，跳过数据采集");
                return;
            }

            log.info("找到 {} 个进行中的活动，开始采集数据", activeCampaigns.size());

            int successCount = 0;
            int failCount = 0;

            for (CampaignActivity campaign : activeCampaigns) {
                try {
                    // 采集该活动的所有数据
                    statsService.collectAllCampaignData(campaign.getId());
                    successCount++;
                    log.info("活动[{}]数据采集成功", campaign.getTitle());
                } catch (Exception e) {
                    failCount++;
                    log.error("活动[{}]数据采集失败: {}", campaign.getTitle(), e.getMessage(), e);
                }
            }

            log.info("活动数据采集完成，成功: {}, 失败: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("活动数据采集任务执行失败", e);
        }
    }

    /**
     * 每天凌晨检查并自动结束已到期的活动
     * cron表达式: 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void autoEndExpiredCampaigns() {
        log.info("开始检查并结束已到期的活动");

        try {
            LocalDateTime now = LocalDateTime.now();

            // 获取所有进行中但已过结束时间的活动
            List<CampaignActivity> expiredCampaigns = campaignService.getExpiredActiveCampaigns();

            if (expiredCampaigns.isEmpty()) {
                log.info("当前没有需要结束的活动");
                return;
            }

            log.info("找到 {} 个已到期的活动，开始自动结束", expiredCampaigns.size());

            int successCount = 0;
            int failCount = 0;

            for (CampaignActivity campaign : expiredCampaigns) {
                try {
                    // 执行最后一次数据采集
                    statsService.collectAllCampaignData(campaign.getId());

                    // 结束活动
                    campaignService.endCampaign(campaign.getId());

                    successCount++;
                    log.info("活动[{}]已自动结束", campaign.getTitle());
                } catch (Exception e) {
                    failCount++;
                    log.error("活动[{}]自动结束失败: {}", campaign.getTitle(), e.getMessage(), e);
                }
            }

            log.info("活动自动结束完成，成功: {}, 失败: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("活动自动结束任务执行失败", e);
        }
    }

    /**
     * 清理过期的缓存数据
     * cron表达式: 每天凌晨3点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredCache() {
        log.info("开始清理过期的活动缓存数据");

        try {
            // 查询所有已结束和已归档的活动（不再需要实时排名/概览缓存）
            List<CampaignActivity> inactiveCampaigns = campaignActivityMapper.selectList(
                new LambdaQueryWrapper<CampaignActivity>()
                    .in(CampaignActivity::getStatus, CampaignStatus.ENDED, CampaignStatus.ARCHIVED)
                    .eq(CampaignActivity::getDeleted, 0)
            );

            if (inactiveCampaigns.isEmpty()) {
                log.info("当前没有需要清理缓存的活动");
                return;
            }

            Cache rankingCache = campaignCacheManager.getCache("campaign_ranking");
            Cache overviewCache = campaignCacheManager.getCache("campaign_overview");
            Cache statsCache = campaignCacheManager.getCache("campaign_stats");

            int cleared = 0;
            for (CampaignActivity campaign : inactiveCampaigns) {
                Long campaignId = campaign.getId();
                // 缓存 key 为 campaignId（Long），见 @Cacheable key="#campaignId"
                if (rankingCache != null) {
                    rankingCache.evictIfPresent(campaignId);
                }
                if (overviewCache != null) {
                    overviewCache.evictIfPresent(campaignId);
                }
                if (statsCache != null) {
                    statsCache.evictIfPresent(campaignId);
                }
                cleared++;
            }

            log.info("活动缓存清理完成，共清理 {} 个已结束/已归档活动的缓存", cleared);

        } catch (Exception e) {
            log.error("活动缓存清理失败", e);
        }
    }
}
