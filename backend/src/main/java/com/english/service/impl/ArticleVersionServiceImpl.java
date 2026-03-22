package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.english.entity.Text;
import com.english.mapper.TextMapper;
import com.english.service.ArticleVersionService;
import com.english.service.HiAgentService;
import com.english.service.OpenRouterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Article version service implementation
 */
@Slf4j
@Service
public class ArticleVersionServiceImpl implements ArticleVersionService {

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private HiAgentService hiAgentService;

    @Autowired
    private OpenRouterService openRouterService;

    // Self-injection to enable @Async and @Transactional to work for internal method calls
    @Autowired
    @Lazy
    private ArticleVersionService self;

    @Autowired
    @Qualifier("versionGeneratorExecutor")
    private Executor versionGeneratorExecutor;

    @Override
    public void checkAndGenerateVersions(Long textId) {
        Text text = textMapper.selectById(textId);
        if (text == null) {
            log.warn("Text not found for ID: {}", textId);
            return;
        }

        // Check which versions are missing
        boolean needsCET4 = isNullOrEmpty(text.getCet4Content());
        boolean needsCET6 = isNullOrEmpty(text.getCet6Content());
        boolean needsAcademic = isNullOrEmpty(text.getAcademicContent());
        boolean needsChineseParallel = isNullOrEmpty(text.getChineseParallelContent());

        // If any version is missing, trigger async generation via self-proxy
        if (needsCET4 || needsCET6 || needsAcademic || needsChineseParallel) {
            String content = text.getContent();
            if (content == null || content.isBlank()) {
                log.warn("Text {} has null/empty content, skipping version generation", textId);
                return;
            }
            log.info("Article {} is missing versions. Triggering async generation...", textId);
            // Use self-injection to ensure @Async annotation is properly processed by
            // Spring proxy
            self.generateVersionsAsync(textId, content, needsCET4, needsCET6, needsAcademic,
                    needsChineseParallel);
        } else {
            log.debug("Article {} already has all versions", textId);
        }
    }

    /**
     * Asynchronously generate missing article versions in parallel
     */
    @Override
    @Async("versionGeneratorExecutor")
    public void generateVersionsAsync(Long textId, String originalContent,
            boolean needsCET4, boolean needsCET6,
            boolean needsAcademic, boolean needsChineseParallel) {
        log.info("Starting async parallel version generation for article {}", textId);

        try {
            Map<String, String> results = new ConcurrentHashMap<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            if (needsCET4) {
                futures.add(createVersionTask("CET-4", textId, originalContent,
                        hiAgentService::generateCET4Version,
                        openRouterService::generateCET4Version,
                        "cet4_content", results));
            }
            if (needsCET6) {
                futures.add(createVersionTask("CET-6", textId, originalContent,
                        hiAgentService::generateCET6Version,
                        openRouterService::generateCET6Version,
                        "cet6_content", results));
            }
            if (needsAcademic) {
                futures.add(createVersionTask("Academic", textId, originalContent,
                        hiAgentService::generateAcademicVersion,
                        openRouterService::generateAcademicVersion,
                        "academic_content", results));
            }
            if (needsChineseParallel) {
                futures.add(createVersionTask("Chinese Parallel", textId, originalContent,
                        hiAgentService::generateChineseParallelVersion,
                        openRouterService::generateChineseParallelVersion,
                        "chinese_parallel_content", results));
            }

            waitForFutures(futures, textId, "Plain text");

            // Save results via proxy for @Transactional to work
            if (!results.isEmpty()) {
                self.saveVersionResults(textId, results);
            } else {
                log.warn("No versions were generated for article {}", textId);
            }

        } catch (Exception e) {
            log.error("Error during async version generation for article {}", textId, e);
        }
    }

    /**
     * Check if string is null or empty/whitespace
     */
    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    // ========== Markdown版本生成支持 ==========

    @Override
    public void checkAndGenerateMarkdownVersions(Long textId) {
        Text text = textMapper.selectById(textId);
        if (text == null) {
            log.warn("Text not found for ID: {}", textId);
            return;
        }

        // 只有当内容类型是MARKDOWN时才生成Markdown版本
        if (!"MARKDOWN".equals(text.getContentType())) {
            log.debug("Text {} is not Markdown format, skipping Markdown version generation", textId);
            return;
        }

        // Defensive null check for markdownContent
        String markdownContent = text.getMarkdownContent();
        if (markdownContent == null || markdownContent.isBlank()) {
            log.warn("Text {} has null/empty markdown content, skipping markdown version generation", textId);
            return;
        }

        // Check which Markdown versions are missing
        boolean needsCET4 = isNullOrEmpty(text.getCet4MarkdownContent());
        boolean needsCET6 = isNullOrEmpty(text.getCet6MarkdownContent());
        boolean needsAcademic = isNullOrEmpty(text.getAcademicMarkdownContent());
        boolean needsChineseParallel = isNullOrEmpty(text.getChineseParallelMarkdownContent());

        // If any version is missing, trigger async generation via self-proxy
        if (needsCET4 || needsCET6 || needsAcademic || needsChineseParallel) {
            log.info("Markdown article {} is missing versions. Triggering async generation...", textId);
            // Use self-injection to ensure @Async annotation is properly processed by Spring proxy
            self.generateMarkdownVersionsAsync(textId, markdownContent, needsCET4, needsCET6, needsAcademic, needsChineseParallel);
        } else {
            log.debug("Markdown article {} already has all versions", textId);
        }
    }

    /**
     * Asynchronously generate missing Markdown article versions in parallel
     */
    @Override
    @Async("versionGeneratorExecutor")
    public void generateMarkdownVersionsAsync(Long textId, String originalMarkdownContent,
            boolean needsCET4, boolean needsCET6,
            boolean needsAcademic, boolean needsChineseParallel) {
        log.info("Starting async parallel Markdown version generation for article {}", textId);

        try {
            Map<String, String> results = new ConcurrentHashMap<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            if (needsCET4) {
                futures.add(createVersionTask("CET-4 Markdown", textId, originalMarkdownContent,
                        hiAgentService::generateCET4VersionMarkdown,
                        openRouterService::generateCET4VersionMarkdown,
                        "cet4_markdown_content", results));
            }
            if (needsCET6) {
                futures.add(createVersionTask("CET-6 Markdown", textId, originalMarkdownContent,
                        hiAgentService::generateCET6VersionMarkdown,
                        openRouterService::generateCET6VersionMarkdown,
                        "cet6_markdown_content", results));
            }
            if (needsAcademic) {
                futures.add(createVersionTask("Academic Markdown", textId, originalMarkdownContent,
                        hiAgentService::generateAcademicVersionMarkdown,
                        openRouterService::generateAcademicVersionMarkdown,
                        "academic_markdown_content", results));
            }
            if (needsChineseParallel) {
                futures.add(createVersionTask("Chinese Parallel Markdown", textId, originalMarkdownContent,
                        hiAgentService::generateChineseParallelVersionMarkdown,
                        openRouterService::generateChineseParallelVersionMarkdown,
                        "chinese_parallel_markdown_content", results));
            }

            waitForFutures(futures, textId, "Markdown");

            // Save results via proxy for @Transactional to work
            if (!results.isEmpty()) {
                self.saveMarkdownVersionResults(textId, results);
            } else {
                log.warn("No Markdown versions were generated for article {}", textId);
            }

        } catch (Exception e) {
            log.error("Error during async Markdown version generation for article {}", textId, e);
        }
    }

    /**
     * Save version generation results with proper transaction management.
     * CRITICAL: Called via self-proxy to ensure @Transactional works through Spring AOP.
     */
    @Override
    @Transactional
    public void saveVersionResults(Long textId, Map<String, String> results) {
        UpdateWrapper<Text> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", textId);
        updateWrapper.eq("deleted", 0); // 只更新未删除的课文
        results.forEach(updateWrapper::set);

        int updated = textMapper.update(null, updateWrapper);
        if (updated > 0) {
            log.info("Successfully saved {} generated versions for article {}", results.size(), textId);
        } else {
            log.warn("No versions were saved for article {} (text not found or deleted)", textId);
        }
    }

    /**
     * Save Markdown version generation results with proper transaction management.
     * CRITICAL: Called via self-proxy to ensure @Transactional works through Spring AOP.
     */
    @Override
    @Transactional
    public void saveMarkdownVersionResults(Long textId, Map<String, String> results) {
        UpdateWrapper<Text> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", textId);
        updateWrapper.eq("deleted", 0); // 只更新未删除的课文
        results.forEach(updateWrapper::set);

        int updated = textMapper.update(null, updateWrapper);
        if (updated > 0) {
            log.info("Successfully saved {} generated Markdown versions for article {}", results.size(), textId);
        } else {
            log.warn("No Markdown versions were saved for article {} (text not found or deleted)", textId);
        }
    }

    // ========== Private helpers ==========

    /**
     * Create a version generation task with HiAgent primary + OpenRouter fallback.
     * Eliminates duplicated CompletableFuture boilerplate across 8 version types.
     */
    private CompletableFuture<Void> createVersionTask(
            String versionName, Long textId, String content,
            Function<String, String> primaryGenerator,
            Function<String, String> fallbackGenerator,
            String resultKey, Map<String, String> results) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Generating {} version for article {}", versionName, textId);
            try {
                try {
                    return primaryGenerator.apply(content);
                } catch (Exception ex) {
                    log.warn("HiAgent failed to generate {} version for article {}, falling back to OpenRouter: {}",
                            versionName, textId, ex.getMessage());
                    return fallbackGenerator.apply(content);
                }
            } catch (Exception e) {
                log.error("Failed to generate {} version for article {}", versionName, textId, e);
                return null;
            }
        }, versionGeneratorExecutor).orTimeout(120, TimeUnit.SECONDS).handle((result, ex) -> {
            if (ex != null) {
                log.error("{} generation timed out or failed for article {}", versionName, textId, ex);
                return null;
            }
            if (result != null) {
                results.put(resultKey, result);
                log.info("{} version generated successfully for article {}", versionName, textId);
            }
            return null;
        });
    }

    /**
     * Wait for all futures to complete with a global timeout, cancelling on failure.
     */
    private void waitForFutures(List<CompletableFuture<Void>> futures, Long textId, String type) {
        if (futures.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(300, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("{} version generation timed out for article {}", type, textId);
            futures.forEach(f -> f.cancel(true));
        } catch (InterruptedException e) {
            log.error("{} version generation interrupted for article {}", type, textId, e);
            futures.forEach(f -> f.cancel(true));
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error waiting for {} version generation for article {}", type, textId, e);
            futures.forEach(f -> f.cancel(true));
        }
    }
}
