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
import java.util.Optional;
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

    @Autowired(required = false)
    private AiProviderRuntimeService aiProviderRuntimeService;

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

        log.info("[版本生成] 文章{} 版本状态: contentType={}, needsCET4={}, needsCET6={}, needsAcademic={}, needsChineseParallel={}",
                textId, text.getContentType(), needsCET4, needsCET6, needsAcademic, needsChineseParallel);

        // If any version is missing, trigger async generation via self-proxy
        if (needsCET4 || needsCET6 || needsAcademic || needsChineseParallel) {
            String content = text.getContent();
            if (content == null || content.isBlank()) {
                log.warn("[版本生成] 文章{} 内容为空，跳过版本生成", textId);
                return;
            }
            log.info("[版本生成] 文章{} 缺少版本，触发异步生成，原文长度={}字符", textId, content.length());
            // Use self-injection to ensure @Async annotation is properly processed by
            // Spring proxy
            self.generateVersionsAsync(textId, content, needsCET4, needsCET6, needsAcademic,
                    needsChineseParallel);
        } else {
            log.info("[版本生成] 文章{} 已有所有版本，无需生成", textId);
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

            log.info("[版本生成] 文章{} 异步生成完成，成功生成 {} 个版本: {}", textId, results.size(), results.keySet());

            // Save results via proxy for @Transactional to work
            if (!results.isEmpty()) {
                self.saveVersionResults(textId, results);
            } else {
                log.warn("[版本生成] 文章{} 没有任何版本生成成功!", textId);
            }

        } catch (Exception e) {
            log.error("[版本生成] 文章{} 异步生成过程异常", textId, e);
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

        log.info("[版本生成] Markdown文章{} 版本状态: needsCET4={}, needsCET6={}, needsAcademic={}, needsChineseParallel={}",
                textId, needsCET4, needsCET6, needsAcademic, needsChineseParallel);

        // If any version is missing, trigger async generation via self-proxy
        if (needsCET4 || needsCET6 || needsAcademic || needsChineseParallel) {
            log.info("[版本生成] Markdown文章{} 缺少版本，触发异步生成，原文长度={}字符", textId, markdownContent.length());
            // Use self-injection to ensure @Async annotation is properly processed by Spring proxy
            self.generateMarkdownVersionsAsync(textId, markdownContent, needsCET4, needsCET6, needsAcademic, needsChineseParallel);
        } else {
            log.info("[版本生成] Markdown文章{} 已有所有版本，无需生成", textId);
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

            log.info("[版本生成] Markdown文章{} 异步生成完成，成功生成 {} 个版本: {}", textId, results.size(), results.keySet());

            // Save results via proxy for @Transactional to work
            if (!results.isEmpty()) {
                self.saveMarkdownVersionResults(textId, results);
            } else {
                log.warn("[版本生成] Markdown文章{} 没有任何版本生成成功!", textId);
            }

        } catch (Exception e) {
            log.error("[版本生成] Markdown文章{} 异步生成过程异常", textId, e);
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
            log.info("[版本生成] 开始生成 {} 版本，文章{}", versionName, textId);
            try {
                // 1. 尝试管理员配置的 AI 提供商
                Optional<String> configuredResult = generateWithConfiguredArticleProvider(versionName, content);
                if (configuredResult.isPresent()) {
                    log.info("[版本生成] {} 版本通过配置的AI提供商生成成功，文章{}，结果长度={}字符",
                            versionName, textId, configuredResult.get().length());
                    return configuredResult.get();
                }
                log.info("[版本生成] {} 无配置的AI提供商或生成为空，尝试 HiAgent，文章{}", versionName, textId);

                // 2. 尝试 HiAgent
                try {
                    String result = primaryGenerator.apply(content);
                    if (result != null && !result.isBlank()) {
                        log.info("[版本生成] {} 版本通过 HiAgent 生成成功，文章{}，结果长度={}字符",
                                versionName, textId, result.length());
                    } else {
                        log.warn("[版本生成] {} 版本 HiAgent 返回空结果，文章{}", versionName, textId);
                    }
                    return result;
                } catch (Exception ex) {
                    log.warn("[版本生成] {} 版本 HiAgent 调用失败，降级到 OpenRouter，文章{}，错误: {}",
                            versionName, textId, ex.getMessage());
                    // 3. 降级到 OpenRouter
                    try {
                        String fallbackResult = fallbackGenerator.apply(content);
                        if (fallbackResult != null && !fallbackResult.isBlank()) {
                            log.info("[版本生成] {} 版本通过 OpenRouter 生成成功，文章{}，结果长度={}字符",
                                    versionName, textId, fallbackResult.length());
                        } else {
                            log.warn("[版本生成] {} 版本 OpenRouter 也返回空结果，文章{}", versionName, textId);
                        }
                        return fallbackResult;
                    } catch (Exception fallbackEx) {
                        log.error("[版本生成] {} 版本 OpenRouter 也失败，文章{}，错误: {}",
                                versionName, textId, fallbackEx.getMessage());
                        return null;
                    }
                }
            } catch (Exception e) {
                log.error("[版本生成] {} 版本生成异常，文章{}", versionName, textId, e);
                return null;
            }
        }, versionGeneratorExecutor).orTimeout(120, TimeUnit.SECONDS).handle((result, ex) -> {
            if (ex != null) {
                String cause = ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : "unknown";
                log.error("[版本生成] {} 版本超时或失败，文章{}，异常类型={}，原因: {}",
                        versionName, textId, cause, ex.getMessage());
                return null;
            }
            if (result != null) {
                results.put(resultKey, result);
                log.info("[版本生成] {} 版本已加入结果集，文章{}，结果长度={}字符",
                        versionName, textId, result.length());
            } else {
                log.warn("[版本生成] {} 版本结果为null，文章{}", versionName, textId);
            }
            return null;
        });
    }

    private Optional<String> generateWithConfiguredArticleProvider(String versionName, String content) {
        if (aiProviderRuntimeService == null) {
            log.info("[版本生成] 未配置 AiProviderRuntimeService，跳过配置的AI提供商");
            return Optional.empty();
        }

        try {
            String method = methodNameForVersion(versionName);
            log.info("[版本生成] 尝试配置的AI提供商: service=article, method={}, version={}", method, versionName);
            Optional<String> result = aiProviderRuntimeService.completeIfConfigured(
                    "article", method, buildVersionPrompt(versionName, content));
            if (result.isEmpty()) {
                log.info("[版本生成] 配置的AI提供商未返回结果 (可能未配置)，version={}", versionName);
            }
            return result;
        } catch (Exception e) {
            log.warn("[版本生成] 配置的AI提供商调用失败，version={}，错误: {}", versionName, e.getMessage());
            return Optional.empty();
        }
    }

    private String buildVersionPrompt(String versionName, String content) {
        boolean markdown = versionName.contains("Markdown");

        if (versionName.startsWith("CET-4")) {
            return markdown
                    ? "Please rewrite the following Markdown article to match CET-4 difficulty level. "
                            + "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown.\n\n"
                            + content
                    : "Please rewrite the following English article to match CET-4 difficulty level. "
                            + "Only return the rewritten text.\n\n" + content;
        }
        if (versionName.startsWith("CET-6")) {
            return markdown
                    ? "Please rewrite the following Markdown article to match CET-6 difficulty level. "
                            + "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown.\n\n"
                            + content
                    : "Please rewrite the following English article to match CET-6 difficulty level. "
                            + "Only return the rewritten text.\n\n" + content;
        }
        if (versionName.startsWith("Academic")) {
            return markdown
                    ? "Please rewrite the following Markdown article in an academic style. "
                            + "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown.\n\n"
                            + content
                    : "Please rewrite the following English article in an academic style. "
                            + "Only return the rewritten text.\n\n" + content;
        }
        if (versionName.startsWith("Chinese Parallel")) {
            return markdown
                    ? "Please create a Chinese-English parallel text version of the following Markdown article. "
                            + "Format: English paragraph, then Chinese paragraph. PRESERVE ALL Markdown formatting. Only return the parallel Markdown.\n\n"
                            + content
                    : "Please create a Chinese-English parallel text version of the following English article. "
                            + "Format: English paragraph, then Chinese paragraph. Only return the parallel text.\n\n" + content;
        }

        return content;
    }

    private String methodNameForVersion(String versionName) {
        boolean markdown = versionName.contains("Markdown");
        if (versionName.startsWith("CET-4")) {
            return markdown ? "generateCET4VersionMarkdown" : "generateCET4Version";
        }
        if (versionName.startsWith("CET-6")) {
            return markdown ? "generateCET6VersionMarkdown" : "generateCET6Version";
        }
        if (versionName.startsWith("Academic")) {
            return markdown ? "generateAcademicVersionMarkdown" : "generateAcademicVersion";
        }
        if (versionName.startsWith("Chinese Parallel")) {
            return markdown ? "generateChineseParallelVersionMarkdown" : "generateChineseParallelVersion";
        }
        return "generateArticleVersion";
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
