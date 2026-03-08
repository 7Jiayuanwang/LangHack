package com.english.service;

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
}
