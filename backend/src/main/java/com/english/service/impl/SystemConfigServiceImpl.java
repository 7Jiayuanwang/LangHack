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
    private static final Map<String, String> FEATURE_TOGGLE_DESCRIPTIONS = createFeatureToggleDescriptions();

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

            int updated = mapper.update(null,
                    new LambdaUpdateWrapper<SystemConfig>()
                            .eq(SystemConfig::getConfigGroup, config.getConfigGroup())
                            .eq(SystemConfig::getConfigKey, config.getConfigKey())
                            .set(SystemConfig::getConfigValue, config.getConfigValue())
                            .set(SystemConfig::getUpdatedBy, updatedBy)
                            .set(SystemConfig::getUpdatedTime, LocalDateTime.now())
            );

            if (updated == 0) {
                SystemConfig inserted = new SystemConfig();
                inserted.setConfigGroup(config.getConfigGroup());
                inserted.setConfigKey(config.getConfigKey());
                inserted.setConfigValue(config.getConfigValue());
                inserted.setDescription(config.getDescription());
                inserted.setIsSecret(config.getIsSecret() != null ? config.getIsSecret() : 0);
                inserted.setUpdatedBy(updatedBy);
                inserted.setUpdatedTime(LocalDateTime.now());
                mapper.insert(inserted);
            }

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

    @Override
    public SystemConfig getByKey(String key) {
        return mapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key)
                .last("LIMIT 1"));
    }

    @Override
    public void upsert(String group, String key, String value, String description) {
        int updated = mapper.update(null,
                new LambdaUpdateWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigKey, key)
                        .set(SystemConfig::getConfigValue, value)
                        .set(SystemConfig::getUpdatedTime, LocalDateTime.now()));

        if (updated == 0) {
            SystemConfig config = new SystemConfig();
            config.setConfigGroup(group);
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(description);
            config.setIsSecret(0);
            config.setUpdatedTime(LocalDateTime.now());
            mapper.insert(config);
        }

        cache.remove(group);
    }

    @Override
    public void deleteByKey(String key) {
        SystemConfig config = getByKey(key);
        if (config != null) {
            mapper.delete(new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
            cache.remove(config.getConfigGroup());
        }
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
            String canonical = resolveCanonicalDescription(config);
            if (canonical != null) {
                config.setDescription(canonical);
                continue;
            }
            config.setDescription(repairMojibake(config.getDescription()));
        }
    }

    private String resolveCanonicalDescription(SystemConfig config) {
        if (config == null) {
            return null;
        }
        if (!"feature_toggle".equals(config.getConfigGroup())) {
            return null;
        }
        return FEATURE_TOGGLE_DESCRIPTIONS.get(config.getConfigKey());
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

    private static Map<String, String> createFeatureToggleDescriptions() {
        Map<String, String> labels = new HashMap<>();
        labels.put("page_dashboard", "学生端-发现页");
        labels.put("page_courses", "学生端-课程页（开发中）");
        labels.put("page_classes", "学生端-班级/社区页");
        labels.put("page_assignments", "学生端-任务页");
        labels.put("page_articles", "学生端-文章页");
        labels.put("page_vocabulary", "学生端-生词本页");
        labels.put("page_my_study", "学生端-我的学习页");
        labels.put("page_ai_video", "学生端-AI视频字幕页（未完善）");
        labels.put("page_ai_qa", "学生端-AI问答页");
        labels.put("page_user_texts", "学生端-我的上传页");
        labels.put("page_scores", "学生端-等级考试成绩页");
        labels.put("page_course_selections", "学生端-我的选课页");
        labels.put("page_text_learning", "学生端-课文学习子页");
        labels.put("page_schedule", "学生端-我的课表页");
        labels.put("page_videos", "学生端-视频学习页");
        labels.put("page_video_player", "学生端-视频播放器页");
        labels.put("page_interactive_text", "学生端-交互式课文页");
        labels.put("page_word_recite", "学生端-背单词页");
        labels.put("page_teacher_dashboard", "教师端-仪表盘");
        labels.put("page_teacher_courses", "教师端-课程管理");
        labels.put("page_teacher_my_courses", "教师端-我的课程");
        labels.put("page_teacher_classes", "教师端-班级管理");
        labels.put("page_teacher_assignments", "教师端-作业管理");
        labels.put("page_admin_videos", "管理员-视频管理");
        labels.put("page_admin_backups", "管理员-备份管理");
        labels.put("page_admin_monitoring", "管理员-系统监控");
        labels.put("page_ops_monitoring", "运维端-系统监控");
        labels.put("page_ops_ai_providers", "运维端-AI提供商管理");
        labels.put("page_ops_feature_toggles", "运维端-功能开关管理");
        labels.put("page_ops_school_data_config", "运维端-学校数据配置");
        labels.put("page_ops_school_data_browser", "运维端-学校数据浏览器");
        labels.put("page_ops_update", "运维端-系统更新");
        labels.put("admin.dashboard", "管理员-仪表盘");
        labels.put("admin.courses", "管理员-课程管理");
        labels.put("admin.classes", "管理员-班级管理");
        labels.put("admin.users", "管理员-用户管理");
        labels.put("admin.units", "管理员-单元管理");
        labels.put("admin.words", "管理员-单词管理");
        labels.put("admin.texts", "管理员-课文管理");
        labels.put("admin.assignments", "管理员-作业管理");
        labels.put("admin.sso_approval", "管理员-SSO 审批");
        labels.put("ops.school_data_browser", "运维端学校数据浏览功能（行政班、课程、教学班等）");
        labels.put("ops.school_data_config", "运维端学校数据源配置功能（API/ETL切换、连接测试）");
        labels.put("ops.school_data_chain_fetch", "运维端按学院链式拉取功能开关");
        labels.put("student.lazy_data_sync", "学生端按需 Lazy 同步四六级/课表");
        labels.put("student.lazy_course_selections", "学生端 Lazy 选课记录同步（陈旧度阈值 1 天）");
        labels.put("teacher.school_data_enabled", "教师端学校数据功能（课表/选课/成绩）");
        labels.put("sso.auto_grant_student", "SSO 学号匹配 school_student 自动授权 STUDENT");
        labels.put("sso.auto_grant_teacher", "SSO 工号匹配 school_teacher 自动授权 TEACHER");
        return Collections.unmodifiableMap(labels);
    }
}
