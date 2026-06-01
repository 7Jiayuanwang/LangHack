package com.english.service;

import com.english.dto.learner.LearnerProfileDto;
import com.english.dto.learner.LearnerHabitDto;
import com.english.dto.learner.LearnerAbilityDto;
import com.english.dto.learner.LearnerPreferenceDto;
import com.english.dto.learner.LearnerTagDto;
import com.english.entity.LearnerProfileSnapshot;
import com.english.mapper.LearnerProfileSnapshotMapper;
import com.english.mapper.LearningRecordMapper;
import com.english.service.impl.LearnerProfileSnapshotServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearnerProfileSnapshotServiceTest {

    @Mock
    private LearnerProfileSnapshotMapper snapshotMapper;

    @Mock
    private LearnerProfileService learnerProfileService;

    @Mock
    private LearningRecordMapper learningRecordMapper;

    @InjectMocks
    private LearnerProfileSnapshotServiceImpl service;

    private void enableSnapshot() {
        ReflectionTestUtils.setField(service, "snapshotEnabled", true);
    }

    private void disableSnapshot() {
        ReflectionTestUtils.setField(service, "snapshotEnabled", false);
    }

    private LearnerProfileDto buildProfile() {
        LearnerProfileDto dto = new LearnerProfileDto();
        dto.setProfileStatus("ready");

        LearnerHabitDto habit = new LearnerHabitDto();
        habit.setActiveDays7d(5);
        habit.setActiveDays14d(10);
        habit.setStudyMinutes14d(120);
        habit.setOverdueReviewCount(3);
        habit.setOverdueReviewRatio(0.15);
        dto.setHabit(habit);

        LearnerAbilityDto ability = new LearnerAbilityDto();
        ability.setNewWords(10);
        ability.setLearningWords(30);
        ability.setMasteredWords(60);
        ability.setWeakRatio(0.4);
        dto.setAbility(ability);

        LearnerPreferenceDto pref = new LearnerPreferenceDto();
        pref.setPrimaryContentVersion("cet4");
        pref.setVersionDistribution(Map.of("cet4", 0.6, "cet6", 0.4));
        dto.setPreference(pref);

        dto.setTags(List.of(
                new LearnerTagDto("VOCAB_WEAK", "词汇弱势", "risk", 20, "加强词汇"),
                new LearnerTagDto("CONSISTENT_LEARNER", "学习稳定", "positive", 40, "保持")
        ));

        return dto;
    }

    @Nested
    @DisplayName("Feature Toggle")
    class FeatureToggleTests {

        @Test
        @DisplayName("disabled → takeSnapshot returns null")
        void disabled_takeSnapshotNull() {
            disableSnapshot();
            assertNull(service.takeSnapshot(1L));
        }

        @Test
        @DisplayName("disabled → takeDailySnapshotsForAllActiveUsers does nothing")
        void disabled_dailyNoop() {
            disableSnapshot();
            service.takeDailySnapshotsForAllActiveUsers();
            verify(learningRecordMapper, never()).selectActiveUserIds(anyInt());
        }
    }

    @Nested
    @DisplayName("takeSnapshot")
    class TakeSnapshotTests {

        @Test
        @DisplayName("creates snapshot with all profile fields")
        void createsSnapshot() {
            enableSnapshot();
            when(snapshotMapper.selectByUserAndDate(eq(1L), any())).thenReturn(null);
            when(learnerProfileService.getUserProfile(1L)).thenReturn(buildProfile());

            LearnerProfileSnapshot result = service.takeSnapshot(1L);

            assertNotNull(result);
            ArgumentCaptor<LearnerProfileSnapshot> captor = ArgumentCaptor.forClass(LearnerProfileSnapshot.class);
            verify(snapshotMapper).insert(captor.capture());
            LearnerProfileSnapshot saved = captor.getValue();
            assertEquals(1L, saved.getUserId());
            assertEquals(5, saved.getActiveDays7d());
            assertEquals(10, saved.getActiveDays14d());
            assertEquals(120, saved.getStudyMinutes14d());
            assertEquals(0.4, saved.getWeakRatio(), 0.01);
            assertEquals("cet4", saved.getPrimaryContentVersion());
            assertTrue(saved.getMatchedTagCodes().contains("VOCAB_WEAK"));
            assertTrue(saved.getMatchedTagCodes().contains("CONSISTENT_LEARNER"));
        }

        @Test
        @DisplayName("existing snapshot today → returns existing, no insert")
        void existingToday_returnsExisting() {
            enableSnapshot();
            LearnerProfileSnapshot existing = new LearnerProfileSnapshot();
            existing.setId(99L);
            existing.setUserId(1L);
            when(snapshotMapper.selectByUserAndDate(eq(1L), any())).thenReturn(existing);

            LearnerProfileSnapshot result = service.takeSnapshot(1L);
            assertEquals(99L, result.getId());
            verify(snapshotMapper, never()).insert(any());
        }

        @Test
        @DisplayName("profile service returns null → returns null")
        void profileNull_returnsNull() {
            enableSnapshot();
            when(snapshotMapper.selectByUserAndDate(eq(1L), any())).thenReturn(null);
            when(learnerProfileService.getUserProfile(1L)).thenReturn(null);

            assertNull(service.takeSnapshot(1L));
            verify(snapshotMapper, never()).insert(any());
        }

        @Test
        @DisplayName("profile service throws → returns null, no propagation")
        void profileException_returnsNull() {
            enableSnapshot();
            when(snapshotMapper.selectByUserAndDate(eq(1L), any())).thenReturn(null);
            when(learnerProfileService.getUserProfile(1L)).thenThrow(new RuntimeException("fail"));

            assertNull(service.takeSnapshot(1L));
        }
    }

    @Nested
    @DisplayName("takeDailySnapshotsForAllActiveUsers")
    class DailySnapshotTests {

        @Test
        @DisplayName("processes all active users, tolerates individual failures")
        void processesAllUsers() {
            enableSnapshot();
            when(learningRecordMapper.selectActiveUserIds(30)).thenReturn(List.of(1L, 2L, 3L));
            when(snapshotMapper.selectByUserAndDate(anyLong(), any())).thenReturn(null);
            when(learnerProfileService.getUserProfile(1L)).thenReturn(buildProfile());
            when(learnerProfileService.getUserProfile(2L)).thenThrow(new RuntimeException("fail user 2"));
            when(learnerProfileService.getUserProfile(3L)).thenReturn(buildProfile());

            assertDoesNotThrow(() -> service.takeDailySnapshotsForAllActiveUsers());
            verify(snapshotMapper, times(2)).insert(any());
        }
    }

    @Nested
    @DisplayName("getSnapshotHistory")
    class GetSnapshotHistoryTests {

        @Test
        @DisplayName("delegates to mapper with correct since date")
        void delegatesToMapper() {
            List<LearnerProfileSnapshot> expected = List.of(new LearnerProfileSnapshot());
            when(snapshotMapper.selectByUserSince(eq(1L), any())).thenReturn(expected);

            List<LearnerProfileSnapshot> result = service.getSnapshotHistory(1L, 30);
            assertEquals(expected, result);
            verify(snapshotMapper).selectByUserSince(eq(1L), any(LocalDate.class));
        }
    }

    @Nested
    @DisplayName("getLatestSnapshot")
    class GetLatestSnapshotTests {

        @Test
        @DisplayName("returns first snapshot from recent history")
        void returnsFirst() {
            LearnerProfileSnapshot snap = new LearnerProfileSnapshot();
            snap.setId(42L);
            when(snapshotMapper.selectByUserSince(eq(1L), any())).thenReturn(List.of(snap));

            LearnerProfileSnapshot result = service.getLatestSnapshot(1L);
            assertNotNull(result);
            assertEquals(42L, result.getId());
        }

        @Test
        @DisplayName("no recent snapshots → returns null")
        void noRecent_returnsNull() {
            when(snapshotMapper.selectByUserSince(eq(1L), any())).thenReturn(Collections.emptyList());

            assertNull(service.getLatestSnapshot(1L));
        }
    }
}
