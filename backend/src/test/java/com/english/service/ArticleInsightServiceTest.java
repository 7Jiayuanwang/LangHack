package com.english.service;

import com.english.dto.learner.ArticleLearningInsightDto;
import com.english.dto.learner.ArticleLearningInsightDto.KnowledgePoint;
import com.english.entity.ArticleInsightMessage;
import com.english.entity.ArticleLearningInsight;
import com.english.entity.Text;
import com.english.entity.TextLearningRecord;
import com.english.exception.BusinessException;
import com.english.mapper.ArticleInsightMessageMapper;
import com.english.mapper.ArticleLearningInsightMapper;
import com.english.mapper.TextLearningRecordMapper;
import com.english.mapper.TextMapper;
import com.english.service.impl.ArticleInsightGenerationService;
import com.english.service.impl.ArticleInsightLevelService;
import com.english.service.impl.ArticleInsightService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleInsightServiceTest {
    @Mock private ArticleLearningInsightMapper insights;
    @Mock private ArticleInsightMessageMapper messages;
    @Mock private TextLearningRecordMapper readings;
    @Mock private TextMapper texts;
    @Mock private UserService users;
    @Mock private ArticleInsightLevelService levels;
    @Mock private ArticleInsightGenerationService generator;
    private ArticleInsightService service;

    @BeforeEach
    void setUp() {
        service = new ArticleInsightService(insights, messages, readings, texts, users, levels,
                generator, new ObjectMapper(), Runnable::run);
    }

    @Test
    void rejectsInsightAccessBeforeOwnArticleIsCompleted() {
        assertThrows(BusinessException.class, () -> service.getForCompletedArticle(7L, 8L));
        verify(texts, never()).selectById(any());
        verify(levels, never()).resolve(any());
    }

    @Test
    void generatesOnceAndReusesSavedPointsForSameArticleLevelAndVersion() {
        TextLearningRecord reading = new TextLearningRecord();
        reading.setTextId(8L);
        reading.setContentVersion("cet6");
        reading.setIsCompleted(1);
        when(readings.selectOne(any())).thenReturn(reading);
        Text text = new Text();
        text.setId(8L);
        text.setContentType("TEXT");
        text.setCet6Content("Building resilience helps students learn from setbacks and continue improving their skills.");
        when(texts.selectById(8L)).thenReturn(text);
        when(levels.resolve(7L)).thenReturn(new ArticleInsightLevelService.Decision("CET6", "已达四级门槛"));
        when(levels.resolve(9L)).thenReturn(new ArticleInsightLevelService.Decision("CET6", "已达四级门槛"));
        KnowledgePoint point = new KnowledgePoint();
        point.setType("VOCABULARY");
        point.setExpression("resilience");
        point.setSourceSentence("Building resilience helps students learn from setbacks and continue improving their skills.");
        when(generator.generate(any(), any())).thenReturn(List.of(point));
        AtomicReference<ArticleLearningInsight> cached = new AtomicReference<>();
        when(insights.selectOne(any())).thenAnswer(invocation -> cached.get());
        doAnswer(invocation -> {
            ArticleLearningInsight entity = invocation.getArgument(0);
            entity.setId(21L);
            cached.set(entity);
            return 1;
        }).when(insights).insert(any());
        when(messages.selectCount(any())).thenReturn(0L, 1L, 0L);

        ArticleLearningInsightDto first = service.getForCompletedArticle(7L, 8L);
        ArticleLearningInsightDto second = service.getForCompletedArticle(7L, 8L);
        ArticleLearningInsightDto anotherStudent = service.getForCompletedArticle(9L, 8L);

        assertEquals("PENDING", first.getStatus());
        assertEquals("READY", second.getStatus());
        assertEquals("resilience", second.getPoints().get(0).getExpression());
        assertEquals("READY", anotherStudent.getStatus());
        verify(generator, times(1)).generate(any(), any());
        verify(insights, times(1)).insert(any());
        verify(messages, times(2)).insert(any());
    }

    @Test
    void listeningUsesTheSamePointCacheWithoutFalselyCompletingReadingOrSendingUnreadMessage() {
        Text text = new Text();
        text.setId(8L);
        text.setContentType("TEXT");
        text.setCet4Content("Practice makes progress.");
        when(texts.selectById(8L)).thenReturn(text);
        when(levels.resolve(7L)).thenReturn(new ArticleInsightLevelService.Decision("CET4", "当前水平"));
        KnowledgePoint point = new KnowledgePoint();
        point.setType("VOCABULARY");
        point.setExpression("progress");
        when(generator.generate(any(), any())).thenReturn(List.of(point));
        AtomicReference<ArticleLearningInsight> cached = new AtomicReference<>();
        when(insights.selectOne(any())).thenAnswer(invocation -> cached.get());
        doAnswer(invocation -> {
            cached.set(invocation.getArgument(0));
            return 1;
        }).when(insights).insert(any());

        assertEquals("PENDING", service.getForListening(7L, 8L, "cet4").getStatus());
        ArticleLearningInsightDto ready = service.getForListening(7L, 8L, "cet4");
        assertEquals("READY", ready.getStatus());
        assertEquals("progress", ready.getPoints().get(0).getExpression());
        verify(readings, never()).selectOne(any());
        verify(messages, never()).insert(any());
        verify(generator, times(1)).generate(any(), any());
    }

    @Test
    void cannotMarkAnotherStudentsMessageRead() {
        ArticleInsightMessage message = new ArticleInsightMessage();
        message.setId(5L);
        message.setUserId(99L);
        when(messages.selectById(5L)).thenReturn(message);

        assertThrows(BusinessException.class, () -> service.markRead(7L, 5L));
        verify(messages, never()).update(any(), any());
    }
}
