package com.english.service;

import com.english.entity.LearnerProfileSnapshot;
import com.english.entity.TagRecalibrationLog;
import com.english.mapper.LearnerProfileSnapshotMapper;
import com.english.mapper.RecommendationEventMapper;
import com.english.mapper.TagRecalibrationLogMapper;
import com.english.service.impl.TagRecalibrationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagRecalibrationServiceTest {

    @Mock
    private LearnerProfileSnapshotMapper snapshotMapper;

    @Mock
    private RecommendationEventMapper eventMapper;

    @Mock
    private TagRecalibrationLogMapper recalibrationLogMapper;

    @InjectMocks
    private TagRecalibrationServiceImpl service;

    @Nested
    @DisplayName("getTagEffectiveness")
    class TagEffectivenessTests {

        @Test
        @DisplayName("computes CTR from event mapper data")
        void computesCtr() {
            Map<String, Object> row = new HashMap<>();
            row.put("persona_tags", "VOCAB_WEAK");
            row.put("impressions", 100L);
            row.put("clicks", 15L);
            when(eventMapper.selectCtrByPersonaTag(30)).thenReturn(List.of(row));

            List<Map<String, Object>> result = service.getTagEffectiveness(30);
            assertEquals(1, result.size());
            assertEquals("VOCAB_WEAK", result.get(0).get("personaTags"));
            assertEquals(0.15, (double) result.get(0).get("ctr"), 0.001);
        }

        @Test
        @DisplayName("zero impressions → CTR = 0.0")
        void zeroImpressions_zeroCtr() {
            Map<String, Object> row = new HashMap<>();
            row.put("persona_tags", "INACTIVE_RISK");
            row.put("impressions", 0L);
            row.put("clicks", 0L);
            when(eventMapper.selectCtrByPersonaTag(7)).thenReturn(List.of(row));

            List<Map<String, Object>> result = service.getTagEffectiveness(7);
            assertEquals(0.0, (double) result.get(0).get("ctr"));
        }

        @Test
        @DisplayName("no data → returns empty list")
        void noData_returnsEmpty() {
            when(eventMapper.selectCtrByPersonaTag(30)).thenReturn(Collections.emptyList());
            assertTrue(service.getTagEffectiveness(30).isEmpty());
        }
    }

    @Nested
    @DisplayName("getRecalibrationProposals")
    class RecalibrationProposalTests {

        private LearnerProfileSnapshot makeSnapshot(String tags) {
            LearnerProfileSnapshot s = new LearnerProfileSnapshot();
            s.setMatchedTagCodes(tags);
            s.setSnapshotDate(LocalDate.now());
            return s;
        }

        @Test
        @DisplayName("tag hit rate > 80% → tighten proposal")
        void highHitRate_tighten() {
            List<LearnerProfileSnapshot> snapshots = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                snapshots.add(makeSnapshot("VOCAB_WEAK"));
            }
            when(snapshotMapper.selectByUserSince(isNull(), any())).thenReturn(snapshots);

            List<Map<String, Object>> proposals = service.getRecalibrationProposals();
            assertEquals(1, proposals.size());
            assertEquals("VOCAB_WEAK", proposals.get(0).get("tagCode"));
            assertEquals("tighten", proposals.get(0).get("proposal"));
        }

        @Test
        @DisplayName("tag hit rate < 5% → loosen proposal")
        void lowHitRate_loosen() {
            List<LearnerProfileSnapshot> snapshots = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                snapshots.add(makeSnapshot(i < 3 ? "RARE_TAG" : "COMMON_TAG"));
            }
            when(snapshotMapper.selectByUserSince(isNull(), any())).thenReturn(snapshots);

            List<Map<String, Object>> proposals = service.getRecalibrationProposals();
            boolean hasLoosenForRare = proposals.stream()
                    .anyMatch(p -> "RARE_TAG".equals(p.get("tagCode")) && "loosen".equals(p.get("proposal")));
            assertTrue(hasLoosenForRare);
        }

        @Test
        @DisplayName("moderate hit rate → no proposal")
        void moderateHitRate_noProposal() {
            List<LearnerProfileSnapshot> snapshots = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                snapshots.add(makeSnapshot(i < 5 ? "TAG_A" : "TAG_B"));
            }
            when(snapshotMapper.selectByUserSince(isNull(), any())).thenReturn(snapshots);

            List<Map<String, Object>> proposals = service.getRecalibrationProposals();
            assertTrue(proposals.isEmpty());
        }

        @Test
        @DisplayName("no snapshots → empty proposals")
        void noSnapshots_emptyProposals() {
            when(snapshotMapper.selectByUserSince(isNull(), any())).thenReturn(Collections.emptyList());
            assertTrue(service.getRecalibrationProposals().isEmpty());
        }

        @Test
        @DisplayName("null snapshots → empty proposals")
        void nullSnapshots_emptyProposals() {
            when(snapshotMapper.selectByUserSince(isNull(), any())).thenReturn(null);
            assertTrue(service.getRecalibrationProposals().isEmpty());
        }
    }

    @Nested
    @DisplayName("recalibrate")
    class RecalibrateTests {

        @Test
        @DisplayName("creates log entry with correct fields")
        void createsLogEntry() {
            service.recalibrate("VOCAB_WEAK", "0.5", "阈值过低", "admin");

            ArgumentCaptor<TagRecalibrationLog> captor = ArgumentCaptor.forClass(TagRecalibrationLog.class);
            verify(recalibrationLogMapper).insert(captor.capture());
            TagRecalibrationLog log = captor.getValue();
            assertEquals("VOCAB_WEAK", log.getTagCode());
            assertEquals("0.5", log.getNewThreshold());
            assertEquals("阈值过低", log.getReason());
            assertEquals("admin", log.getOperator());
            assertNotNull(log.getCreatedTime());
        }

        @Test
        @DisplayName("returns the inserted log entry")
        void returnsLogEntry() {
            TagRecalibrationLog result = service.recalibrate("TAG_A", "0.3", "test", "admin");
            assertNotNull(result);
            assertEquals("TAG_A", result.getTagCode());
        }
    }
}
