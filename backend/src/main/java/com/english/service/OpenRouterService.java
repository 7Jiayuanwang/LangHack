package com.english.service;

/**
 * OpenRouter AI service interface for article generation (Fallback Provider)
 */
public interface OpenRouterService {

    String generateCET4Version(String originalContent);

    String generateCET6Version(String originalContent);

    String generateAcademicVersion(String originalContent);

    String generateChineseParallelVersion(String originalContent);

    String generateCET4VersionMarkdown(String originalMarkdownContent);

    String generateCET6VersionMarkdown(String originalMarkdownContent);

    String generateAcademicVersionMarkdown(String originalMarkdownContent);

    String generateChineseParallelVersionMarkdown(String originalMarkdownContent);
}
