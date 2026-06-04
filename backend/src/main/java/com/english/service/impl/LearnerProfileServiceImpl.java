package com.english.service.impl;

import com.english.dto.learner.*;
import com.english.entity.LearnerProfileSnapshot;
import com.english.entity.SchoolStudent;
import com.english.entity.StudentClass;
import com.english.entity.User;
import com.english.enums.*;
import com.english.mapper.SchoolStudentMapper;
import com.english.mapper.StudentClassMapper;
import com.english.mapper.TextLearningRecordMapper;
import com.english.service.LearnerProfileService;
import com.english.service.LearnerProfileSnapshotService;
import com.english.service.CompositeTagEngine;
import com.english.service.LearningRecordsService;
import com.english.service.UserService;
import com.english.service.VocabularyBooksService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LearnerProfileServiceImpl implements LearnerProfileService {

    private static final Logger logger = LoggerFactory.getLogger(LearnerProfileServiceImpl.class);

    @Value("${learner-profile.enabled:true}")
    private boolean profileEnabled;

    @Autowired
    private LearningRecordsService learningRecordsService;

    @Autowired
    private VocabularyBooksService vocabularyBooksService;

    @Autowired
    private TextLearningRecordMapper textLearningRecordMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private SchoolStudentMapper schoolStudentMapper;

    @Autowired
    private StudentClassMapper studentClassMapper;

    @Autowired(required = false)
    private LearnerProfileSnapshotService snapshotService;

    @Autowired(required = false)
    private CompositeTagEngine compositeTagEngine;

    @Override
    @Cacheable(value = "learner-profile:user", key = "#userId")
    public LearnerProfileDto getUserProfile(Long userId) {
        if (!profileEnabled) {
            LearnerProfileDto dto = new LearnerProfileDto();
            dto.setProfileStatus(ProfileStatus.EMPTY.getCode());
            return dto;
        }

        try {
            LearnerProfileDto profile = new LearnerProfileDto();
            profile.setIdentity(buildIdentity(userId));
            profile.setAbility(buildAbility(userId));
            profile.setHabit(buildHabit(userId));
            profile.setPreference(buildPreference(userId));

            List<LearnerTagDto> tags;
            if (compositeTagEngine != null) {
                Map<String, Double> metricsMap = buildMetricsMap(profile.getHabit(), profile.getAbility(), profile.getPreference(), userId);
                tags = compositeTagEngine.evaluateAll(metricsMap);
            } else {
                tags = new ArrayList<>();
            }
            tags.sort(Comparator.comparingInt(LearnerTagDto::getPriority));
            profile.setTags(tags);
            profile.setRisk(buildRisk(profile.getHabit(), tags));
            profile.setEvidence(buildEvidence(profile));

            profile.setProfileStatus(determineStatus(profile).getCode());
            return profile;
        } catch (Exception e) {
            logger.error("构建学习者画像失败: userId={}", userId, e);
            LearnerProfileDto dto = new LearnerProfileDto();
            dto.setProfileStatus(ProfileStatus.EMPTY.getCode());
            return dto;
        }
    }

    @Override
    @Cacheable(value = "learner-profile:class", key = "#classId")
    public List<LearnerProfileClassSummaryDto> getClassProfileSummary(Long classId) {
        if (!profileEnabled) {
            return Collections.emptyList();
        }

        List<StudentClass> students = studentClassMapper.selectStudentsByClassId(classId);
        if (students == null || students.isEmpty()) {
            return Collections.emptyList();
        }

        return students.stream()
                .map(sc -> buildClassSummary(sc.getStudentId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private LearnerIdentityDto buildIdentity(Long userId) {
        LearnerIdentityDto dto = new LearnerIdentityDto();
        dto.setUserId(userId);

        User user = userService.getUserById(userId);
        if (user != null) {
            dto.setUsername(user.getUsername());
            dto.setRealName(user.getRealName());
            dto.setRole(user.getRole());

            if (user.getSchoolStudentId() != null) {
                SchoolStudent student = schoolStudentMapper.selectById(user.getSchoolStudentId());
                if (student != null) {
                    dto.setClassName(student.getClassName());
                    dto.setCollegeName(student.getCollegeName());
                    dto.setGrade(student.getGrade());
                    dto.setMajorName(student.getMajorName());
                }
            }
        }
        return dto;
    }

    private LearnerAbilityDto buildAbility(Long userId) {
        LearnerAbilityDto dto = new LearnerAbilityDto();

        Map<String, Object> vocabStats = vocabularyBooksService.getWordStudyStats(userId, null);
        long total = ((Number) vocabStats.getOrDefault("totalWords", 0)).longValue();
        long newWords = ((Number) vocabStats.getOrDefault("newWords", 0)).longValue();
        long learning = ((Number) vocabStats.getOrDefault("learningWords", 0)).longValue();
        long mastered = ((Number) vocabStats.getOrDefault("masteredWords", 0)).longValue();

        dto.setTotalWords(total);
        dto.setNewWords(newWords);
        dto.setLearningWords(learning);
        dto.setMasteredWords(mastered);
        dto.setWeakRatio(total > 0 ? (double) (total - mastered) / total : 0.0);

        dto.setTextsRead(textLearningRecordMapper.countDistinctTexts(userId));
        dto.setTextsCompleted(textLearningRecordMapper.countDistinctCompletedTexts(userId));

        return dto;
    }

    private LearnerHabitDto buildHabit(Long userId) {
        LearnerHabitDto dto = new LearnerHabitDto();
        dto.setActiveDays7d(learningRecordsService.countActiveDays(userId, 7));
        dto.setActiveDays14d(learningRecordsService.countActiveDays(userId, 14));
        dto.setConsecutiveDays(learningRecordsService.getConsecutiveDays(userId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fourteenDaysAgo = now.minusDays(14);
        dto.setStudyMinutes14d(learningRecordsService.getStudyMinutesInRange(userId, fourteenDaysAgo, now));
        dto.setTodayStudyMinutes(learningRecordsService.getTodayStudyTime(userId));

        Map<String, Object> reviewStats = vocabularyBooksService.getReviewStats(userId);
        dto.setOverdueReviewCount(((Number) reviewStats.getOrDefault("overdueCount", 0)).intValue());
        dto.setDueReviewCount(((Number) reviewStats.getOrDefault("dueCount", 0)).intValue());
        dto.setOverdueReviewRatio(((Number) reviewStats.getOrDefault("overdueRatio", 0.0)).doubleValue());

        return dto;
    }

    private LearnerPreferenceDto buildPreference(Long userId) {
        LearnerPreferenceDto dto = new LearnerPreferenceDto();

        List<Map<String, Object>> versionDist = textLearningRecordMapper.selectVersionDistribution(userId);
        if (versionDist != null && !versionDist.isEmpty()) {
            long totalSeconds = versionDist.stream()
                    .mapToLong(m -> ((Number) m.getOrDefault("total_seconds", 0)).longValue())
                    .sum();

            Map<String, Double> distribution = new LinkedHashMap<>();
            for (Map<String, Object> row : versionDist) {
                String version = (String) row.get("content_version");
                long seconds = ((Number) row.getOrDefault("total_seconds", 0)).longValue();
                distribution.put(version, totalSeconds > 0 ? (double) seconds / totalSeconds : 0.0);
            }
            dto.setVersionDistribution(distribution);

            String primary = textLearningRecordMapper.selectPrimaryVersion(userId);
            dto.setPrimaryContentVersion(primary);

            dto.setTargetExamLevel(inferTargetExamLevel(distribution));
        } else {
            dto.setPrimaryContentVersion("unknown");
            dto.setVersionDistribution(Collections.emptyMap());
        }

        return dto;
    }

    private String inferTargetExamLevel(Map<String, Double> distribution) {
        double cet4 = distribution.getOrDefault("cet4", 0.0);
        double cet6 = distribution.getOrDefault("cet6", 0.0);
        double academic = distribution.getOrDefault("academic", 0.0);

        if (cet4 >= 0.5) return "CET-4";
        if (cet6 >= 0.4) return "CET-6";
        if (academic >= 0.3) return "考研";
        return null;
    }

    private LearnerRiskDto buildRisk(LearnerHabitDto habit, List<LearnerTagDto> tags) {
        LearnerRiskDto dto = new LearnerRiskDto();

        Set<String> tagCodes = tags.stream().map(LearnerTagDto::getCode).collect(Collectors.toSet());
        boolean hasInactiveRisk = tagCodes.contains("INACTIVE_RISK");
        boolean hasVocabWeak = tagCodes.contains("VOCAB_WEAK");
        boolean hasReviewOverdue = tagCodes.contains("REVIEW_OVERDUE");

        int riskCount = (hasInactiveRisk ? 1 : 0) + (hasVocabWeak ? 1 : 0) + (hasReviewOverdue ? 1 : 0);

        RiskLevel level;
        if (hasInactiveRisk || riskCount >= 2) {
            level = RiskLevel.HIGH;
        } else if (riskCount == 1) {
            level = RiskLevel.MEDIUM;
        } else {
            level = RiskLevel.LOW;
        }

        dto.setRiskLevel(level.getCode());
        dto.setInactiveDays(14 - habit.getActiveDays14d());
        dto.setChurnRisk(hasInactiveRisk);
        return dto;
    }

    private Map<String, Double> buildMetricsMap(LearnerHabitDto habit, LearnerAbilityDto ability,
                                                LearnerPreferenceDto preference, Long userId) {
        Map<String, Double> m = new HashMap<>();

        // Habit metrics
        m.put("activeDays7d", (double) habit.getActiveDays7d());
        m.put("activeDays14d", (double) habit.getActiveDays14d());
        m.put("studyMinutes14d", (double) habit.getStudyMinutes14d());
        m.put("consecutiveDays", (double) habit.getConsecutiveDays());
        m.put("overdueReviewRatio", habit.getOverdueReviewRatio());
        m.put("overdueReviewCount", (double) habit.getOverdueReviewCount());
        m.put("dueReviewCount", (double) habit.getDueReviewCount());

        // Ability metrics
        m.put("totalWords", (double) ability.getTotalWords());
        m.put("weakRatio", ability.getWeakRatio());
        m.put("textsRead", (double) ability.getTextsRead());

        // Preference metrics
        int versionDistSize = (preference.getVersionDistribution() != null) ? preference.getVersionDistribution().size() : 0;
        m.put("versionDistSize", (double) versionDistSize);

        // Snapshot-based trend metrics
        if (snapshotService != null) {
            try {
                List<LearnerProfileSnapshot> snapshots = snapshotService.getSnapshotHistory(userId, 28);
                if (snapshots != null && snapshots.size() >= 2) {
                    LearnerProfileSnapshot recent = snapshots.get(0);
                    LearnerProfileSnapshot older = findSnapshotNDaysBack(snapshots, 14);

                    if (older != null && older.getActiveDays14d() != null && recent.getActiveDays14d() != null) {
                        double oldVal = older.getActiveDays14d();
                        m.put("oldActiveDays14d", oldVal);
                        m.put("trendRatio", oldVal > 0 ? recent.getActiveDays14d() / oldVal : 0.0);
                    }
                }

                if (snapshots != null && snapshots.size() >= 3) {
                    for (int i = 0; i < Math.min(3, snapshots.size()); i++) {
                        LearnerProfileSnapshot snap = snapshots.get(i);
                        double overdueRatio = (snap.getOverdueReviewRatio() != null) ? snap.getOverdueReviewRatio() : 1.0;
                        m.put("snap" + i + "OverdueRatio", overdueRatio);
                    }
                }
            } catch (Exception e) {
                logger.warn("构建快照指标失败: userId={}", userId, e);
            }
        }

        return m;
    }

    private LearnerProfileSnapshot findSnapshotNDaysBack(List<LearnerProfileSnapshot> snapshots, int daysBack) {
        LocalDate target = LocalDate.now().minusDays(daysBack);
        return snapshots.stream()
                .min(Comparator.comparingLong(s -> Math.abs(s.getSnapshotDate().toEpochDay() - target.toEpochDay())))
                .filter(s -> Math.abs(s.getSnapshotDate().toEpochDay() - target.toEpochDay()) <= 2)
                .orElse(null);
    }

    private LearnerEvidenceDto buildEvidence(LearnerProfileDto profile) {
        LearnerEvidenceDto dto = new LearnerEvidenceDto();
        dto.setProfileGeneratedAt(LocalDateTime.now());

        int dataPoints = 0;
        if (profile.getAbility() != null && profile.getAbility().getTotalWords() > 0) dataPoints++;
        if (profile.getAbility() != null && profile.getAbility().getTextsRead() > 0) dataPoints++;
        if (profile.getHabit() != null && profile.getHabit().getActiveDays14d() > 0) dataPoints++;
        if (profile.getPreference() != null && !"unknown".equals(profile.getPreference().getPrimaryContentVersion())) dataPoints++;

        dto.setDataPointCount(dataPoints);
        dto.setDataCompleteness(dataPoints >= 3 ? "high" : dataPoints >= 1 ? "medium" : "low");
        return dto;
    }

    private ProfileStatus determineStatus(LearnerProfileDto profile) {
        if (profile.getEvidence() != null && profile.getEvidence().getDataPointCount() >= 3) {
            return ProfileStatus.READY;
        } else if (profile.getEvidence() != null && profile.getEvidence().getDataPointCount() >= 1) {
            return ProfileStatus.PARTIAL;
        }
        return ProfileStatus.EMPTY;
    }

    private LearnerProfileClassSummaryDto buildClassSummary(Long userId) {
        try {
            LearnerProfileDto profile = getUserProfile(userId);
            if (profile == null || ProfileStatus.EMPTY.getCode().equals(profile.getProfileStatus())) {
                return null;
            }

            LearnerProfileClassSummaryDto summary = new LearnerProfileClassSummaryDto();
            summary.setUserId(userId);
            if (profile.getIdentity() != null) {
                summary.setUsername(profile.getIdentity().getUsername());
                summary.setRealName(profile.getIdentity().getRealName());
            }
            if (profile.getRisk() != null) {
                summary.setRiskLevel(profile.getRisk().getRiskLevel());
            }
            if (profile.getHabit() != null) {
                summary.setActiveDays7d(profile.getHabit().getActiveDays7d());
                summary.setStudyMinutes14d(profile.getHabit().getStudyMinutes14d());
            }
            if (profile.getAbility() != null) {
                summary.setWeakRatio(profile.getAbility().getWeakRatio());
            }
            summary.setTags(profile.getTags());
            return summary;
        } catch (Exception e) {
            logger.warn("构建班级画像摘要失败: userId={}", userId, e);
            return null;
        }
    }
}
