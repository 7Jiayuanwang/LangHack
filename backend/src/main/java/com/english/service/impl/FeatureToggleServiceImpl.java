package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.english.entity.FeatureOverride;
import com.english.entity.UserTag;
import com.english.mapper.FeatureOverrideMapper;
import com.english.mapper.UserTagMapper;
import com.english.service.FeatureToggleService;
import com.english.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FeatureToggleServiceImpl implements FeatureToggleService {

    private static final Logger logger = LoggerFactory.getLogger(FeatureToggleServiceImpl.class);
    private static final long CACHE_TTL_MS = 30_000; // 缩短缓存时间到30秒，提高响应性

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private UserTagMapper userTagMapper;

    @Autowired
    private FeatureOverrideMapper featureOverrideMapper;

    // 标签级缓存: tagName -> CacheEntry<featureKey, enabled>
    private final ConcurrentHashMap<String, CacheEntry> tagCache = new ConcurrentHashMap<>();
    // 用户级缓存: userId -> CacheEntry<featureKey, enabled>
    private final ConcurrentHashMap<Long, CacheEntry> userCache = new ConcurrentHashMap<>();

    @Override
    public Map<String, Boolean> getMergedTogglesForUser(Long userId) {
        // 1. 全局 toggles（由 SystemConfigService 负责缓存）
        Map<String, String> globalRaw = systemConfigService.getByGroup("feature_toggle");
        Map<String, Boolean> merged = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : globalRaw.entrySet()) {
            merged.put(entry.getKey(), "true".equals(entry.getValue()));
        }

        // 2. 标签层覆盖
        List<String> tags = userTagMapper.selectTagsByUserId(userId);
        for (String tag : tags) {
            Map<String, Boolean> tagOverrides = getTagOverrides(tag);
            merged.putAll(tagOverrides);
        }

        // 3. 用户层覆盖
        Map<String, Boolean> userOverrides = getUserOverrides(userId);
        merged.putAll(userOverrides);

        return merged;
    }

    private Map<String, Boolean> getTagOverrides(String tagName) {
        CacheEntry entry = tagCache.get(tagName);
        if (entry != null && !entry.isExpired()) {
            return entry.data;
        }
        List<FeatureOverride> overrides = featureOverrideMapper.selectList(
                new LambdaQueryWrapper<FeatureOverride>()
                        .eq(FeatureOverride::getScope, "tag")
                        .eq(FeatureOverride::getScopeValue, tagName)
        );
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (FeatureOverride o : overrides) {
            result.put(o.getFeatureKey(), o.getEnabled() != null && o.getEnabled() == 1);
        }
        tagCache.put(tagName, new CacheEntry(result));
        return result;
    }

    private Map<String, Boolean> getUserOverrides(Long userId) {
        CacheEntry entry = userCache.get(userId);
        if (entry != null && !entry.isExpired()) {
            return entry.data;
        }
        List<FeatureOverride> overrides = featureOverrideMapper.selectList(
                new LambdaQueryWrapper<FeatureOverride>()
                        .eq(FeatureOverride::getScope, "user")
                        .eq(FeatureOverride::getScopeValue, userId.toString())
        );
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (FeatureOverride o : overrides) {
            result.put(o.getFeatureKey(), o.getEnabled() != null && o.getEnabled() == 1);
        }
        userCache.put(userId, new CacheEntry(result));
        return result;
    }

    // ---------- 标签管理 ----------

    @Override
    public List<String> listAllTagNames() {
        return userTagMapper.selectAllTagNames();
    }

    @Override
    public List<Long> getUserIdsByTag(String tagName) {
        return userTagMapper.selectUserIdsByTag(tagName);
    }

    @Override
    public List<String> getUserTags(Long userId) {
        return userTagMapper.selectTagsByUserId(userId);
    }

    @Override
    @Transactional
    public void setUserTags(Long userId, List<String> tags, String operator) {
        // 先删除该用户的所有标签
        userTagMapper.delete(new LambdaQueryWrapper<UserTag>()
                .eq(UserTag::getUserId, userId));

        // 批量插入新标签
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && !tag.trim().isEmpty()) {
                    UserTag ut = new UserTag();
                    ut.setUserId(userId);
                    ut.setTagName(tag.trim());
                    ut.setCreatedBy(operator);
                    ut.setCreatedTime(LocalDateTime.now());
                    userTagMapper.insert(ut);
                }
            }
        }
        // 用户标签变更后，清除用户级覆盖缓存（合并结果会变）
        userCache.remove(userId);
        logger.info("Set user tags for userId={}, tags={}, by={}", userId, tags, operator);
    }

    @Override
    @Transactional
    public void deleteTag(String tagName, String operator) {
        // 删除该标签的所有用户关联
        userTagMapper.delete(new LambdaQueryWrapper<UserTag>()
                .eq(UserTag::getTagName, tagName));
        // 删除该标签的所有覆盖规则
        featureOverrideMapper.delete(new LambdaQueryWrapper<FeatureOverride>()
                .eq(FeatureOverride::getScope, "tag")
                .eq(FeatureOverride::getScopeValue, tagName));
        // 清除标签缓存
        tagCache.remove(tagName);
        logger.info("Deleted tag={} by={}", tagName, operator);
    }

    // ---------- 覆盖规则管理 ----------

    @Override
    public List<FeatureOverride> listOverrides(String scope, String scopeValue) {
        LambdaQueryWrapper<FeatureOverride> wrapper = new LambdaQueryWrapper<>();
        if (scope != null) {
            wrapper.eq(FeatureOverride::getScope, scope);
        }
        if (scopeValue != null) {
            wrapper.eq(FeatureOverride::getScopeValue, scopeValue);
        }
        return featureOverrideMapper.selectList(wrapper);
    }

    @Override
    public void upsertOverride(FeatureOverride override, String operator) {
        override.setUpdatedBy(operator);
        override.setUpdatedTime(LocalDateTime.now());

        // 使用字符串列名的 UpdateWrapper 避免 LambdaUpdateWrapper 在纯单元测试环境
        // （无 Spring 上下文）下触发 MyBatis Plus lambda 元数据缓存缺失异常
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FeatureOverride> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("scope", override.getScope())
               .eq("scope_value", override.getScopeValue())
               .eq("feature_key", override.getFeatureKey())
               .set("enabled", override.getEnabled())
               .set("description", override.getDescription())
               .set("updated_by", operator)
               .set("updated_time", override.getUpdatedTime());

        int updated = featureOverrideMapper.update(null, wrapper);
        if (updated == 0) {
            featureOverrideMapper.insert(override);
        }

        evictCache(override.getScope(), override.getScopeValue());
        logger.info("Upsert override scope={} scopeValue={} featureKey={} enabled={} by={}",
                override.getScope(), override.getScopeValue(), override.getFeatureKey(),
                override.getEnabled(), operator);
    }

    @Override
    public void deleteOverride(String scope, String scopeValue, String featureKey, String operator) {
        featureOverrideMapper.delete(new LambdaQueryWrapper<FeatureOverride>()
                .eq(FeatureOverride::getScope, scope)
                .eq(FeatureOverride::getScopeValue, scopeValue)
                .eq(FeatureOverride::getFeatureKey, featureKey));
        evictCache(scope, scopeValue);
        logger.info("Deleted override scope={} scopeValue={} featureKey={} by={}", scope, scopeValue, featureKey, operator);
    }

    private void evictCache(String scope, String scopeValue) {
        if ("tag".equals(scope)) {
            tagCache.remove(scopeValue);
            logger.info("Evicted tag cache for tag: {}", scopeValue);
        } else if ("user".equals(scope)) {
            try {
                userCache.remove(Long.valueOf(scopeValue));
                logger.info("Evicted user cache for userId: {}", scopeValue);
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * 清除所有功能开关相关缓存
     * 在系统配置批量更新时调用
     */
    public void evictAllCache() {
        tagCache.clear();
        userCache.clear();
        logger.info("Evicted all feature toggle caches");
    }

    /**
     * 清除指定用户的所有相关缓存（包括标签缓存）
     * @param userId 用户ID
     */
    public void evictUserRelatedCache(Long userId) {
        // 清除用户级缓存
        userCache.remove(userId);
        
        // 获取用户的所有标签并清除对应的标签缓存
        List<String> userTags = userTagMapper.selectTagsByUserId(userId);
        for (String tag : userTags) {
            tagCache.remove(tag);
        }
        
        logger.info("Evicted cache for userId: {} and related tags: {}", userId, userTags);
    }

    private static class CacheEntry {
        final Map<String, Boolean> data;
        final long createdAt;

        CacheEntry(Map<String, Boolean> data) {
            this.data = Collections.unmodifiableMap(data);
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }
}
