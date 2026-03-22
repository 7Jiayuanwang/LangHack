package com.english.service;

/**
 * HiAgent AI service interface for article generation and rewriting
 */
public interface HiAgentService {

    /**
     * Generate CET-4 version of an article
     */
    String generateCET4Version(String originalContent);

    /**
     * Generate CET-6 version of an article
     */
    String generateCET6Version(String originalContent);

    /**
     * Generate Academic version of an article
     */
    String generateAcademicVersion(String originalContent);

    /**
     * Generate Chinese-English parallel version
     */
    String generateChineseParallelVersion(String originalContent);

    // Markdown support

    String generateCET4VersionMarkdown(String originalMarkdownContent);

    String generateCET6VersionMarkdown(String originalMarkdownContent);

    String generateAcademicVersionMarkdown(String originalMarkdownContent);

    String generateChineseParallelVersionMarkdown(String originalMarkdownContent);

    /**
     * AI Q&A: answer a user question about highlighted text in an article
     */
    String chatQA(String userQuestion, String selectedText, String articleContext);
}
