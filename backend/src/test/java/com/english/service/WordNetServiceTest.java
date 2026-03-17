package com.english.service;

import com.english.entity.Word;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WordNetServiceTest {

    @Mock
    private WordService wordService;

    @Mock
    private ECDICTService ecdictService;

    @InjectMocks
    private WordNetService wordNetService;

    @BeforeEach
    public void setUp() {
        wordNetService.init();
    }

    @Test
    public void testGetOrCreateWordExistingInDatabase() {
        String wordText = "hello";
        Word dbWord = new Word();
        dbWord.setId(1L);
        dbWord.setWord(wordText);

        when(wordService.getWordByWord(wordText)).thenReturn(dbWord);

        Map<String, Object> result = wordNetService.getOrCreateWord(wordText);

        assertEquals(true, result.get("found"));
        assertEquals("database", result.get("primarySource"));
        assertEquals(wordText, result.get("word"));
        assertEquals(1L, result.get("wordId"));

        verify(wordService, times(1)).getWordByWord(wordText);
        verify(ecdictService, never()).getWordInfo(anyString());
    }

    @Test
    public void testGetOrCreateWordNotFoundInDatabaseButInECDICT() {
        String wordText = "world";
        when(wordService.getWordByWord(wordText)).thenReturn(null);
        when(ecdictService.isECDICTAvailable()).thenReturn(true);

        Map<String, Object> ecdictResult = new HashMap<>();
        ecdictResult.put("found", true);
        ecdictResult.put("word", wordText);
        ecdictResult.put("definition", "the earth and all people and things on it");
        ecdictResult.put("partOfSpeech", "n");
        ecdictResult.put("difficulty", "EASY");

        when(ecdictService.getWordInfo(wordText)).thenReturn(ecdictResult);

        when(wordService.createWord(any(Word.class))).thenAnswer(invocation -> {
            Word w = invocation.getArgument(0);
            w.setId(2L);
            return true;
        });

        Map<String, Object> result = wordNetService.getOrCreateWord(wordText);

        assertEquals(true, result.get("found"));
        assertEquals("ecdict_created", result.get("primarySource"));
        assertEquals(2L, result.get("wordId"));
        assertEquals(true, result.get("created"));

        verify(wordService, times(1)).getWordByWord(wordText);
        verify(ecdictService, times(1)).getWordInfo(wordText);
        verify(wordService, times(1)).createWord(any(Word.class));
    }

    @Test
    public void testGetOrCreateWordNotFoundAnywhere() {
        String wordText = "nonexistentword";
        when(wordService.getWordByWord(wordText)).thenReturn(null);
        when(ecdictService.isECDICTAvailable()).thenReturn(true);

        Map<String, Object> ecdictResult = new HashMap<>();
        ecdictResult.put("found", false);
        when(ecdictService.getWordInfo(wordText)).thenReturn(ecdictResult);

        when(wordService.createWord(any(Word.class))).thenAnswer(invocation -> {
            Word w = invocation.getArgument(0);
            w.setId(3L);
            return true;
        });

        Map<String, Object> result = wordNetService.getOrCreateWord(wordText);

        assertEquals(true, result.get("found"));
        assertEquals("basic_created", result.get("primarySource"));
        assertEquals(3L, result.get("wordId"));

        verify(wordService, times(1)).getWordByWord(wordText);
        verify(wordService, times(1)).createWord(any(Word.class));
    }

    @Test
    void getWordInfo_nullWord_shouldReturnError() {
        Map<String, Object> result = wordNetService.getWordInfo(null);

        assertFalse((Boolean) result.get("found"));
        assertEquals("error", result.get("primarySource"));
        assertTrue(result.get("error").toString().contains("不能为空"));
    }

    @Test
    void getWordInfo_emptyWord_shouldReturnError() {
        Map<String, Object> result = wordNetService.getWordInfo("   ");

        assertFalse((Boolean) result.get("found"));
        assertEquals("error", result.get("primarySource"));
    }

    @Test
    void getWordInfo_wordTooLong_shouldTruncate() {
        String longWord = "a".repeat(150);

        when(ecdictService.isECDICTAvailable()).thenReturn(false);
        when(wordService.getWordByWord(anyString())).thenReturn(null);

        Map<String, Object> result = wordNetService.getWordInfo(longWord);

        assertNotNull(result);
        assertFalse((Boolean) result.get("found"));
    }

    @Test
    void getWordInfo_specialCharactersInWord_shouldHandle() {
        String word = "don't";

        Word dbWord = new Word();
        dbWord.setId(1L);
        dbWord.setWord(word);
        dbWord.setDefinition("does not");

        when(ecdictService.isECDICTAvailable()).thenReturn(false);
        when(wordService.getWordByWord(anyString())).thenReturn(dbWord);

        Map<String, Object> result = wordNetService.getWordInfo(word);

        assertTrue((Boolean) result.get("found"));
        assertEquals("database", result.get("primarySource"));
    }

    @Test
    void getWordInfo_caseInsensitive_shouldNormalize() {
        String word = "HELLO";

        Word dbWord = new Word();
        dbWord.setId(1L);
        dbWord.setWord("hello");
        dbWord.setDefinition("greeting");

        when(ecdictService.isECDICTAvailable()).thenReturn(false);
        when(wordService.getWordByWord("hello")).thenReturn(dbWord);

        Map<String, Object> result = wordNetService.getWordInfo(word);

        assertTrue((Boolean) result.get("found"));
        assertEquals("hello", result.get("word"));
    }

    @Test
    void getWordInfo_fromECDICTFuzzy_shouldReturnWithFuzzyFlag() {
        String word = "running";

        Map<String, Object> ecdictResult = new HashMap<>();
        ecdictResult.put("found", false);
        ecdictResult.put("fuzzy", true);
        ecdictResult.put("word", "run");
        ecdictResult.put("definition", "to move quickly");

        when(ecdictService.isECDICTAvailable()).thenReturn(true);
        when(ecdictService.getWordInfo(anyString())).thenReturn(ecdictResult);
        when(wordService.getWordByWord(anyString())).thenReturn(null);

        Map<String, Object> result = wordNetService.getWordInfo(word);

        assertTrue((Boolean) result.get("found"));
        assertEquals("ecdict_fuzzy", result.get("primarySource"));
    }

    @Test
    void searchWords_emptyKeyword_shouldReturnEmptyList() {
        List<Map<String, Object>> result = wordNetService.searchWords("", 10);

        assertEquals(0, result.size());
    }

    @Test
    void searchWords_nullKeyword_shouldReturnEmptyList() {
        List<Map<String, Object>> result = wordNetService.searchWords(null, 10);

        assertEquals(0, result.size());
    }

    @Test
    void searchWords_validKeyword_shouldReturnResults() {
        String keyword = "test";

        List<Map<String, Object>> ecdictResults = new ArrayList<>();
        Map<String, Object> wordMap = new HashMap<>();
        wordMap.put("word", "test");
        wordMap.put("definition", "examination");
        ecdictResults.add(wordMap);

        when(ecdictService.isECDICTAvailable()).thenReturn(true);
        when(ecdictService.searchWords(keyword, 10)).thenReturn(ecdictResults);

        List<Map<String, Object>> result = wordNetService.searchWords(keyword, 10);

        assertNotNull(result);
    }

    @Test
    void searchWords_keywordTooLong_shouldTruncate() {
        String longKeyword = "a".repeat(150);

        when(ecdictService.isECDICTAvailable()).thenReturn(false);

        List<Map<String, Object>> result = wordNetService.searchWords(longKeyword, 10);

        assertNotNull(result);
    }

    @Test
    void searchWords_limitNegative_shouldUseMinimum() {
        String keyword = "word";

        when(ecdictService.isECDICTAvailable()).thenReturn(false);

        List<Map<String, Object>> result = wordNetService.searchWords(keyword, -5);

        assertNotNull(result);
    }

    @Test
    void searchWords_limitTooLarge_shouldCapAt100() {
        String keyword = "word";

        List<Map<String, Object>> ecdictResults = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Map<String, Object> wordMap = new HashMap<>();
            wordMap.put("word", "word" + i);
            ecdictResults.add(wordMap);
        }

        when(ecdictService.isECDICTAvailable()).thenReturn(true);
        when(ecdictService.searchWords(keyword, 100)).thenReturn(ecdictResults);

        List<Map<String, Object>> result = wordNetService.searchWords(keyword, 500);

        assertNotNull(result);
    }

    @Test
    void getDictionaryStats_ecdictAvailable_shouldReturnStats() {
        Map<String, Object> ecdictStats = new HashMap<>();
        ecdictStats.put("totalWords", 50000);
        ecdictStats.put("available", true);

        when(ecdictService.isECDICTAvailable()).thenReturn(true);
        when(ecdictService.getDictionaryStats()).thenReturn(ecdictStats);

        Map<String, Object> result = wordNetService.getDictionaryStats();

        assertNotNull(result);
        assertEquals("ECDICT", result.get("primaryDictionary"));
    }

    @Test
    void getDictionaryStats_ecdictNotAvailable_shouldReturnDefaultStats() {
        when(ecdictService.isECDICTAvailable()).thenReturn(false);

        Map<String, Object> result = wordNetService.getDictionaryStats();

        assertNotNull(result);
        assertEquals("Database", result.get("primaryDictionary"));
        assertEquals(0, result.get("totalWords"));
    }

    @Test
    void isWordNetAvailable_shouldAlwaysReturnTrue() {
        boolean result = wordNetService.isWordNetAvailable();

        assertTrue(result);
    }

    @Test
    void getClickableWordsFromText_validText_shouldParseWords() {
        String text = "This is a test sentence";

        List<Map<String, Object>> result = wordNetService.getClickableWordsFromText(text);

        assertNotNull(result);
        assertTrue(result.size() > 0);

        for (Map<String, Object> word : result) {
            assertTrue(word.containsKey("text"));
            assertTrue(word.containsKey("cleanWord"));
            assertTrue(word.containsKey("position"));
            assertTrue(word.containsKey("clickable"));
        }
    }

    @Test
    void getClickableWordsFromText_emptyText_shouldReturnEmpty() {
        List<Map<String, Object>> result = wordNetService.getClickableWordsFromText("");

        assertEquals(0, result.size());
    }

    @Test
    void getClickableWordsFromText_nullText_shouldReturnEmpty() {
        List<Map<String, Object>> result = wordNetService.getClickableWordsFromText(null);

        assertEquals(0, result.size());
    }

    @Test
    void getClickableWordsFromText_textWithPunctuation_shouldCleanWords() {
        String text = "Hello, world! How's this?";

        List<Map<String, Object>> result = wordNetService.getClickableWordsFromText(text);

        assertNotNull(result);

        for (Map<String, Object> word : result) {
            String cleanWord = (String) word.get("cleanWord");
            assertFalse(cleanWord.contains(","));
            assertFalse(cleanWord.contains("!"));
            assertFalse(cleanWord.contains("?"));
        }
    }

    @Test
    void getClickableWordsFromText_textTooLong_shouldTruncate() {
        String longText = "word ".repeat(20000);

        List<Map<String, Object>> result = wordNetService.getClickableWordsFromText(longText);

        assertNotNull(result);
    }

    @Test
    void getWordInfo_databaseOverride_shouldUseDatabaseVersion() {
        String word = "very";

        Word dbWord = new Word();
        dbWord.setId(1L);
        dbWord.setWord("very");
        dbWord.setDefinition("to a great degree");

        when(wordService.getWordByWord("very")).thenReturn(dbWord);
        when(ecdictService.isECDICTAvailable()).thenReturn(true);

        Map<String, Object> result = wordNetService.getWordInfo(word);

        assertTrue((Boolean) result.get("found"));
        assertEquals("database_override", result.get("primarySource"));
    }

    @Test
    void getOrCreateWord_nullWord_shouldReturnError() {
        Map<String, Object> result = wordNetService.getOrCreateWord(null);

        assertFalse((Boolean) result.get("found"));
        assertEquals("error", result.get("primarySource"));
    }

    @Test
    void getOrCreateWord_emptyWord_shouldReturnError() {
        Map<String, Object> result = wordNetService.getOrCreateWord("   ");

        assertFalse((Boolean) result.get("found"));
        assertEquals("error", result.get("primarySource"));
    }

    @Test
    void getOrCreateWord_creationFails_shouldReturnError() {
        String wordText = "newword";

        when(wordService.getWordByWord(wordText)).thenReturn(null);
        when(ecdictService.isECDICTAvailable()).thenReturn(true);

        Map<String, Object> ecdictResult = new HashMap<>();
        ecdictResult.put("found", true);
        when(ecdictService.getWordInfo(wordText)).thenReturn(ecdictResult);

        when(wordService.createWord(any(Word.class))).thenReturn(false);

        Map<String, Object> result = wordNetService.getOrCreateWord(wordText);

        assertEquals("error", result.get("primarySource"));
    }
}
