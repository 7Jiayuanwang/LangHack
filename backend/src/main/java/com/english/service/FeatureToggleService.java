package com.english.service;

import com.english.entity.FeatureOverride;

import java.util.List;
import java.util.Map;

public interface FeatureToggleService {

    /**
     * 三层合并：全局 → 标签级 → 用户级，返回 Boolean map
     */
    Map<String, Boolean> getMergedTogglesForUser(Long userId);

    // ---------- 标签管理 ----------

    List<String> listAllTagNames();

    List<Long> getUserIdsByTag(String tagName);

    List<String> getUserTags(Long userId);

    /** 全量覆盖用户标签（先删后批量插） */
    void setUserTags(Long userId, List<String> tags, String operator);

    void deleteTag(String tagName, String operator);

    // ---------- 覆盖规则管理 ----------

    List<FeatureOverride> listOverrides(String scope, String scopeValue);

    void upsertOverride(FeatureOverride override, String operator);

    void deleteOverride(String scope, String scopeValue, String featureKey, String operator);
}
