package com.english.service;

import com.english.entity.SystemConfig;
import java.util.List;
import java.util.Map;

public interface SystemConfigService {

    /**
     * Get all config values for a group as key-value map (runtime use, cached)
     */
    Map<String, String> getByGroup(String group);

    /**
     * Get a single config value (runtime use, cached)
     */
    String get(String group, String key);

    /**
     * Get a single config value with default fallback
     */
    String get(String group, String key, String defaultValue);

    /**
     * Set a single config value (updates DB + clears cache)
     */
    void set(String group, String key, String value, String updatedBy);

    /**
     * List all configs for a group with metadata (for admin UI)
     */
    List<SystemConfig> listByGroup(String group);

    /**
     * Batch update configs (updates DB + clears cache)
     */
    void batchUpdate(List<SystemConfig> configs, String updatedBy);

    /**
     * Get config by key (cross-group search)
     */
    SystemConfig getByKey(String key);

    /**
     * Upsert config (insert or update)
     */
    void upsert(String group, String key, String value, String description);

    /**
     * Delete config by key
     */
    void deleteByKey(String key);
}
