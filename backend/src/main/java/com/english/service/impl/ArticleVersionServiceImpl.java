package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.english.entity.Text;
import com.english.mapper.TextMapper;
import com.english.service.ArticleVersionService;
import com.english.service.OpenRouterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Article version service implementation
 */
@Slf4j
@Service
public class ArticleVersionServiceImpl implements ArticleVersionService {

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private OpenRouterService openRouterService;

    // Self-injection to enable @Async to work for internal method calls
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
            // Use ConcurrentHashMap to safely collect results from parallel tasks
            Map<String, String> results = new ConcurrentHashMap<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Submit CET-4 generation task
            if (needsCET4) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    log.info("Generating CET-4 version for article {}", textId);
                    try {
                        return openRouterService.generateCET4Version(originalContent);
                    } catch (Exception e) {
                        log.error("Failed to generate CET-4 version for article {}", textId, e);
                        return null;
                    }
                }, versionGeneratorExecutor).orTimeout(120, TimeUnit.SECONDS).handle((content, ex) -> {
                    if (ex != null) {
                        log.error("CET-4 generation timed out or failed for article {}", textId, ex);
                        return null;
                    }
                    if (content != null) {
                        results.put("cet4_content", content);
                        log.info("CET-4 version generated successfully for article {}", textId);
                    }
                    return null;
                }));
            }

            // Submit CET-6 generation task
            if (needsCET6) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    log.info("Generating CET-6 version for article {}", textId);
                    try {
                        return openRouterService.generateCET6Version(originalContent);
                    } catch (Exception e) {
                        log.error("Failed to generate CET-6 version for article {}", textId, e);
                        return null;
                    }
                }, versionGeneratorExecutor).orTimeout(120, TimeUnit.SECONDS).handle((content, ex) -> {
                    if (ex != null) {
                        log.error("CET-6 generation timed out or failed for article {}", textId, ex);
                        return null;
                    }
                    if (content != null) {
                        results.put("cet6_content", content);
                        log.info("CET-6 version generated successfully for article {}", textId);
                    }
                    return null;
                }));
            }

            // Submit Academic generation task
            if (needsAcademic) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    log.info("Generating Academic version for article {}", textId);
                    try {
                        return openRouterService.generateAcademicVersion(originalContent);
                    } catch (Exception e) {
                        log.error("Failed to generate Academic version for article {}", textId, e);
                        return null;
                    }
                }, versionGeneratorExecutor).orTimeout(120, TimeUnit.SECONDS).handle((content, ex) -> {
                    if (ex != null) {
                        log.error("Academic generation timed out or failed for article {}", textId, ex);
                        return null;
                    }
                    if (content != null) {
                        results.put("academic_content", content);
                        log.info("Academic version generated successfully for article {}", textId);
                    }
                    return null;
                }));
            }

            // Submit Chinese Parallel generation task
            if (needsChineseParallel) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    log.info("Generating Chinese Parallel version for article {}", textId);
                    try {
                        return openRouterService.generateChineseParallelVersion(originalContent);
                    } catch (Exception e) {
                        log.error("Failed to generate Chinese Parallel version for article {}", textId, e);
                        return null;
                    }
                }, versionGeneratorExecutor).orTimeout(120, TimeUnit.SECONDS).handle((content, ex) -> {
                    if (ex != null) {
                        log.error("Chinese Parallel generation timed out or failed for article {}", textId, ex);
                        return null;
                    }
                    if (content != null) {
                        results.put("chinese_parallel_content", content);
                        log.info("Chinese Parallel version generated successfully for article {}", textId);
                    }
                    return null;
                }));
            }

            // Wait for all parallel tasks to complete (max 300 seconds)
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(300, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.error("Version generation timed out for article {}", textId);
                futures.forEach(f -> f.cancel(true));
            } catch (Exception e) {
                log.error("Error waiting for version generation for article {}", textId, e);
                futures.forEach(f -> f.cancel(true));
            }

            // Build UpdateWrapper in main thread from collected results (thread-safe)
            if (!results.isEmpty()) {
                UpdateWrapper<Text> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", textId);
                results.forEach(updateWrapper::set);

                int updated = textMapper.update(null, updateWrapper);
                if (updated > 0) {
                    log.info("Successfully saved {} generated versions for article {}", results.size(), textId);
                } else {
                    log.warn("No versions were saved for article {}", textId);
                }
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

        // Check which Markdown versions are missing
        boolean needsCET4 = isNullOrEmpty(text.getCet4MarkdownContent());
        boolean needsCET6 = isNullOrEmpty(text.getCet6MarkdownContent());
        boolean needsAcademic = isNullOrEmpty(text.getAcademicMarkdownContent());
        boolean needsChineseParallel = isNullOrEmpty(text.getChineseParallelMarkdownContent());

        // If any version is missing, trigger async generation via self-proxy
        if (needsCET4 || needsCET6 || needsAcademic || needsChineseParallel) {
            String markdownContent = text.getMarkdownContent();
            if (markdownContent == null || markdownContent.isBlank()) {
                log.warn("Text {} has null/empty markdown content, skipping markdown version generation", textId);
                return;
            }
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
            // Use ConcurrentHashMap to safely collect results from parallel tasks
            Map<String, String> results = new ConcurrentHashMap<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Submit CET-4 Markdown generation task
            if (needsCET4) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    log.info("Generating CET-4 Markdown version for article {}", textId);
                    try {
                        return openRouterService.generateCET4VersionMarkdown(originalMarkdownContent);
                    } catch (Exception e) {
                        log.error("Failed to generate CET-4 Markdown version for article {}", textId, e);
                        return null;
                    }
                }, versionGeneratorExecutor).orTimeout(120, TimeUnit.SECONDS).handle((content, ex) -> {
                    if (ex != null) {
                        log.error("CET-4 Markdown generation timed out or failed for article {}", textId, ex);
                        return null;
                    }
                    if (content != null) {
                        results.put("cet4_markdown_content", content);
                        log.info("CET-4 Markdown version generated successfully for article {}", textId);
                    }
                    return null;
                }));
            }

            // Submit CET-6 Markdown generation task
            if (needsCET6) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    log.info("Generating CET-6 Markdown version for article {}", textId);
                    try {
                        return openRouterService.generateCET6VersionMarkdown(originalMarkdownContent);
                    } catch (Exception e) {
                        log.error("Failed to generate CET-6 Markdown version for article {}", textId, e);
                        return null;
                    }
                }, versionGeneratorExecutor).orTimeout(120, TimeUnit.SECONDS).handle((content, ex) -> {
                    if (ex != null) {
                        log.error("CET-6 Markdown generation timed out or failed for article {}", textId, ex);
                        return null;
                    }
                    if (content != null) {
                        results.put("cet6_markdown_content", content);
                        log.info("CET-6 Markdown version generated successfully for article {}", textId);
                    }
                    return null;
                }));
            }

            // Submit Academic Markdown generation task
            if (needsAcademic) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    log.info("Generating Academic Markdown version for article {}", textId);
                    try {
                        return openRouterService.generateAcademicVersionMarkdown(originalMarkdownContent);
                    } catch (Exception e) {
                        log.error("Failed to generate Academic Markdown version for article {}", textId, e);
                        return null;
                    }
                }, versionGeneratorExecutor).orTimeout(120, TimeUnit.SECONDS).handle((content, ex) -> {
                    if (ex != null) {
                        log.error("Academic Markdown generation timed out or failed for article {}", textId, ex);
                        return null;
                    }
                    if (content != null) {
                        results.put("academic_markdown_content", content);
                        log.info("Academic Markdown version generated successfully for article {}", textId);
                    }
                    return null;
                }));
            }

            // Submit Chinese Parallel Markdown generation task
            if (needsChineseParallel) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    log.info("Generating Chinese Parallel Markdown version for article {}", textId);
                    try {
                        return openRouterService.generateChineseParallelVersionMarkdown(originalMarkdownContent);
                    } catch (Exception e) {
                        log.error("Failed to generate Chinese Parallel Markdown version for article {}", textId, e);
                        return null;
                    }
                }, versionGeneratorExecutor).orTimeout(120, TimeUnit.SECONDS).handle((content, ex) -> {
                    if (ex != null) {
                        log.error("Chinese Parallel Markdown generation timed out or failed for article {}", textId, ex);
                        return null;
                    }
                    if (content != null) {
                        results.put("chinese_parallel_markdown_content", content);
                        log.info("Chinese Parallel Markdown version generated successfully for article {}", textId);
                    }
                    return null;
                }));
            }

            // Wait for all parallel tasks to complete (max 300 seconds)
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(300, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.error("Markdown version generation timed out for article {}", textId);
                futures.forEach(f -> f.cancel(true));
            } catch (Exception e) {
                log.error("Error waiting for Markdown version generation for article {}", textId, e);
                futures.forEach(f -> f.cancel(true));
            }

            // Build UpdateWrapper in main thread from collected results (thread-safe)
            if (!results.isEmpty()) {
                UpdateWrapper<Text> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", textId);
                results.forEach(updateWrapper::set);

                int updated = textMapper.update(null, updateWrapper);
                if (updated > 0) {
                    log.info("Successfully saved {} generated Markdown versions for article {}", results.size(), textId);
                } else {
                    log.warn("No Markdown versions were saved for article {}", textId);
                }
            } else {
                log.warn("No Markdown versions were generated for article {}", textId);
            }

        } catch (Exception e) {
            log.error("Error during async Markdown version generation for article {}", textId, e);
        }
    }
}
