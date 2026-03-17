package com.english.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.english.entity.Text;
import com.english.mapper.TextMapper;
import com.english.service.impl.ArticleVersionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ArticleVersionServiceImpl.
 *
 * Key observations from source code:
 * - Injects TextMapper, OpenRouterService, self (ArticleVersionService via @Lazy), versionGeneratorExecutor
 * - checkAndGenerateVersions() loads Text by ID, checks which versions are missing, delegates to self.generateVersionsAsync()
 * - checkAndGenerateMarkdownVersions() additionally requires contentType == "MARKDOWN" and markdownContent non-null
 * - generateVersionsAsync() uses CompletableFuture + versionGeneratorExecutor for parallel generation
 * - saveVersionResults() / saveMarkdownVersionResults() use UpdateWrapper with deleted=0 guard
 *
 * Testing strategy:
 * - Mock TextMapper, OpenRouterService, self-proxy, and versionGeneratorExecutor
 * - Test checkAndGenerateVersions: text not found, null content, all versions present, missing versions
 * - Test checkAndGenerateMarkdownVersions: non-MARKDOWN type, null markdown content, missing versions
 * - Test saveVersionResults / saveMarkdownVersionResults: success and no-update cases
 * - For generateVersionsAsync: tested indirectly via the async flow mocking
 */
@ExtendWith(MockitoExtension.class)
class ArticleVersionServiceTest {

    @InjectMocks
    private ArticleVersionServiceImpl articleVersionService;

    @Mock
    private TextMapper textMapper;

    @Mock
    private OpenRouterService openRouterService;

    @Mock
    private ArticleVersionService self;

    @Mock
    private Executor versionGeneratorExecutor;

    private Text testText;

    @BeforeEach
    void setUp() {
        testText = new Text();
        testText.setId(1L);
        testText.setTitle("Test Article");
        testText.setContent("This is the original article content for testing.");
        testText.setContentType("TEXT");
        testText.setDeleted(0);
    }

    // ==================== checkAndGenerateVersions tests ====================

    @Test
    void checkAndGenerateVersions_textNotFound_doesNothing() {
        // Arrange
        when(textMapper.selectById(999L)).thenReturn(null);

        // Act
        articleVersionService.checkAndGenerateVersions(999L);

        // Assert: no async generation triggered
        verify(self, never()).generateVersionsAsync(anyLong(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void checkAndGenerateVersions_allVersionsPresent_doesNothing() {
        // Arrange: all versions already filled
        testText.setCet4Content("CET4 content");
        testText.setCet6Content("CET6 content");
        testText.setAcademicContent("Academic content");
        testText.setChineseParallelContent("Parallel content");
        when(textMapper.selectById(1L)).thenReturn(testText);

        // Act
        articleVersionService.checkAndGenerateVersions(1L);

        // Assert: no async generation triggered
        verify(self, never()).generateVersionsAsync(anyLong(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void checkAndGenerateVersions_allVersionsMissing_triggersAsync() {
        // Arrange: no version content set, all null
        when(textMapper.selectById(1L)).thenReturn(testText);

        // Act
        articleVersionService.checkAndGenerateVersions(1L);

        // Assert: triggers async generation with all flags true
        verify(self).generateVersionsAsync(eq(1L), eq(testText.getContent()),
                eq(true), eq(true), eq(true), eq(true));
    }

    @Test
    void checkAndGenerateVersions_someMissing_triggersAsyncWithCorrectFlags() {
        // Arrange: CET4 and Academic already present
        testText.setCet4Content("CET4 content");
        testText.setAcademicContent("Academic content");
        when(textMapper.selectById(1L)).thenReturn(testText);

        // Act
        articleVersionService.checkAndGenerateVersions(1L);

        // Assert: only CET6 and Chinese Parallel are needed
        verify(self).generateVersionsAsync(eq(1L), eq(testText.getContent()),
                eq(false), eq(true), eq(false), eq(true));
    }

    @Test
    void checkAndGenerateVersions_nullContent_doesNothing() {
        // Arrange: text exists but content is null
        testText.setContent(null);
        when(textMapper.selectById(1L)).thenReturn(testText);

        // Act
        articleVersionService.checkAndGenerateVersions(1L);

        // Assert: no async generation because content is null
        verify(self, never()).generateVersionsAsync(anyLong(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void checkAndGenerateVersions_blankContent_doesNothing() {
        // Arrange: text exists but content is blank
        testText.setContent("   ");
        when(textMapper.selectById(1L)).thenReturn(testText);

        // Act
        articleVersionService.checkAndGenerateVersions(1L);

        // Assert: no async generation because content is blank
        verify(self, never()).generateVersionsAsync(anyLong(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void checkAndGenerateVersions_emptyStringVersionTreatedAsMissing() {
        // Arrange: version content is empty string (not null)
        testText.setCet4Content("");
        testText.setCet6Content("  "); // whitespace only
        testText.setAcademicContent("Academic");
        testText.setChineseParallelContent("Parallel");
        when(textMapper.selectById(1L)).thenReturn(testText);

        // Act
        articleVersionService.checkAndGenerateVersions(1L);

        // Assert: CET4 (empty) and CET6 (whitespace) should be treated as missing
        verify(self).generateVersionsAsync(eq(1L), eq(testText.getContent()),
                eq(true), eq(true), eq(false), eq(false));
    }

    // ==================== checkAndGenerateMarkdownVersions tests ====================

    @Test
    void checkAndGenerateMarkdownVersions_textNotFound_doesNothing() {
        // Arrange
        when(textMapper.selectById(999L)).thenReturn(null);

        // Act
        articleVersionService.checkAndGenerateMarkdownVersions(999L);

        // Assert
        verify(self, never()).generateMarkdownVersionsAsync(anyLong(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void checkAndGenerateMarkdownVersions_notMarkdownType_doesNothing() {
        // Arrange: content type is TEXT, not MARKDOWN
        testText.setContentType("TEXT");
        when(textMapper.selectById(1L)).thenReturn(testText);

        // Act
        articleVersionService.checkAndGenerateMarkdownVersions(1L);

        // Assert
        verify(self, never()).generateMarkdownVersionsAsync(anyLong(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void checkAndGenerateMarkdownVersions_nullMarkdownContent_doesNothing() {
        // Arrange: MARKDOWN type but markdownContent is null
        testText.setContentType("MARKDOWN");
        testText.setMarkdownContent(null);
        when(textMapper.selectById(1L)).thenReturn(testText);

        // Act
        articleVersionService.checkAndGenerateMarkdownVersions(1L);

        // Assert
        verify(self, never()).generateMarkdownVersionsAsync(anyLong(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void checkAndGenerateMarkdownVersions_blankMarkdownContent_doesNothing() {
        // Arrange
        testText.setContentType("MARKDOWN");
        testText.setMarkdownContent("   ");
        when(textMapper.selectById(1L)).thenReturn(testText);

        // Act
        articleVersionService.checkAndGenerateMarkdownVersions(1L);

        // Assert
        verify(self, never()).generateMarkdownVersionsAsync(anyLong(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void checkAndGenerateMarkdownVersions_allVersionsPresent_doesNothing() {
        // Arrange
        testText.setContentType("MARKDOWN");
        testText.setMarkdownContent("# Original Markdown");
        testText.setCet4MarkdownContent("# CET4");
        testText.setCet6MarkdownContent("# CET6");
        testText.setAcademicMarkdownContent("# Academic");
        testText.setChineseParallelMarkdownContent("# Parallel");
        when(textMapper.selectById(1L)).thenReturn(testText);

        // Act
        articleVersionService.checkAndGenerateMarkdownVersions(1L);

        // Assert
        verify(self, never()).generateMarkdownVersionsAsync(anyLong(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void checkAndGenerateMarkdownVersions_allMissing_triggersAsync() {
        // Arrange
        testText.setContentType("MARKDOWN");
        testText.setMarkdownContent("# Original Markdown");
        // All markdown versions are null
        when(textMapper.selectById(1L)).thenReturn(testText);

        // Act
        articleVersionService.checkAndGenerateMarkdownVersions(1L);

        // Assert
        verify(self).generateMarkdownVersionsAsync(eq(1L), eq("# Original Markdown"),
                eq(true), eq(true), eq(true), eq(true));
    }

    @Test
    void checkAndGenerateMarkdownVersions_someMissing_triggersWithCorrectFlags() {
        // Arrange
        testText.setContentType("MARKDOWN");
        testText.setMarkdownContent("# Original Markdown");
        testText.setCet4MarkdownContent("# CET4 present");
        testText.setAcademicMarkdownContent("# Academic present");
        // CET6 and ChineseParallel are null
        when(textMapper.selectById(1L)).thenReturn(testText);

        // Act
        articleVersionService.checkAndGenerateMarkdownVersions(1L);

        // Assert
        verify(self).generateMarkdownVersionsAsync(eq(1L), eq("# Original Markdown"),
                eq(false), eq(true), eq(false), eq(true));
    }

    // ==================== saveVersionResults tests ====================

    @Test
    void saveVersionResults_success_updatesText() {
        // Arrange
        Map<String, String> results = new ConcurrentHashMap<>();
        results.put("cet4_content", "CET4 content");
        results.put("cet6_content", "CET6 content");
        when(textMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        // Act
        articleVersionService.saveVersionResults(1L, results);

        // Assert
        verify(textMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void saveVersionResults_noRowsUpdated_logsWarning() {
        // Arrange: text not found or deleted
        Map<String, String> results = new ConcurrentHashMap<>();
        results.put("cet4_content", "CET4 content");
        when(textMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        // Act: should not throw, just logs warning
        assertDoesNotThrow(() -> articleVersionService.saveVersionResults(999L, results));

        // Assert
        verify(textMapper).update(isNull(), any(UpdateWrapper.class));
    }

    // ==================== saveMarkdownVersionResults tests ====================

    @Test
    void saveMarkdownVersionResults_success_updatesText() {
        // Arrange
        Map<String, String> results = new ConcurrentHashMap<>();
        results.put("cet4_markdown_content", "# CET4 Markdown");
        when(textMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        // Act
        articleVersionService.saveMarkdownVersionResults(1L, results);

        // Assert
        verify(textMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void saveMarkdownVersionResults_noRowsUpdated_logsWarning() {
        // Arrange
        Map<String, String> results = new ConcurrentHashMap<>();
        results.put("cet4_markdown_content", "# CET4 Markdown");
        when(textMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        // Act
        assertDoesNotThrow(() -> articleVersionService.saveMarkdownVersionResults(999L, results));

        // Assert
        verify(textMapper).update(isNull(), any(UpdateWrapper.class));
    }
}
