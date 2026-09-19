package com.english.service;

import com.english.dto.learner.ArticleLearningInsightDto.KnowledgePoint;
import com.english.entity.ECDICTWord;
import com.english.mapper.ECDICTWordMapper;
import com.english.service.impl.ArticleInsightGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleInsightGenerationServiceTest {
    @Mock private ECDICTWordMapper dictionary;

    @Test
    void selectsLevelMatchedVocabularyAndQuotesOriginalSentence() {
        when(dictionary.selectList(any())).thenReturn(List.of(
                entry("important", "重要的", "cet4 cet6"),
                entry("resilience", "韧性", "cet6 ky")));
        String source = "Building resilience helps students learn from setbacks and continue improving their skills. "
                + "This habit is important because progress is rarely a straight line for anyone.";

        List<KnowledgePoint> points = new ArticleInsightGenerationService(dictionary).generate(source, "CET6");

        assertFalse(points.isEmpty());
        assertEquals("resilience", points.get(0).getExpression());
        assertEquals("VOCABULARY", points.get(0).getType());
        assertFalse(points.get(0).getSourceSentence().isBlank());
    }

    @Test
    void extractsOnlyAnActuallyPresentGrammarPatternWhenDictionaryHasNoMatch() {
        when(dictionary.selectList(any())).thenReturn(List.of());
        String source = "Although the research team faced a difficult challenge, they continued collecting useful evidence. "
                + "Their careful work helped the community understand how people learn languages.";

        List<KnowledgePoint> points = new ArticleInsightGenerationService(dictionary).generate(source, "POSTGRADUATE");

        assertEquals("GRAMMAR", points.get(0).getType());
        assertEquals("Although ..., ...", points.get(0).getExpression());
        assertEquals(source.split("\\. ")[0] + ".", points.get(0).getSourceSentence());
    }

    private ECDICTWord entry(String word, String translation, String tag) {
        ECDICTWord entry = new ECDICTWord();
        entry.setWord(word);
        entry.setTranslation(translation);
        entry.setTag(tag);
        return entry;
    }
}
