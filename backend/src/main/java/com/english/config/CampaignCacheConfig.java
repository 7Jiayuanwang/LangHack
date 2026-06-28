package com.english.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * 活动系统缓存配置
 */
@Configuration
public class CampaignCacheConfig {

    /**
     * 配置缓存管理器
     * 为活动排名等数据配置5分钟TTL的缓存
     */
    @Bean(name = "campaignCacheManager")
    public CacheManager campaignCacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
            new ConcurrentMapCache("campaign_ranking"),
            new ConcurrentMapCache("campaign_overview"),
            new ConcurrentMapCache("campaign_stats")
        ));
        return cacheManager;
    }
}
