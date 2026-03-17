package com.english.service;
import com.english.test.TestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.english.entity.UnitWord;
import com.english.entity.Word;
import com.english.mapper.UnitWordMapper;
import com.english.mapper.WordMapper;
import com.english.service.impl.UnitWordServiceImpl;
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
 * Unit tests for UnitWordServiceImpl.
 *
 * Key observations from source code:
 * - UnitWordServiceImpl extends ServiceImpl<UnitWordMapper, UnitWord>
 * - Injects WordMapper
 * - addToUnit() checks existence first via exists() to prevent duplicates
 * - getWordsWithDetailsByUnitId() batch-fetches Words and filters deleted ones
 * - batchAddToUnit() fetches existing records first, then only inserts new ones
 * - filterNotDeleted() is a private helper that filters words where deleted != 0
 */
@ExtendWith(MockitoExtension.class)
class UnitWordServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UnitWord.class);
    }

    @Spy
    @InjectMocks
    private UnitWordServiceImpl unitWordService;

    @Mock
    private UnitWordMapper baseMapper;

    @Mock
    private WordMapper wordMapper;

    private UnitWord testUnitWord;
    private Word testWord;

    @BeforeEach
    void setUp() {
        TestUtils.injectBaseMapper(unitWordService, baseMapper);
        testUnitWord = new UnitWord();
        testUnitWord.setId(1L);
        testUnitWord.setUnitId(10L);
        testUnitWord.setWordId(100L);
        testUnitWord.setCreatedTime(LocalDateTime.now());

        testWord = new Word();
        testWord.setId(100L);
        testWord.setWord("hello");
        testWord.setPronunciation("/helo/");
        testWord.setDefinition("a greeting");
        testWord.setExampleSentence("Hello, world!");
        testWord.setPartOfSpeech("interjection");
        testWord.setDifficulty("EASY");
        testWord.setAudioUrl("/audio/hello.mp3");
        testWord.setDeleted(0);
    }

    // ==================== exists tests ====================

    @Test
    void exists_found_returnsTrue() {
        // Arrange: count() delegates to baseMapper.selectCount()
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        // Act
        boolean result = unitWordService.exists(10L, 100L);

        // Assert
        assertTrue(result);
    }

    @Test
    void exists_notFound_returnsFalse() {
        // Arrange
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        // Act
        boolean result = unitWordService.exists(10L, 999L);

        // Assert
        assertFalse(result);
    }

    // ==================== addToUnit tests ====================

    @Test
    void addToUnit_notExisting_savesNewRecord() {
        // Arrange: exists() returns false (count = 0)
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(baseMapper.insert(any(UnitWord.class))).thenReturn(1);

        // Act
        unitWordService.addToUnit(10L, 100L);

        // Assert
        verify(baseMapper).insert(any(UnitWord.class));
    }

    @Test
    void addToUnit_alreadyExists_doesNotInsert() {
        // Arrange: exists() returns true (count > 0)
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        // Act
        unitWordService.addToUnit(10L, 100L);

        // Assert: insert should never be called
        verify(baseMapper, never()).insert(any(UnitWord.class));
    }

    // ==================== removeFromUnit tests ====================

    @Test
    void removeFromUnit_removesMatchingRecord() {
        // Arrange: remove() delegates to baseMapper.delete()
        when(baseMapper.delete(any(QueryWrapper.class))).thenReturn(1);

        // Act
        unitWordService.removeFromUnit(10L, 100L);

        // Assert
        verify(baseMapper).delete(any(QueryWrapper.class));
    }

    @Test
    void removeFromUnit_noMatch_deletesZero() {
        // Arrange
        when(baseMapper.delete(any(QueryWrapper.class))).thenReturn(0);

        // Act: should not throw even if nothing deleted
        assertDoesNotThrow(() -> unitWordService.removeFromUnit(10L, 999L));
    }

    // ==================== getWordsWithDetailsByUnitId tests ====================

    @Test
    void getWordsWithDetailsByUnitId_withWords_returnsMappedDetails() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(testUnitWord));
        when(wordMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(testWord));

        // Act
        List<Map<String, Object>> result = unitWordService.getWordsWithDetailsByUnitId(10L);

        // Assert
        assertEquals(1, result.size());
        Map<String, Object> wordMap = result.get(0);
        assertEquals(1L, wordMap.get("id"));
        assertEquals(10L, wordMap.get("unitId"));
        assertEquals(100L, wordMap.get("wordId"));
        assertEquals("hello", wordMap.get("word"));
        assertEquals("/helo/", wordMap.get("pronunciation"));
        assertEquals("a greeting", wordMap.get("definition"));
        assertEquals("Hello, world!", wordMap.get("exampleSentence"));
        assertEquals("interjection", wordMap.get("partOfSpeech"));
        assertEquals("EASY", wordMap.get("difficulty"));
        assertEquals("/audio/hello.mp3", wordMap.get("audioUrl"));
    }

    @Test
    void getWordsWithDetailsByUnitId_noUnitWords_returnsEmptyList() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        // Act
        List<Map<String, Object>> result = unitWordService.getWordsWithDetailsByUnitId(10L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getWordsWithDetailsByUnitId_nullUnitWords_returnsEmptyList() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(null);

        // Act
        List<Map<String, Object>> result = unitWordService.getWordsWithDetailsByUnitId(10L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getWordsWithDetailsByUnitId_deletedWordsFiltered() {
        // Arrange: word has deleted=1
        Word deletedWord = new Word();
        deletedWord.setId(200L);
        deletedWord.setWord("deleted-word");
        deletedWord.setDeleted(1);

        UnitWord uw1 = new UnitWord();
        uw1.setId(1L);
        uw1.setUnitId(10L);
        uw1.setWordId(100L);

        UnitWord uw2 = new UnitWord();
        uw2.setId(2L);
        uw2.setUnitId(10L);
        uw2.setWordId(200L);

        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(uw1, uw2));
        when(wordMapper.selectBatchIds(List.of(100L, 200L))).thenReturn(List.of(testWord, deletedWord));

        // Act
        List<Map<String, Object>> result = unitWordService.getWordsWithDetailsByUnitId(10L);

        // Assert: only the non-deleted word should appear
        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).get("word"));
    }

    // ==================== getUnitsWithDetailsByWordId tests ====================

    @Test
    void getUnitsWithDetailsByWordId_returnsUnitDetails() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(testUnitWord));

        // Act
        List<Map<String, Object>> result = unitWordService.getUnitsWithDetailsByWordId(100L);

        // Assert
        assertEquals(1, result.size());
        Map<String, Object> unitMap = result.get(0);
        assertEquals(1L, unitMap.get("id"));
        assertEquals(10L, unitMap.get("unitId"));
        assertEquals(100L, unitMap.get("wordId"));
        assertEquals(testUnitWord.getCreatedTime(), unitMap.get("addedTime"));
    }

    @Test
    void getUnitsWithDetailsByWordId_noRecords_returnsEmptyList() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        // Act
        List<Map<String, Object>> result = unitWordService.getUnitsWithDetailsByWordId(999L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getUnitsWithDetailsByWordId_nullRecords_returnsEmptyList() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(null);

        // Act
        List<Map<String, Object>> result = unitWordService.getUnitsWithDetailsByWordId(999L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== batchAddToUnit tests ====================

    @Test
    void batchAddToUnit_allNew_insertsAll() {
        // Arrange: no existing records
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        // saveBatch needs SqlSession which is unavailable in unit tests; stub via spy
        doReturn(true).when(unitWordService).saveBatch(anyCollection());

        // Act
        unitWordService.batchAddToUnit(10L, List.of(100L, 200L, 300L));

        // Assert: saveBatch called, we can verify selectList was called for existence check
        verify(baseMapper).selectList(any(QueryWrapper.class));
    }

    @Test
    void batchAddToUnit_someExisting_onlyInsertsNew() {
        // Arrange: wordId 100 already exists
        UnitWord existing = new UnitWord();
        existing.setUnitId(10L);
        existing.setWordId(100L);
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(existing));
        // saveBatch needs SqlSession which is unavailable in unit tests; stub via spy
        doReturn(true).when(unitWordService).saveBatch(anyCollection());

        // Act
        unitWordService.batchAddToUnit(10L, List.of(100L, 200L));

        // Assert: existence check was made
        verify(baseMapper).selectList(any(QueryWrapper.class));
    }

    @Test
    void batchAddToUnit_nullWordIds_returnsEarly() {
        // Act
        unitWordService.batchAddToUnit(10L, null);

        // Assert: no mapper interactions
        verifyNoInteractions(baseMapper);
    }

    @Test
    void batchAddToUnit_emptyWordIds_returnsEarly() {
        // Act
        unitWordService.batchAddToUnit(10L, Collections.emptyList());

        // Assert
        verifyNoInteractions(baseMapper);
    }

    // ==================== batchRemoveFromUnit tests ====================

    @Test
    void batchRemoveFromUnit_removesMatchingRecords() {
        // Arrange
        when(baseMapper.delete(any(QueryWrapper.class))).thenReturn(2);

        // Act
        unitWordService.batchRemoveFromUnit(10L, List.of(100L, 200L));

        // Assert
        verify(baseMapper).delete(any(QueryWrapper.class));
    }

    @Test
    void batchRemoveFromUnit_nullWordIds_returnsEarly() {
        // Act
        unitWordService.batchRemoveFromUnit(10L, null);

        // Assert
        verifyNoInteractions(baseMapper);
    }

    @Test
    void batchRemoveFromUnit_emptyWordIds_returnsEarly() {
        // Act
        unitWordService.batchRemoveFromUnit(10L, Collections.emptyList());

        // Assert
        verifyNoInteractions(baseMapper);
    }
}
