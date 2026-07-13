package com.english.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 活动系统缓存配置
 *
 * 历史：早先用 ConcurrentMapCache，它不支持 TTL，一旦某条排名把
 * className/collegeName 为 null 的 record 缓存下来（例如学生首次
 * 参与活动时 school_student_id 还没绑定），后续即便名册补齐，
 * 排名接口仍持续吐旧缓存直到应用重启。改用 Caffeine 让 5 分钟 TTL 生效。
 */
@Configuration
@EnableCaching
public class CampaignCacheConfig {

    @Bean(name = "campaignCacheManager")
    public CacheManager campaignCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
            "campaign_ranking",
            "campaign_overview",
            "campaign_stats"
        );
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000)
        );
        return manager;
    }
}
