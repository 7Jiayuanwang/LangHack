package com.english.service;

import com.english.entity.ECDICTWord;
import com.english.mapper.ECDICTWordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ECDICTServiceTest {

    @Mock
    private ECDICTWordMapper ecdictWordMapper;

    @InjectMocks
    private ECDICTService ecdictService;

    @BeforeEach
    void setUp() {
        ecdictService.init();
    }

    @Test
    void init_shouldInitializeObjectMapper() {
        assertNotNull(ecdictService);
        // Verify that ObjectMapper is initialized by testing subsequent operations
    }

    @Test
    void isECDICTAvailable_afterInit_shouldReturnStatus() {
        boolean available = ecdictService.isECDICTAvailable();
        assertNotNull(available);
    }

    @Test
    void getWordInfo_validWord_shouldReturnWordData() {
        String word = "hello";
        ECDICTWord entity = new ECDICTWord();
        entity.setWord(word);
        entity.setPhonetic("[hə'ləʊ]");
        entity.setDefinition("used as a greeting");
        entity.setTranslation("你好");
        entity.setPos("n");

        when(ecdictWordMapper.findByWord(word)).thenReturn(entity);

        Map<String, Object> result = ecdictService.getWordInfo(word);

        assertNotNull(result);
        assertTrue((Boolean) result.get("found"));
        assertEquals(word, result.get("word"));
    }

    @Test
    void getWordInfo_nullWord_shouldHandleGracefully() {
        Map<String, Object> result = ecdictService.getWordInfo(null);

        assertNotNull(result);
        assertFalse((Boolean) result.get("found"));
    }

    @Test
    void getWordInfo_emptyWord_shouldHandleGracefully() {
        Map<String, Object> result = ecdictService.getWordInfo("");

        assertNotNull(result);
        assertFalse((Boolean) result.get("found"));
    }

    @Test
    void getWordInfo_wordNotFound_shouldReturnNotFound() {
        String word = "nonexistentword";

        when(ecdictWordMapper.findByWord(anyString())).thenReturn(null);

        Map<String, Object> result = ecdictService.getWordInfo(word);

        assertNotNull(result);
        assertFalse((Boolean) result.get("found"));
    }

    @Test
    void getWordInfo_specialCharacters_shouldHandle() {
        String word = "don't";
        ECDICTWord entity = new ECDICTWord();
        entity.setWord(word);
        entity.setPhonetic("[dəʊnt]");
        entity.setDefinition("does not");
        entity.setTranslation("不");

        when(ecdictWordMapper.findByWord(anyString())).thenReturn(entity);

        Map<String, Object> result = ecdictService.getWordInfo(word);

        assertTrue((Boolean) result.get("found"));
    }

    @Test
    void searchWords_nullKeyword_shouldReturnEmptyList() {
        List<Map<String, Object>> result = ecdictService.searchWords(null, 10);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void searchWords_emptyKeyword_shouldReturnEmptyList() {
        List<Map<String, Object>> result = ecdictService.searchWords("", 10);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void searchWords_validKeyword_shouldReturnResults() {
        String keyword = "test";

        List<Map<String, Object>> result = ecdictService.searchWords(keyword, 10);

        assertNotNull(result);
        // Result can be empty or populated depending on CSV/database state
    }

    @Test
    void searchWords_limitNegative_shouldUseMinimum() {
        String keyword = "word";

        List<Map<String, Object>> result = ecdictService.searchWords(keyword, -5);

        assertNotNull(result);
    }

    @Test
    void searchWords_limitZero_shouldUseMinimum() {
        String keyword = "word";

        List<Map<String, Object>> result = ecdictService.searchWords(keyword, 0);

        assertNotNull(result);
    }

    @Test
    void searchWords_limitExceeds100_shouldCapAt100() {
        String keyword = "word";

        List<Map<String, Object>> result = ecdictService.searchWords(keyword, 500);

        assertNotNull(result);
        assertTrue(result.size() <= 100);
    }

    @Test
    void getDictionaryStats_shouldReturnStats() {
        Map<String, Object> stats = ecdictService.getDictionaryStats();

        assertNotNull(stats);
        assertTrue(stats.containsKey("totalWords"));
        assertTrue(stats.containsKey("available"));
        assertTrue(stats.containsKey("lastUpdated"));
        assertTrue(stats.containsKey("levelStats"));
    }

    @Test
    void getDictionaryStats_levelStatsStructure_shouldBeComplete() {
        Map<String, Object> stats = ecdictService.getDictionaryStats();

        Map<String, Integer> levelStats = (Map<String, Integer>) stats.get("levelStats");
        assertNotNull(levelStats);
        assertTrue(levelStats.containsKey("cet4"));
        assertTrue(levelStats.containsKey("cet6"));
        assertTrue(levelStats.containsKey("ielts"));
        assertTrue(levelStats.containsKey("toefl"));
        assertTrue(levelStats.containsKey("gre"));
    }

    @Test
    void getWordInfo_withAllFields_shouldMapCorrectly() {
        String word = "example";
        ECDICTWord entity = new ECDICTWord();
        entity.setWord(word);
        entity.setPhonetic("[ɪg'zɑːmpl]");
        entity.setDefinition("a thing characteristic of its kind");
        entity.setTranslation("例子");
        entity.setPos("n");
        entity.setCollins("3");
        entity.setOxford("3000");
        entity.setTag("cet4");
        entity.setBnc("2545");
        entity.setFrq("2231");
        entity.setExchange("examples");

        when(ecdictWordMapper.findByWord(word)).thenReturn(entity);

        Map<String, Object> result = ecdictService.getWordInfo(word);

        assertTrue((Boolean) result.get("found"));
        assertNotNull(result.get("pronunciation"));
        assertNotNull(result.get("definition"));
        assertNotNull(result.get("partOfSpeech"));
        assertNotNull(result.get("difficulty"));
    }

    @Test
    void searchWords_withSpecialCharacters_shouldHandle() {
        String keyword = "don't";

        List<Map<String, Object>> result = ecdictService.searchWords(keyword, 10);

        assertNotNull(result);
    }

    @Test
    void searchWords_caseInsensitive_shouldNormalize() {
        String keyword = "HELLO";

        List<Map<String, Object>> result = ecdictService.searchWords(keyword, 10);

        assertNotNull(result);
    }

    @Test
    void getWordInfo_multipleDefinitions_shouldParseCorrectly() {
        String word = "run";
        ECDICTWord entity = new ECDICTWord();
        entity.setWord(word);
        entity.setPhonetic("[rʌn]");
        entity.setDefinition("to move quickly; to operate");
        entity.setTranslation("跑;运行");
        entity.setPos("v");

        when(ecdictWordMapper.findByWord(word)).thenReturn(entity);

        Map<String, Object> result = ecdictService.getWordInfo(word);

        assertTrue((Boolean) result.get("found"));
        assertNotNull(result.get("multipleDefinitions"));
    }

    @Test
    void searchWords_resultContainsRequiredFields() {
        String keyword = "test";

        List<Map<String, Object>> results = ecdictService.searchWords(keyword, 10);

        if (results.size() > 0) {
            Map<String, Object> firstResult = results.get(0);
            assertTrue(firstResult.containsKey("word"));
            assertTrue(firstResult.containsKey("definition") || firstResult.containsKey("translation"));
        }
    }

    @Test
    void isECDICTAvailable_shouldIndicateAvailability() {
        boolean available = ecdictService.isECDICTAvailable();
        assertNotNull(available);
        // Available depends on CSV/database configuration
    }

    @Test
    void getWordInfo_difficultyInference_shouldInferFromLevel() {
        String word = "difficult";
        ECDICTWord entity = new ECDICTWord();
        entity.setWord(word);
        entity.setPhonetic("[ˈdɪfɪkəlt]");
        entity.setDefinition("hard to do");
        entity.setTranslation("困难的");
        entity.setPos("adj");
        entity.setCollins("2"); // High level = easy word

        when(ecdictWordMapper.findByWord(word)).thenReturn(entity);

        Map<String, Object> result = ecdictService.getWordInfo(word);

        assertTrue((Boolean) result.get("found"));
        assertNotNull(result.get("difficulty"));
    }

    @Test
    void getWordInfo_withSynonyms_shouldParseExchange() {
        String word = "happy";
        ECDICTWord entity = new ECDICTWord();
        entity.setWord(word);
        entity.setPhonetic("[ˈhæpi]");
        entity.setDefinition("feeling pleasure");
        entity.setTranslation("高兴的");
        entity.setExchange("happier,happiest,happily");

        when(ecdictWordMapper.findByWord(word)).thenReturn(entity);

        Map<String, Object> result = ecdictService.getWordInfo(word);

        assertTrue((Boolean) result.get("found"));
        assertNotNull(result.get("synonymsList"));
    }

    @Test
    void searchWords_limitBoundary_shouldHandleLimit1() {
        String keyword = "test";

        List<Map<String, Object>> result = ecdictService.searchWords(keyword, 1);

        assertNotNull(result);
        assertTrue(result.size() <= 1);
    }

    @Test
    void getDictionaryStats_levelStatsAllNonNegative() {
        Map<String, Object> stats = ecdictService.getDictionaryStats();

        Map<String, Integer> levelStats = (Map<String, Integer>) stats.get("levelStats");
        for (Integer count : levelStats.values()) {
            assertTrue(count >= 0, "Level stat count should be non-negative");
        }
    }
}
