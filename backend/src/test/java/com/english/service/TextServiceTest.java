package com.english.service;
import com.english.test.TestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.english.entity.Text;
import com.english.entity.ArticleType;
import com.english.dto.ArticleFilterDTO;
import com.english.exception.BusinessException;
import com.english.mapper.TextMapper;
import com.english.mapper.TextExerciseMapper;
import com.english.service.impl.TextServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TextServiceImpl.
 *
 * Key observations from source code:
 * - TextServiceImpl extends ServiceImpl<TextMapper, Text>
 * - Text entity has @TableLogic on 'deleted' field
 * - deleteText() calls underlineService.deleteUnderlinesByTextId(id) then removeById(id)
 * - createText() sets default difficulty to "MEDIUM" if null
 * - updateText() uses UpdateWrapper (not updateById)
 * - getTextById() uses getOne(QueryWrapper) with deleted=0 check (not selectById)
 * - @Autowired dependency: UnderlineService
 * - Text entity fields: createdAt/updatedAt (Java) mapped to created_time/updated_time (DB)
 *   Getters: getCreatedAt(), getCreatedTime() (alias), getUpdatedAt()
 *   Setters: setCreatedAt(), setUpdatedAt()
 */
@ExtendWith(MockitoExtension.class)
class TextServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Text.class);
    }

    @InjectMocks
    private TextServiceImpl textService;

    @Mock
    private TextMapper baseMapper;

    @Mock
    private UnderlineService underlineService;

    @Mock
    private TextExerciseMapper textExerciseMapper;

    private Text testText;

    @BeforeEach
    void setUp() {
        testText = new Text();
        testText.setId(1L);
        testText.setUnitId(10L);
        testText.setTitle("Introduction to English");
        testText.setContent("This is a sample text content.");
        testText.setDifficulty("MEDIUM");
        testText.setContentType("ARTICLE");
        testText.setOrderNum(1);
        testText.setDeleted(0);
        testText.setCreatedAt(LocalDateTime.now());
        testText.setUpdatedAt(LocalDateTime.now());
    }

    // ==================== createText tests ====================

    @Test
    void createText_success_setsDefaultFields() {
        // Arrange
        Text newText = new Text();
        newText.setTitle("New Text");
        newText.setContent("Content here");

        when(baseMapper.insert(any(Text.class))).thenReturn(1);

        // Act
        textService.createText(newText);

        // Assert
        assertEquals("MEDIUM", newText.getDifficulty()); // default difficulty
        assertEquals(0, newText.getDeleted());
        assertNotNull(newText.getCreatedAt());
        assertNotNull(newText.getUpdatedAt());
        verify(baseMapper).insert(any(Text.class));
    }

    @Test
    void createText_withCustomDifficulty_preservesDifficulty() {
        // Arrange
        Text newText = new Text();
        newText.setTitle("Hard Text");
        newText.setContent("Difficult content");
        newText.setDifficulty("HARD");

        when(baseMapper.insert(any(Text.class))).thenReturn(1);

        // Act
        textService.createText(newText);

        // Assert
        assertEquals("HARD", newText.getDifficulty()); // NOT overridden to MEDIUM
    }

    // ==================== getTextById tests ====================

    @Test
    void getTextById_found_returnsText() {
        // Arrange: getTextById uses getOne(QueryWrapper) with eq("id", id).eq("deleted", 0)
        // which translates to baseMapper.selectOne(QueryWrapper)
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testText);

        // Act
        Text result = textService.getTextById(1L);

        // Assert
        assertNotNull(result);
        assertEquals("Introduction to English", result.getTitle());
        verify(baseMapper).selectOne(any(QueryWrapper.class));
    }

    @Test
    void getTextById_notFound_returnsNull() {
        // Arrange
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // Act
        Text result = textService.getTextById(999L);

        // Assert
        assertNull(result);
    }

    // ==================== updateText tests ====================

    @Test
    void updateText_success_usesUpdateWrapper() {
        // Arrange: updateText first calls getTextById (which uses selectOne),
        // then builds an UpdateWrapper and calls update(null, updateWrapper)
        Text updateText = new Text();
        updateText.setId(1L);
        updateText.setTitle("Updated Title");
        updateText.setContent("Updated content");

        // Mock getTextById -> selectOne returns existing text
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testText);
        // Mock update(null, UpdateWrapper)
        when(baseMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        // Act
        textService.updateText(updateText);

        // Assert
        assertNotNull(updateText.getUpdatedAt());
        verify(baseMapper).selectOne(any(QueryWrapper.class));
        verify(baseMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void updateText_nullText_throwsBusinessException() {
        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> textService.updateText(null));
        assertEquals("课文对象及ID不能为空", exception.getMessage());
    }

    @Test
    void updateText_nullId_throwsBusinessException() {
        // Arrange
        Text noIdText = new Text();
        noIdText.setTitle("No ID");

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> textService.updateText(noIdText));
        assertEquals("课文对象及ID不能为空", exception.getMessage());
    }

    @Test
    void updateText_textNotFound_throwsBusinessException() {
        // Arrange
        Text updateText = new Text();
        updateText.setId(999L);
        updateText.setTitle("Non-existent");

        // getTextById returns null (text does not exist or is deleted)
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> textService.updateText(updateText));
        assertEquals("课文不存在或已被删除", exception.getMessage());
    }

    // ==================== deleteText tests ====================

    @Test
    void deleteText_success_deletesUnderlinesAndRemovesById() {
        // Arrange: deleteText calls getTextById (selectOne), then
        // underlineService.deleteUnderlinesByTextId(id),
        // textExerciseMapper.deleteExercisesByTextId(id), then removeById(id)
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testText);
        doNothing().when(underlineService).deleteUnderlinesByTextId(1L);
        when(textExerciseMapper.deleteExercisesByTextId(1L)).thenReturn(0);
        // Text entity has @TableLogic + fill=INSERT_UPDATE, so removeById(id)
        // internally calls deleteById(Text entity), not deleteById(Serializable id)
        when(baseMapper.deleteById(any(Text.class))).thenReturn(1);

        // Act
        textService.deleteText(1L);

        // Assert: verify cascade deletions happened before removeById
        InOrder inOrder = inOrder(underlineService, textExerciseMapper, baseMapper);
        inOrder.verify(underlineService).deleteUnderlinesByTextId(1L);
        inOrder.verify(textExerciseMapper).deleteExercisesByTextId(1L);
        inOrder.verify(baseMapper).deleteById(any(Text.class));
    }

    @Test
    void deleteText_notFound_throwsBusinessException() {
        // Arrange: getTextById returns null
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> textService.deleteText(999L));
        assertEquals("课文不存在或已被删除", exception.getMessage());

        // Verify underlineService was never called
        verify(underlineService, never()).deleteUnderlinesByTextId(anyLong());
        verify(baseMapper, never()).deleteById(anyLong());
    }

    // ==================== getTextsByUnit tests ====================

    @Test
    void getTextsByUnit_returnsTextsForUnit() {
        // Arrange
        Text text1 = new Text();
        text1.setId(1L);
        text1.setUnitId(10L);
        text1.setTitle("Text 1");

        Text text2 = new Text();
        text2.setId(2L);
        text2.setUnitId(10L);
        text2.setTitle("Text 2");

        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(text1, text2));

        // Act
        List<Text> result = textService.getTextsByUnit(10L);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    // ==================== getTextsByDifficulty tests ====================

    @Test
    void getTextsByDifficulty_returnsMatchingTexts() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(testText));

        // Act
        List<Text> result = textService.getTextsByDifficulty("MEDIUM");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("MEDIUM", result.get(0).getDifficulty());
    }

    @Test
    void getArticles_filtersByTextbookContentSection() {
        ArticleFilterDTO filter = new ArticleFilterDTO();
        filter.setArticleType(ArticleType.INNER);
        filter.setBookVolume("综合教程1");
        filter.setContentSection("iExplore1");
        filter.setUnitNum(2);
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(testText));

        List<Text> result = textService.getArticles(filter);

        ArgumentCaptor<QueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(baseMapper).selectList(wrapperCaptor.capture());
        assertEquals(1, result.size());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("content_section"));
    }

    // ==================== searchTexts tests ====================

    @Test
    void searchTexts_withKeyword_returnsMatchingTexts() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(testText));

        // Act
        List<Text> result = textService.searchTexts("Introduction");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Introduction to English", result.get(0).getTitle());
    }

    @Test
    void searchTexts_nullKeyword_returnsAllTexts() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(testText));

        // Act
        List<Text> result = textService.searchTexts(null);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // ==================== getTextsByUploader tests ====================

    @Test
    void getTextsByUploader_returnsTextsForUploader() {
        // Arrange
        testText.setUploadedBy(42L);
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(testText));

        // Act
        List<Text> result = textService.getTextsByUploader(42L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getTextsByUploader_nullUploaderId_returnsEmptyList() {
        // Act
        List<Text> result = textService.getTextsByUploader(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(baseMapper, never()).selectList(any());
    }

    // ==================== incrementViewCount tests ====================

    @Test
    void incrementViewCount_callsUpdateWithSql() {
        // Arrange
        when(baseMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        // Act
        textService.incrementViewCount(1L);

        // Assert
        verify(baseMapper).update(isNull(), any(UpdateWrapper.class));
    }

    // ==================== incrementLikeCount tests ====================

    @Test
    void incrementLikeCount_callsUpdateWithSql() {
        // Arrange
        when(baseMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        // Act
        textService.incrementLikeCount(1L);

        // Assert
        verify(baseMapper).update(isNull(), any(UpdateWrapper.class));
    }
}
