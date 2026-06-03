package com.english.service.impl;

import com.english.entity.LearnerProfileSnapshot;
import com.english.entity.TagRecalibrationLog;
import com.english.mapper.LearnerProfileSnapshotMapper;
import com.english.mapper.RecommendationEventMapper;
import com.english.mapper.TagRecalibrationLogMapper;
import com.english.service.TagRecalibrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TagRecalibrationServiceImpl implements TagRecalibrationService {

    private static final Logger log = LoggerFactory.getLogger(TagRecalibrationServiceImpl.class);

    @Autowired
    private LearnerProfileSnapshotMapper snapshotMapper;

    @Autowired
    private RecommendationEventMapper eventMapper;

    @Autowired
    private TagRecalibrationLogMapper recalibrationLogMapper;

    @Override
    public List<Map<String, Object>> getTagEffectiveness(int lastNDays) {
        List<Map<String, Object>> ctrByTag = eventMapper.selectCtrByPersonaTag(lastNDays);

        return ctrByTag.stream().map(row -> {
            Map<String, Object> result = new HashMap<>();
            result.put("personaTags", row.get("persona_tags"));
            long impressions = ((Number) row.getOrDefault("impressions", 0)).longValue();
            long clicks = ((Number) row.getOrDefault("clicks", 0)).longValue();
            result.put("impressions", impressions);
            result.put("clicks", clicks);
            result.put("ctr", impressions > 0 ? (double) clicks / impressions : 0.0);
            return result;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getRecalibrationProposals() {
        List<Map<String, Object>> proposals = new ArrayList<>();

        LocalDate since = LocalDate.now().minusDays(14);
        List<LearnerProfileSnapshot> recentSnapshots = snapshotMapper.selectByUserSince(null, since);

        if (recentSnapshots == null || recentSnapshots.isEmpty()) {
            return proposals;
        }

        Map<String, Integer> tagHitCounts = new HashMap<>();
        int totalSnapshots = recentSnapshots.size();

        for (LearnerProfileSnapshot snapshot : recentSnapshots) {
            if (snapshot.getMatchedTagCodes() != null && !snapshot.getMatchedTagCodes().isEmpty()) {
                for (String tag : snapshot.getMatchedTagCodes().split(",")) {
                    tagHitCounts.merge(tag.trim(), 1, Integer::sum);
                }
            }
        }

        for (Map.Entry<String, Integer> entry : tagHitCounts.entrySet()) {
            double hitRate = (double) entry.getValue() / totalSnapshots;
            if (hitRate > 0.8) {
                Map<String, Object> proposal = new HashMap<>();
                proposal.put("tagCode", entry.getKey());
                proposal.put("hitRate", hitRate);
                proposal.put("proposal", "tighten");
                proposal.put("reason", "命中率过高(" + String.format("%.1f%%", hitRate * 100) + "), 建议收紧阈值");
                proposals.add(proposal);
            } else if (hitRate < 0.05) {
                Map<String, Object> proposal = new HashMap<>();
                proposal.put("tagCode", entry.getKey());
                proposal.put("hitRate", hitRate);
                proposal.put("proposal", "loosen");
                proposal.put("reason", "命中率过低(" + String.format("%.1f%%", hitRate * 100) + "), 建议放宽阈值");
                proposals.add(proposal);
            }
        }

        return proposals;
    }

    @Override
    public TagRecalibrationLog recalibrate(String tagCode, String newThreshold, String reason, String operator) {
        TagRecalibrationLog logEntry = new TagRecalibrationLog();
        logEntry.setTagCode(tagCode);
        logEntry.setOldThreshold("(current)");
        logEntry.setNewThreshold(newThreshold);
        logEntry.setReason(reason);
        logEntry.setOperator(operator);
        logEntry.setAffectedUserCount(0);
        logEntry.setCreatedTime(LocalDateTime.now());

        recalibrationLogMapper.insert(logEntry);
        log.info("[Recalibration] 标签阈值校准: tagCode={}, newThreshold={}, operator={}", tagCode, newThreshold, operator);

        return logEntry;
    }
}
