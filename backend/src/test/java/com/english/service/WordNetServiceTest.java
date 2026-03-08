package com.english.service;

import com.english.entity.Word;
import com.english.service.impl.WordServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class WordNetServiceTest {

    @Mock
    private WordService wordService;

    @Mock
    private ECDICTService ecdictService;

    @InjectMocks
    private WordNetService wordNetService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        wordNetService.init(); // Initialize ObjectMapper and other things
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

        // Simulate word deletion/creation
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

        // Should fall back to createBasicWord
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
}
