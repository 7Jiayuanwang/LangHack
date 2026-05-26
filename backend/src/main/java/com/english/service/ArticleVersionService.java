package com.english.service;

import java.util.Map;

/**
 * Service for managing article versions
 */
public interface ArticleVersionService {

    /**
     * Check if article versions exist, and generate missing ones asynchronously
     * 
     * @param textId Article ID
     */
    void checkAndGenerateVersions(Long textId);

    /**
     * Asynchronously generate missing article versions
     * Called internally via proxy to ensure @Async works
     */
    void generateVersionsAsync(Long textId, String originalContent,
            boolean needsCET4, boolean needsCET6,
            boolean needsAcademic, boolean needsChineseParallel);

    /**
     * Check if Markdown article versions exist, and generate missing ones asynchronously
     * 
     * @param textId Article ID
     */
    void checkAndGenerateMarkdownVersions(Long textId);

    /**
     * Asynchronously generate missing Markdown article versions
     * Called internally via proxy to ensure @Async works
     */
    void generateMarkdownVersionsAsync(Long textId, String originalMarkdownContent,
            boolean needsCET4, boolean needsCET6,
            boolean needsAcademic, boolean needsChineseParallel);

    /**
     * Save plain text version results (must be called via proxy for @Transactional)
     */
    void saveVersionResults(Long textId, Map<String, String> results);

    /**
     * Save Markdown version results (must be called via proxy for @Transactional)
     */
    void saveMarkdownVersionResults(Long textId, Map<String, String> results);

    /**
     * 强制重新生成指定文章的改写版本，覆盖已有内容。
     * @param textId  文章 ID
     * @param version 版本标识，取值 CET4 / CET6 / ACADEMIC / PARALLEL / ALL（大小写不敏感）
     */
    void regenerateVersions(Long textId, String version);
}
