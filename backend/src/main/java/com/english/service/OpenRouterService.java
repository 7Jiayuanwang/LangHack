package com.english.service;

/**
 * Service for interacting with OpenRouter DeepSeek API
 */
public interface OpenRouterService {

    /**
     * Generate CET-4 simplified version of the article
     * 
     * @param originalContent Original article content
     * @return CET-4 version content
     */
    String generateCET4Version(String originalContent);

    /**
     * Generate CET-6 intermediate version of the article
     * 
     * @param originalContent Original article content
     * @return CET-6 version content
     */
    String generateCET6Version(String originalContent);

    /**
     * Generate academic version of the article
     * 
     * @param originalContent Original article content
     * @return Academic version content
     */
    String generateAcademicVersion(String originalContent);

    /**
     * Generate Chinese-English parallel version of the article
     * 
     * @param originalContent Original article content
     * @return Chinese-English parallel version content
     */
    String generateChineseParallelVersion(String originalContent);

    // ========== Markdown支持的方法 ==========

    /**
     * Generate CET-4 simplified version of the Markdown article (preserving images/videos)
     * 
     * @param originalMarkdownContent Original Markdown article content
     * @return CET-4 version Markdown content
     */
    String generateCET4VersionMarkdown(String originalMarkdownContent);

    /**
     * Generate CET-6 intermediate version of the Markdown article (preserving images/videos)
     * 
     * @param originalMarkdownContent Original Markdown article content
     * @return CET-6 version Markdown content
     */
    String generateCET6VersionMarkdown(String originalMarkdownContent);

    /**
     * Generate academic version of the Markdown article (preserving images/videos)
     * 
     * @param originalMarkdownContent Original Markdown article content
     * @return Academic version Markdown content
     */
    String generateAcademicVersionMarkdown(String originalMarkdownContent);

    /**
     * Generate Chinese-English parallel version of the Markdown article (preserving images/videos)
     * 
     * @param originalMarkdownContent Original Markdown article content
     * @return Chinese-English parallel version Markdown content
     */
    String generateChineseParallelVersionMarkdown(String originalMarkdownContent);
}
