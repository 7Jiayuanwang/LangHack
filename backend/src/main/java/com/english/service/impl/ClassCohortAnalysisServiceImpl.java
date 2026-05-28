package com.english.service.impl;

import com.english.dto.learner.*;
import com.english.entity.StudentClass;
import com.english.mapper.ClassMapper;
import com.english.mapper.StudentClassMapper;
import com.english.service.ClassCohortAnalysisService;
import com.english.service.LearnerProfileService;
import com.english.service.LearnerProfileSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClassCohortAnalysisServiceImpl implements ClassCohortAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ClassCohortAnalysisServiceImpl.class);

    @Autowired
    private LearnerProfileService learnerProfileService;

    @Autowired
    private StudentClassMapper studentClassMapper;

    @Autowired
    private ClassMapper classMapper;

    @Autowired(required = false)
    private LearnerProfileSummaryService summaryService;

    @Override
    public ClassCohortAnalysisDto analyzeCohort(Long classId) {
        ClassCohortAnalysisDto dto = new ClassCohortAnalysisDto();
        dto.setClassId(classId);

        com.english.entity.Class cls = classMapper.selectById(classId);
        dto.setClassName(cls != null ? cls.getName() : "未知班级");

        List<StudentClass> members = studentClassMapper.selectStudentsByClassId(classId);
        if (members == null || members.isEmpty()) {
            dto.setTotalStudents(0);
            dto.setRiskDistribution(Collections.emptyMap());
            dto.setTagHitDistribution(Collections.emptyMap());
            dto.setSubgroups(Collections.emptyList());
            dto.setTopRiskStudents(Collections.emptyList());
            return dto;
        }

        List<LearnerProfileDto> profiles = new ArrayList<>();
        List<LearnerProfileClassSummaryDto> summaries = new ArrayList<>();
        for (StudentClass sc : members) {
            try {
                LearnerProfileDto p = learnerProfileService.getUserProfile(sc.getStudentId());
                if (p != null && !"EMPTY".equals(p.getProfileStatus())) {
                    profiles.add(p);
                    summaries.add(toSummary(sc.getStudentId(), p));
                }
            } catch (Exception e) {
                log.warn("队列分析获取画像失败: studentId={}", sc.getStudentId(), e);
            }
        }

        dto.setTotalStudents(members.size());
        dto.setRiskDistribution(computeRiskDistribution(profiles));
        dto.setTagHitDistribution(computeTagHitDistribution(profiles));
        computeAverages(profiles, dto);

        dto.setTopRiskStudents(summaries.stream()
                .filter(s -> "HIGH".equals(s.getRiskLevel()))
                .sorted(Comparator.comparingInt(LearnerProfileClassSummaryDto::getActiveDays7d))
                .limit(10)
                .collect(Collectors.toList()));

        dto.setSubgroups(buildSubgroups(profiles, summaries));

        return dto;
    }

    @Override
    public List<ClassCohortAnalysisDto> compareClasses(List<Long> classIds) {
        return classIds.stream()
                .map(this::analyzeCohort)
                .collect(Collectors.toList());
    }

    private Map<String, Integer> computeRiskDistribution(List<LearnerProfileDto> profiles) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("LOW", 0);
        dist.put("MEDIUM", 0);
        dist.put("HIGH", 0);
        for (LearnerProfileDto p : profiles) {
            if (p.getRisk() != null && p.getRisk().getRiskLevel() != null) {
                dist.merge(p.getRisk().getRiskLevel(), 1, Integer::sum);
            }
        }
        return dist;
    }

    private Map<String, Integer> computeTagHitDistribution(List<LearnerProfileDto> profiles) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (LearnerProfileDto p : profiles) {
            if (p.getTags() != null) {
                for (LearnerTagDto tag : p.getTags()) {
                    dist.merge(tag.getCode(), 1, Integer::sum);
                }
            }
        }
        return dist;
    }

    private void computeAverages(List<LearnerProfileDto> profiles, ClassCohortAnalysisDto dto) {
        if (profiles.isEmpty()) {
            dto.setAvgActiveDays7d(0);
            dto.setAvgStudyMinutes14d(0);
            dto.setAvgWeakRatio(0);
            return;
        }
        double sumActive = 0, sumMinutes = 0, sumWeak = 0;
        int countHabit = 0, countAbility = 0;
        for (LearnerProfileDto p : profiles) {
            if (p.getHabit() != null) {
                sumActive += p.getHabit().getActiveDays7d();
                sumMinutes += p.getHabit().getStudyMinutes14d();
                countHabit++;
            }
            if (p.getAbility() != null) {
                sumWeak += p.getAbility().getWeakRatio();
                countAbility++;
            }
        }
        dto.setAvgActiveDays7d(countHabit > 0 ? sumActive / countHabit : 0);
        dto.setAvgStudyMinutes14d(countHabit > 0 ? sumMinutes / countHabit : 0);
        dto.setAvgWeakRatio(countAbility > 0 ? sumWeak / countAbility : 0);
    }

    private List<CohortSubgroupDto> buildSubgroups(List<LearnerProfileDto> profiles,
                                                    List<LearnerProfileClassSummaryDto> summaries) {
        List<CohortSubgroupDto> subgroups = new ArrayList<>();

        List<LearnerProfileClassSummaryDto> highRisk = summaries.stream()
                .filter(s -> "HIGH".equals(s.getRiskLevel()))
                .collect(Collectors.toList());
        if (!highRisk.isEmpty()) {
            CohortSubgroupDto g = new CohortSubgroupDto();
            g.setSubgroupName("高风险学生");
            g.setStudentCount(highRisk.size());
            g.setStudentIds(highRisk.stream().map(LearnerProfileClassSummaryDto::getUserId).collect(Collectors.toList()));
            g.setAvgActiveDays7d(highRisk.stream().mapToInt(LearnerProfileClassSummaryDto::getActiveDays7d).average().orElse(0));
            g.setAvgStudyMinutes14d(highRisk.stream().mapToLong(LearnerProfileClassSummaryDto::getStudyMinutes14d).average().orElse(0));
            g.setAvgWeakRatio(highRisk.stream().mapToDouble(LearnerProfileClassSummaryDto::getWeakRatio).average().orElse(0));
            g.setSuggestedAction("建议教师主动联系，了解学习障碍并制定个性化帮扶计划");
            subgroups.add(g);
        }

        List<LearnerProfileClassSummaryDto> vocabWeak = summaries.stream()
                .filter(s -> s.getTags() != null && s.getTags().stream().anyMatch(t -> "VOCAB_WEAK".equals(t.getCode())))
                .collect(Collectors.toList());
        if (!vocabWeak.isEmpty()) {
            CohortSubgroupDto g = new CohortSubgroupDto();
            g.setSubgroupName("词汇弱势群组");
            g.setStudentCount(vocabWeak.size());
            g.setStudentIds(vocabWeak.stream().map(LearnerProfileClassSummaryDto::getUserId).collect(Collectors.toList()));
            g.setAvgActiveDays7d(vocabWeak.stream().mapToInt(LearnerProfileClassSummaryDto::getActiveDays7d).average().orElse(0));
            g.setAvgWeakRatio(vocabWeak.stream().mapToDouble(LearnerProfileClassSummaryDto::getWeakRatio).average().orElse(0));
            g.setSuggestedAction("建议布置针对性词汇任务，配合间隔复习策略");
            subgroups.add(g);
        }

        List<LearnerProfileClassSummaryDto> consistent = summaries.stream()
                .filter(s -> s.getTags() != null && s.getTags().stream().anyMatch(t -> "CONSISTENT_LEARNER".equals(t.getCode())))
                .collect(Collectors.toList());
        if (!consistent.isEmpty()) {
            CohortSubgroupDto g = new CohortSubgroupDto();
            g.setSubgroupName("学习稳定群组");
            g.setStudentCount(consistent.size());
            g.setStudentIds(consistent.stream().map(LearnerProfileClassSummaryDto::getUserId).collect(Collectors.toList()));
            g.setAvgActiveDays7d(consistent.stream().mapToInt(LearnerProfileClassSummaryDto::getActiveDays7d).average().orElse(0));
            g.setAvgStudyMinutes14d(consistent.stream().mapToLong(LearnerProfileClassSummaryDto::getStudyMinutes14d).average().orElse(0));
            g.setSuggestedAction("学习习惯良好，可挑战更高级内容或担任学习互助角色");
            subgroups.add(g);
        }

        return subgroups;
    }

    private LearnerProfileClassSummaryDto toSummary(Long userId, LearnerProfileDto profile) {
        LearnerProfileClassSummaryDto s = new LearnerProfileClassSummaryDto();
        s.setUserId(userId);
        if (profile.getIdentity() != null) {
            s.setUsername(profile.getIdentity().getUsername());
            s.setRealName(profile.getIdentity().getRealName());
        }
        if (profile.getRisk() != null) {
            s.setRiskLevel(profile.getRisk().getRiskLevel());
        }
        if (profile.getHabit() != null) {
            s.setActiveDays7d(profile.getHabit().getActiveDays7d());
            s.setStudyMinutes14d(profile.getHabit().getStudyMinutes14d());
        }
        if (profile.getAbility() != null) {
            s.setWeakRatio(profile.getAbility().getWeakRatio());
        }
        s.setTags(profile.getTags());
        return s;
    }
}
