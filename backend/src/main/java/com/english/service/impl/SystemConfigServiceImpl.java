package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.english.entity.SystemConfig;
import com.english.mapper.SystemConfigMapper;
import com.english.service.SystemConfigService;
import com.english.service.FeatureToggleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigServiceImpl.class);
    private static final long CACHE_TTL_MS = 60_000; // 60 seconds

    @Autowired
    private SystemConfigMapper mapper;

    @Autowired
    @Lazy
    private FeatureToggleService featureToggleService;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public Map<String, String> getByGroup(String group) {
        CacheEntry entry = cache.get(group);
        if (entry != null && !entry.isExpired()) {
            return entry.data;
        }

        List<SystemConfig> configs = mapper.selectList(
                new LambdaQueryWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigGroup, group)
        );

        Map<String, String> result = new HashMap<>();
        for (SystemConfig config : configs) {
            result.put(config.getConfigKey(), config.getConfigValue());
        }

        cache.put(group, new CacheEntry(result));
        return result;
    }

    @Override
    public String get(String group, String key) {
        return getByGroup(group).get(key);
    }

    @Override
    public String get(String group, String key, String defaultValue) {
        String value = get(group, key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    @Override
    public void set(String group, String key, String value, String updatedBy) {
        int updated = mapper.update(null,
                new LambdaUpdateWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigGroup, group)
                        .eq(SystemConfig::getConfigKey, key)
                        .set(SystemConfig::getConfigValue, value)
                        .set(SystemConfig::getUpdatedBy, updatedBy)
                        .set(SystemConfig::getUpdatedTime, LocalDateTime.now())
        );

        if (updated == 0) {
            // Key doesn't exist yet, insert it
            SystemConfig config = new SystemConfig();
            config.setConfigGroup(group);
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setUpdatedBy(updatedBy);
            config.setUpdatedTime(LocalDateTime.now());
            mapper.insert(config);
        }

        cache.remove(group);
        
        // 如果是功能开关配置更新，通知FeatureToggleService清除相关缓存
        if ("feature_toggle".equals(group)) {
            try {
                // 使用反射调用FeatureToggleServiceImpl的evictAllCache方法
                if (featureToggleService instanceof com.english.service.impl.FeatureToggleServiceImpl) {
                    ((com.english.service.impl.FeatureToggleServiceImpl) featureToggleService).evictAllCache();
                }
            } catch (Exception e) {
                logger.warn("Failed to evict feature toggle cache: {}", e.getMessage());
            }
        }
        
        logger.info("System config updated: {}.{} by {}", group, key, updatedBy);
    }

    @Override
    public List<SystemConfig> listByGroup(String group) {
        List<SystemConfig> configs = mapper.selectList(
                new LambdaQueryWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigGroup, group)
                        .orderByAsc(SystemConfig::getId)
        );
        normalizeDescriptions(configs);
        return configs;
    }

    @Override
    public void batchUpdate(List<SystemConfig> configs, String updatedBy) {
        Set<String> groupsToInvalidate = new HashSet<>();

        for (SystemConfig config : configs) {
            if (config.getConfigGroup() == null || config.getConfigKey() == null) {
                continue;
            }

            mapper.update(null,
                    new LambdaUpdateWrapper<SystemConfig>()
                            .eq(SystemConfig::getConfigGroup, config.getConfigGroup())
                            .eq(SystemConfig::getConfigKey, config.getConfigKey())
                            .set(SystemConfig::getConfigValue, config.getConfigValue())
                            .set(SystemConfig::getUpdatedBy, updatedBy)
                            .set(SystemConfig::getUpdatedTime, LocalDateTime.now())
            );

            groupsToInvalidate.add(config.getConfigGroup());
        }

        // Clear cache for all affected groups
        for (String group : groupsToInvalidate) {
            cache.remove(group);
        }

        // 如果包含功能开关配置更新，通知FeatureToggleService清除相关缓存
        if (groupsToInvalidate.contains("feature_toggle")) {
            try {
                // 使用反射调用FeatureToggleServiceImpl的evictAllCache方法
                if (featureToggleService instanceof com.english.service.impl.FeatureToggleServiceImpl) {
                    ((com.english.service.impl.FeatureToggleServiceImpl) featureToggleService).evictAllCache();
                }
            } catch (Exception e) {
                logger.warn("Failed to evict feature toggle cache: {}", e.getMessage());
            }
        }

        logger.info("Batch updated {} configs by {}", configs.size(), updatedBy);
    }

    private static class CacheEntry {
        final Map<String, String> data;
        final long createdAt;

        CacheEntry(Map<String, String> data) {
            this.data = Collections.unmodifiableMap(data);
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }

    private void normalizeDescriptions(List<SystemConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        for (SystemConfig config : configs) {
            if (config == null) {
                continue;
            }
            config.setDescription(repairMojibake(config.getDescription()));
        }
    }

    private String repairMojibake(String text) {
        if (text == null || text.isBlank() || !looksLikeMojibake(text)) {
            return text;
        }
        String latin1Candidate = tryRepair(text, StandardCharsets.ISO_8859_1);
        String windowsCandidate = tryRepair(text, Charset.forName("windows-1252"));
        String repaired = betterCandidate(latin1Candidate, windowsCandidate);
        return containsCjk(repaired) ? repaired : text;
    }

    private boolean looksLikeMojibake(String text) {
        return text.contains("Ã")
                || text.contains("Â")
                || text.contains("å")
                || text.contains("ä")
                || text.contains("ç")
                || text.contains("ï")
                || text.contains("ð");
    }

    private boolean containsCjk(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.codePoints().anyMatch(cp ->
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    private String tryRepair(String text, Charset sourceCharset) {
        try {
            return new String(text.getBytes(sourceCharset), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return text;
        }
    }

    private String betterCandidate(String first, String second) {
        int firstScore = scoreCandidate(first);
        int secondScore = scoreCandidate(second);
        return secondScore > firstScore ? second : first;
    }

    private int scoreCandidate(String text) {
        if (text == null || text.isBlank()) {
            return Integer.MIN_VALUE;
        }
        int cjkCount = (int) text.codePoints().filter(cp ->
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN).count();
        int replacementPenalty = text.contains("�") ? 10 : 0;
        return cjkCount * 10 - replacementPenalty;
    }
}
