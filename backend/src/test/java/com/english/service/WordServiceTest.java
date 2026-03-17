package com.english.service;
import com.english.test.TestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.Word;
import com.english.exception.BusinessException;
import com.english.mapper.WordMapper;
import com.english.service.impl.WordServiceImpl;
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
 * Unit tests for WordServiceImpl.
 *
 * Key observations from source code:
 * - WordServiceImpl extends ServiceImpl<WordMapper, Word>
 * - No additional injected dependencies (only uses baseMapper via ServiceImpl)
 * - Word entity has @TableLogic on deleted field
 * - createWord() normalizes word to lowercase, sets default difficulty MEDIUM
 * - deleteWord() uses removeById() (correct @TableLogic pattern)
 * - getWordById() uses manual deleted=0 filter (not getById)
 * - getRandomWords() clamps count to [1,100], handles zero-word case
 * - getWordStats() counts words per difficulty level
 */
@ExtendWith(MockitoExtension.class)
class WordServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Word.class);
    }

    @InjectMocks
    private WordServiceImpl wordService;

    @Mock
    private WordMapper baseMapper;

    private Word testWord;

    @BeforeEach
    void setUp() {
        testWord = new Word();
        testWord.setId(1L);
        testWord.setWord("hello");
        testWord.setPronunciation("/helo/");
        testWord.setDefinition("a greeting");
        testWord.setExampleSentence("Hello, world!");
        testWord.setPartOfSpeech("interjection");
        testWord.setDifficulty("EASY");
        testWord.setAudioUrl("/audio/hello.mp3");
        testWord.setDeleted(0);
        testWord.setCreatedTime(LocalDateTime.now());
        testWord.setUpdatedTime(LocalDateTime.now());
    }

    // ==================== getWordsByPage tests ====================

    @Test
    void getWordsByPage_defaultParams_returnsPage() {
        // Arrange
        Map<String, Object> params = new HashMap<>();

        Page<Word> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(testWord));
        mockPage.setTotal(1);

        when(baseMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        // Act
        Map<String, Object> result = wordService.getWordsByPage(params);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.get("total"));
        assertNotNull(result.get("records"));
        assertEquals(1L, result.get("current"));
    }

    @Test
    void getWordsByPage_withKeywordAndDifficulty_filtersCorrectly() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("page", 2);
        params.put("size", 5);
        params.put("keyword", "greet");
        params.put("difficulty", "EASY");

        Page<Word> mockPage = new Page<>(2, 5);
        mockPage.setRecords(List.of(testWord));
        mockPage.setTotal(1);

        when(baseMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        // Act
        Map<String, Object> result = wordService.getWordsByPage(params);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("records"));
    }

    @Test
    void getWordsByPage_withUnitId_filtersCorrectly() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("unitId", 5L);

        Page<Word> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Collections.emptyList());
        mockPage.setTotal(0);

        when(baseMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        // Act
        Map<String, Object> result = wordService.getWordsByPage(params);

        // Assert
        assertEquals(0L, result.get("total"));
    }

    // ==================== getWordById tests ====================

    @Test
    void getWordById_found_returnsWord() {
        // Arrange: uses getOne with deleted=0 filter
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testWord);

        // Act
        Word result = wordService.getWordById(1L);

        // Assert
        assertNotNull(result);
        assertEquals("hello", result.getWord());
    }

    @Test
    void getWordById_notFound_returnsNull() {
        // Arrange
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // Act
        Word result = wordService.getWordById(999L);

        // Assert
        assertNull(result);
    }

    // ==================== getWordByWord tests ====================

    @Test
    void getWordByWord_found_returnsWord() {
        // Arrange
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testWord);

        // Act
        Word result = wordService.getWordByWord("hello");

        // Assert
        assertNotNull(result);
        assertEquals("hello", result.getWord());
    }

    @Test
    void getWordByWord_null_returnsNull() {
        // Act
        Word result = wordService.getWordByWord(null);

        // Assert
        assertNull(result);
        verifyNoInteractions(baseMapper);
    }

    @Test
    void getWordByWord_notFound_returnsNull() {
        // Arrange
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // Act
        Word result = wordService.getWordByWord("nonexistent");

        // Assert
        assertNull(result);
    }

    // ==================== createWord tests ====================

    @Test
    void createWord_success_normalizesAndSaves() {
        // Arrange
        Word newWord = new Word();
        newWord.setWord("  HELLO  ");
        newWord.setDefinition("greeting");

        when(baseMapper.insert(any(Word.class))).thenReturn(1);

        // Act
        boolean result = wordService.createWord(newWord);

        // Assert
        assertTrue(result);
        assertEquals("hello", newWord.getWord()); // normalized to lowercase + trimmed
        assertEquals("MEDIUM", newWord.getDifficulty()); // default difficulty
        assertEquals(0, newWord.getDeleted());
        assertNotNull(newWord.getCreatedTime());
        assertNotNull(newWord.getUpdatedTime());
        verify(baseMapper).insert(any(Word.class));
    }

    @Test
    void createWord_withExistingDifficulty_preservesDifficulty() {
        // Arrange
        Word newWord = new Word();
        newWord.setWord("test");
        newWord.setDifficulty("HARD");

        when(baseMapper.insert(any(Word.class))).thenReturn(1);

        // Act
        wordService.createWord(newWord);

        // Assert
        assertEquals("HARD", newWord.getDifficulty());
    }

    @Test
    void createWord_nullWord_throwsBusinessException() {
        // Arrange
        Word newWord = new Word();
        newWord.setWord(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> wordService.createWord(newWord));
        assertEquals("单词不能为空", exception.getMessage());
    }

    @Test
    void createWord_emptyWord_throwsBusinessException() {
        // Arrange
        Word newWord = new Word();
        newWord.setWord("   ");

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> wordService.createWord(newWord));
        assertEquals("单词不能为空", exception.getMessage());
    }

    @Test
    void createWord_nullObject_throwsBusinessException() {
        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> wordService.createWord(null));
        assertEquals("单词不能为空", exception.getMessage());
    }

    // ==================== updateWord tests ====================

    @Test
    void updateWord_success_setsUpdatedTime() {
        // Arrange
        Word updateWord = new Word();
        updateWord.setId(1L);
        updateWord.setDefinition("updated definition");

        when(baseMapper.updateById(any(Word.class))).thenReturn(1);

        // Act
        boolean result = wordService.updateWord(updateWord);

        // Assert
        assertTrue(result);
        assertNotNull(updateWord.getUpdatedTime());
        verify(baseMapper).updateById(updateWord);
    }

    @Test
    void updateWord_nullWord_throwsBusinessException() {
        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> wordService.updateWord(null));
        assertEquals("单词ID不能为空", exception.getMessage());
    }

    @Test
    void updateWord_nullId_throwsBusinessException() {
        // Arrange
        Word updateWord = new Word();
        updateWord.setId(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> wordService.updateWord(updateWord));
        assertEquals("单词ID不能为空", exception.getMessage());
    }

    // ==================== deleteWord tests ====================

    @Test
    void deleteWord_success_usesRemoveById() {
        // Arrange: removeById with @TableLogic + fill=INSERT_UPDATE
        // internally calls deleteById(Word entity), not deleteById(Serializable id)
        when(baseMapper.deleteById(any(Word.class))).thenReturn(1);

        // Act
        boolean result = wordService.deleteWord(1L);

        // Assert
        assertTrue(result);
        verify(baseMapper).deleteById(any(Word.class));
    }

    // ==================== getWordsByUnit tests ====================

    @Test
    void getWordsByUnit_returnsWords() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(testWord));

        // Act
        List<Word> result = wordService.getWordsByUnit(10L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).getWord());
    }

    @Test
    void getWordsByUnit_noWords_returnsEmptyList() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        // Act
        List<Word> result = wordService.getWordsByUnit(999L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getWordsByDifficulty tests ====================

    @Test
    void getWordsByDifficulty_returnsMatchingWords() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(testWord));

        // Act
        List<Word> result = wordService.getWordsByDifficulty("EASY");

        // Assert
        assertEquals(1, result.size());
        assertEquals("EASY", result.get(0).getDifficulty());
    }

    // ==================== getRandomWords tests ====================

    @Test
    void getRandomWords_totalZero_returnsEmptyList() {
        // Arrange
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        // Act
        List<Word> result = wordService.getRandomWords(5);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getRandomWords_totalNull_returnsEmptyList() {
        // Arrange
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(null);

        // Act
        List<Word> result = wordService.getRandomWords(5);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getRandomWords_fewerThanRequested_returnsAllWords() {
        // Arrange: total=2, requested=5, so should return all
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(2L);
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(testWord));

        // Act
        List<Word> result = wordService.getRandomWords(5);

        // Assert
        assertNotNull(result);
        // Verify it used list() instead of page()
        verify(baseMapper).selectList(any(QueryWrapper.class));
    }

    @Test
    void getRandomWords_moreThanRequested_returnsRandomPage() {
        // Arrange: total=100, requested=5
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(100L);

        Page<Word> mockPage = new Page<>(1, 5);
        mockPage.setRecords(List.of(testWord));
        when(baseMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        // Act
        List<Word> result = wordService.getRandomWords(5);

        // Assert
        assertNotNull(result);
        verify(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    void getRandomWords_countClampedToMinimumOne() {
        // Arrange: count = -5, clamped to 1; total = 10
        when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(10L);

        Page<Word> mockPage = new Page<>(1, 1);
        mockPage.setRecords(List.of(testWord));
        when(baseMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        // Act
        List<Word> result = wordService.getRandomWords(-5);

        // Assert
        assertNotNull(result);
    }

    // ==================== searchWords tests ====================

    @Test
    void searchWords_withKeyword_returnsMatchingWords() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(testWord));

        // Act
        List<Word> result = wordService.searchWords("hello");

        // Assert
        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).getWord());
    }

    @Test
    void searchWords_nullKeyword_returnsAllNonDeletedWords() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(testWord));

        // Act
        List<Word> result = wordService.searchWords(null);

        // Assert: still returns results (no keyword filter applied)
        assertNotNull(result);
    }

    @Test
    void searchWords_emptyKeyword_returnsAllNonDeletedWords() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(testWord));

        // Act
        List<Word> result = wordService.searchWords("   ");

        // Assert
        assertNotNull(result);
    }

    // ==================== getWordStats tests ====================

    @Test
    void getWordStats_returnsAllDifficultyCounts() {
        // Arrange: 3 separate count calls for EASY, MEDIUM, HARD
        when(baseMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(10L)   // EASY
                .thenReturn(20L)   // MEDIUM
                .thenReturn(5L);   // HARD

        // Act
        Map<String, Object> stats = wordService.getWordStats();

        // Assert
        assertNotNull(stats);
        assertEquals(10L, stats.get("totalEasy"));
        assertEquals(20L, stats.get("totalMedium"));
        assertEquals(5L, stats.get("totalHard"));
        assertEquals(35L, stats.get("totalWords"));
    }

    @Test
    void getWordStats_zeroCounts_returnsZeros() {
        // Arrange
        when(baseMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(0L)
                .thenReturn(0L)
                .thenReturn(0L);

        // Act
        Map<String, Object> stats = wordService.getWordStats();

        // Assert
        assertEquals(0L, stats.get("totalEasy"));
        assertEquals(0L, stats.get("totalMedium"));
        assertEquals(0L, stats.get("totalHard"));
        assertEquals(0L, stats.get("totalWords"));
    }
}
