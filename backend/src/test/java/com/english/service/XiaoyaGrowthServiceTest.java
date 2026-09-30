package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.learner.XiaoyaExternalLearningRequest;
import com.english.dto.learner.XiaoyaGrowthDto;
import com.english.entity.XiaoyaExternalLearning;
import com.english.exception.BusinessException;
import com.english.mapper.XiaoyaGrowthMapper;
import com.english.service.impl.XiaoyaGrowthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XiaoyaGrowthServiceTest {
    @Mock private XiaoyaGrowthMapper mapper;
    @InjectMocks private XiaoyaGrowthService service;

    @Test
    void derivesGrowthFromMeaningfulActivityWithoutLosingProgressAfterIdleDays() {
        when(mapper.countMeaningfulDaysSince(eq(7L), any(LocalDateTime.class))).thenReturn(9, 4);
        when(mapper.countCompletedArticles(7L)).thenReturn(4L);
        when(mapper.countPracticedArticles(7L)).thenReturn(3L);
        when(mapper.countExternalDays(7L)).thenReturn(2L);

        XiaoyaGrowthDto growth = service.forUser(7L);

        assertEquals(27, growth.getGrowthPoints());
        assertEquals("SPROUT", growth.getStage());
        assertEquals(4, growth.getMeaningfulDays7d());
        assertEquals(30, growth.getNextStageAt());
        assertEquals(2, growth.getExternalDays());
    }

    @Test
    void onlyAcceptsAnEnglishTakeawayAndNeverRewardsAVisitAlone() {
        XiaoyaExternalLearningRequest request = request("ARTICLE", "", "");
        assertThrows(BusinessException.class, () -> service.addExternalLearning(7L, request));
        request.setTakeawayEn("a b c d e");
        assertThrows(BusinessException.class, () -> service.addExternalLearning(7L, request));
        verify(mapper, never()).insert(any(XiaoyaExternalLearning.class));
    }

    @Test
    void storesPrivateEvidenceWithOneRewardedExternalDay() {
        XiaoyaExternalLearningRequest request = request("VIDEO", "Nature documentary",
                "I learned how whales communicate across the ocean.");
        service.addExternalLearning(7L, request);

        ArgumentCaptor<XiaoyaExternalLearning> captured = ArgumentCaptor.forClass(XiaoyaExternalLearning.class);
        verify(mapper).insert(captured.capture());
        assertEquals(7L, captured.getValue().getUserId());
        assertEquals("VIDEO", captured.getValue().getSourceType());
        assertEquals(64, captured.getValue().getTakeawayHash().length());
        verify(mapper).lockUserForExternalLearning(7L);
    }

    @Test
    void rejectsRepeatedOrExcessiveDailyNotes() {
        XiaoyaExternalLearningRequest request = request("ARTICLE", "Science",
                "I learned that plants react to sunlight every day.");
        when(mapper.countExternalNotesOnDay(eq(7L), any())).thenReturn(3);
        BusinessException limit = assertThrows(BusinessException.class,
                () -> service.addExternalLearning(7L, request));
        assertTrue(limit.getMessage().contains("3 条"));
        verify(mapper, never()).insert(any(XiaoyaExternalLearning.class));
    }

    @Test
    void rejectsTheSameTakeawayTwiceEvenIfSubmittedConcurrently() {
        XiaoyaExternalLearningRequest request = request("ARTICLE", "Science",
                "I learned that plants react to sunlight every day.");
        when(mapper.insert(any(XiaoyaExternalLearning.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.addExternalLearning(7L, request));

        assertTrue(duplicate.getMessage().contains("相同"));
    }

    @Test
    void cannotDeleteAnotherLearnersNote() {
        assertThrows(BusinessException.class, () -> service.deleteExternalLearning(7L, 99L));
        verify(mapper, never()).deleteById(99L);
    }

    @Test
    void onlyReturnsSafeNoteFieldsToTheStudent() {
        XiaoyaExternalLearning note = new XiaoyaExternalLearning();
        note.setId(11L);
        note.setUserId(7L);
        note.setTakeawayHash("private-hash");
        note.setTakeawayEn("I read about new ways to learn languages.");
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(note));

        XiaoyaGrowthDto growth = service.forUser(7L);

        assertEquals(1, growth.getRecentExternalLearning().size());
        assertEquals("I read about new ways to learn languages.",
                growth.getRecentExternalLearning().get(0).getTakeawayEn());
    }

    @Test
    void globalSummaryDoesNotReadPrivateTakeawayText() {
        XiaoyaGrowthDto summary = service.summaryForUser(7L);

        assertTrue(summary.getRecentExternalLearning().isEmpty());
        verify(mapper, never()).selectList(any(QueryWrapper.class));
    }

    private XiaoyaExternalLearningRequest request(String type, String title, String takeaway) {
        XiaoyaExternalLearningRequest request = new XiaoyaExternalLearningRequest();
        request.setSourceType(type);
        request.setSourceTitle(title);
        request.setTakeawayEn(takeaway);
        return request;
    }
}
