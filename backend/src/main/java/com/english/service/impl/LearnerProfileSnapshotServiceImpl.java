package com.english.service.impl;

import com.english.dto.learner.LearnerProfileDto;
import com.english.dto.learner.LearnerTagDto;
import com.english.entity.LearnerProfileSnapshot;
import com.english.mapper.LearnerProfileSnapshotMapper;
import com.english.mapper.LearningRecordMapper;
import com.english.service.LearnerProfileService;
import com.english.service.LearnerProfileSnapshotService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LearnerProfileSnapshotServiceImpl implements LearnerProfileSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(LearnerProfileSnapshotServiceImpl.class);

    @Value("${learner-profile.snapshot.enabled:false}")
    private boolean snapshotEnabled;

    @Autowired
    @Lazy
    private LearnerProfileService learnerProfileService;

    @Autowired
    private LearnerProfileSnapshotMapper snapshotMapper;

    @Autowired
    private LearningRecordMapper learningRecordMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LearnerProfileSnapshot takeSnapshot(Long userId) {
        if (!snapshotEnabled) return null;

        LocalDate today = LocalDate.now();
        LearnerProfileSnapshot existing = snapshotMapper.selectByUserAndDate(userId, today);
        if (existing != null) {
            return existing;
        }

        try {
            LearnerProfileDto profile = learnerProfileService.getUserProfile(userId);
            if (profile == null) return null;

            LearnerProfileSnapshot snapshot = new LearnerProfileSnapshot();
            snapshot.setUserId(userId);
            snapshot.setSnapshotDate(today);
            snapshot.setProfileStatus(profile.getProfileStatus());

            if (profile.getRisk() != null) {
                snapshot.setRiskLevel(profile.getRisk().getRiskLevel());
            }
            if (profile.getHabit() != null) {
                snapshot.setActiveDays7d(profile.getHabit().getActiveDays7d());
                snapshot.setActiveDays14d(profile.getHabit().getActiveDays14d());
                snapshot.setStudyMinutes14d((int) profile.getHabit().getStudyMinutes14d());
                snapshot.setInactiveDays(14 - profile.getHabit().getActiveDays14d());
                snapshot.setOverdueReviewCount(profile.getHabit().getOverdueReviewCount());
                snapshot.setOverdueReviewRatio(profile.getHabit().getOverdueReviewRatio());
            }
            if (profile.getAbility() != null) {
                snapshot.setNewCount((int) profile.getAbility().getNewWords());
                snapshot.setLearningCount((int) profile.getAbility().getLearningWords());
                snapshot.setMasteredCount((int) profile.getAbility().getMasteredWords());
                snapshot.setWeakRatio(profile.getAbility().getWeakRatio());
            }
            if (profile.getPreference() != null) {
                snapshot.setPrimaryContentVersion(profile.getPreference().getPrimaryContentVersion());
                if (profile.getPreference().getVersionDistribution() != null) {
                    snapshot.setVersionDistributionJson(
                            objectMapper.writeValueAsString(profile.getPreference().getVersionDistribution()));
                }
            }
            if (profile.getTags() != null && !profile.getTags().isEmpty()) {
                snapshot.setMatchedTagCodes(profile.getTags().stream()
                        .map(LearnerTagDto::getCode)
                        .collect(Collectors.joining(",")));
            }

            snapshot.setCreatedTime(LocalDateTime.now());
            snapshotMapper.insert(snapshot);
            return snapshot;
        } catch (JsonProcessingException e) {
            log.error("[Snapshot] JSON序列化失败: userId={}", userId, e);
            return null;
        } catch (Exception e) {
            log.error("[Snapshot] 创建快照失败: userId={}", userId, e);
            return null;
        }
    }

    @Override
    public void takeDailySnapshotsForAllActiveUsers() {
        if (!snapshotEnabled) return;

        try {
            List<Long> activeUserIds = learningRecordMapper.selectActiveUserIds(30);
            log.info("[Snapshot] 开始每日快照, 活跃用户数: {}", activeUserIds.size());

            int success = 0;
            int fail = 0;
            for (Long userId : activeUserIds) {
                try {
                    takeSnapshot(userId);
                    success++;
                } catch (Exception e) {
                    fail++;
                    log.warn("[Snapshot] 用户快照失败: userId={}", userId, e);
                }
            }
            log.info("[Snapshot] 每日快照完成: success={}, fail={}", success, fail);
        } catch (Exception e) {
            log.error("[Snapshot] 每日快照任务异常", e);
        }
    }

    @Override
    public List<LearnerProfileSnapshot> getSnapshotHistory(Long userId, int lastNDays) {
        LocalDate since = LocalDate.now().minusDays(lastNDays);
        return snapshotMapper.selectByUserSince(userId, since);
    }

    @Override
    public LearnerProfileSnapshot getLatestSnapshot(Long userId) {
        List<LearnerProfileSnapshot> recent = snapshotMapper.selectByUserSince(userId, LocalDate.now().minusDays(1));
        return recent.isEmpty() ? null : recent.get(0);
    }
}
