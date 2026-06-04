package com.english.controller;

import com.english.dto.Result;
import com.english.dto.learner.ClassCohortAnalysisDto;
import com.english.dto.learner.LearnerProfileClassSummaryDto;
import com.english.dto.learner.LearnerProfileDto;
import com.english.dto.learner.PersonaTrendPointDto;
import com.english.entity.LearnerCompositeTag;
import com.english.entity.LearnerProfileSnapshot;
import com.english.entity.LearnerProfileSummary;
import com.english.entity.TagRecalibrationLog;
import com.english.mapper.LearnerCompositeTagMapper;
import com.english.mapper.LearnerProfileSnapshotMapper;
import com.english.service.ClassCohortAnalysisService;
import com.english.service.LearnerProfileService;
import com.english.service.LearnerProfileSnapshotService;
import com.english.service.LearnerProfileSummaryService;
import com.english.service.RecommendationTrackingService;
import com.english.service.TagRecalibrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/admin/learner-profile")
@PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
public class LearnerProfileController {

    @Autowired
    private LearnerProfileService learnerProfileService;

    @Autowired(required = false)
    private LearnerProfileSnapshotService snapshotService;

    @Autowired(required = false)
    private RecommendationTrackingService trackingService;

    @Autowired(required = false)
    private TagRecalibrationService recalibrationService;

    @Autowired(required = false)
    private LearnerProfileSummaryService summaryService;

    @Autowired(required = false)
    private ClassCohortAnalysisService cohortAnalysisService;

    @Autowired
    private LearnerCompositeTagMapper compositeTagMapper;

    @Autowired
    private LearnerProfileSnapshotMapper snapshotMapper;

    @Autowired(required = false)
    private CacheManager cacheManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/{userId}")
    public Result<LearnerProfileDto> getUserProfile(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            return Result.error("无效的用户ID");
        }
        LearnerProfileDto profile = learnerProfileService.getUserProfile(userId);
        return Result.success(profile);
    }

    @GetMapping("/class/{classId}")
    public Result<List<LearnerProfileClassSummaryDto>> getClassProfileSummary(@PathVariable Long classId) {
        if (classId == null || classId <= 0) {
            return Result.error("无效的班级ID");
        }
        List<LearnerProfileClassSummaryDto> summary = learnerProfileService.getClassProfileSummary(classId);
        return Result.success(summary);
    }

    // ── V2: Snapshot History ──

    @GetMapping("/{userId}/snapshots")
    public Result<List<LearnerProfileSnapshot>> getSnapshotHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "30") int lastNDays) {
        if (snapshotService == null) {
            return Result.error("快照功能未启用");
        }
        return Result.success(snapshotService.getSnapshotHistory(userId, lastNDays));
    }

    // ── V2: Tag Effectiveness & Recalibration ──

    @GetMapping("/tags/effectiveness")
    public Result<List<Map<String, Object>>> getTagEffectiveness(
            @RequestParam(defaultValue = "30") int lastNDays) {
        if (recalibrationService == null) {
            return Result.error("校准功能未启用");
        }
        return Result.success(recalibrationService.getTagEffectiveness(lastNDays));
    }

    @GetMapping("/tags/proposals")
    public Result<List<Map<String, Object>>> getRecalibrationProposals() {
        if (recalibrationService == null) {
            return Result.error("校准功能未启用");
        }
        return Result.success(recalibrationService.getRecalibrationProposals());
    }

    @PostMapping("/tags/recalibrate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<TagRecalibrationLog> recalibrate(@RequestBody Map<String, String> body,
                                                    Authentication auth) {
        if (recalibrationService == null) {
            return Result.error("校准功能未启用");
        }
        String tagCode = body.get("tagCode");
        String newThreshold = body.get("newThreshold");
        String reason = body.get("reason");
        if (tagCode == null || newThreshold == null) {
            return Result.error("缺少必要参数");
        }
        TagRecalibrationLog log = recalibrationService.recalibrate(
                tagCode, newThreshold, reason, auth.getName());
        return Result.success(log);
    }

    // ── V2: Analytics ──

    @GetMapping("/analytics/overview")
    public Result<Map<String, Object>> getAnalyticsOverview() {
        Map<String, Object> overview = new HashMap<>();
        if (trackingService != null) {
            overview.put("ctrBySource", trackingService.getClickThroughRateBySource(30));
        }
        if (recalibrationService != null) {
            overview.put("tagEffectiveness", recalibrationService.getTagEffectiveness(30));
            overview.put("recalibrationProposals", recalibrationService.getRecalibrationProposals());
        }
        return Result.success(overview);
    }

    @GetMapping("/analytics/recommendation-effectiveness")
    public Result<Map<String, Object>> getRecommendationEffectiveness(
            @RequestParam(defaultValue = "30") int lastNDays) {
        Map<String, Object> result = new HashMap<>();
        if (trackingService != null) {
            result.put("ctrBySource", trackingService.getClickThroughRateBySource(lastNDays));
            result.put("personaMetrics", trackingService.getPersonaEffectivenessMetrics(lastNDays));
        }
        return Result.success(result);
    }

    @GetMapping("/analytics/trends")
    public Result<Map<String, Object>> getAnalyticsTrends(
            @RequestParam(defaultValue = "30") int lastNDays) {
        LocalDate since = LocalDate.now().minusDays(lastNDays);

        List<Map<String, Object>> dailyRows = snapshotMapper.selectDailyAggregation(since);
        List<String> allTagCodes = snapshotMapper.selectTagCodesSince(since);

        Map<String, Integer> tagHitCounts = new HashMap<>();
        int totalSnapshots = 0;
        for (String codes : allTagCodes) {
            totalSnapshots++;
            for (String code : codes.split(",")) {
                String trimmed = code.trim();
                if (!trimmed.isEmpty()) {
                    tagHitCounts.merge(trimmed, 1, Integer::sum);
                }
            }
        }

        List<PersonaTrendPointDto> points = new ArrayList<>();
        for (Map<String, Object> row : dailyRows) {
            PersonaTrendPointDto point = new PersonaTrendPointDto();
            point.setDate(toLocalDate(row.get("snapshotDate")));
            point.setTotalUsers(((Number) row.get("totalUsers")).intValue());
            point.setRiskHigh(((Number) row.get("riskHigh")).intValue());
            point.setRiskMedium(((Number) row.get("riskMedium")).intValue());
            point.setRiskLow(((Number) row.get("riskLow")).intValue());
            point.setAvgActiveDays7d(((Number) row.get("avgActiveDays7d")).doubleValue());
            point.setAvgStudyMinutes14d(((Number) row.get("avgStudyMinutes14d")).intValue());
            point.setAvgOverdueReviewRatio(((Number) row.get("avgOverdueReviewRatio")).doubleValue());
            points.add(point);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dailyPoints", points);
        result.put("tagHitCounts", tagHitCounts);
        result.put("totalSnapshotsWithTags", totalSnapshots);
        return Result.success(result);
    }

    // ── V3: LLM Summary ──

    @PostMapping("/{userId}/generate-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<LearnerProfileSummary> generateSummary(@PathVariable Long userId) {
        if (summaryService == null) {
            return Result.error("LLM摘要功能未启用");
        }
        LearnerProfileSummary summary = summaryService.generateSummary(userId, "TEACHER_FACING");
        if (summary == null) {
            return Result.error("生成摘要失败");
        }
        return Result.success(summary);
    }

    // ── V3: Cohort Analysis ──

    @GetMapping("/class/{classId}/cohort-analysis")
    public Result<ClassCohortAnalysisDto> getCohortAnalysis(@PathVariable Long classId) {
        if (cohortAnalysisService == null) {
            return Result.error("队列分析功能未启用");
        }
        if (classId == null || classId <= 0) {
            return Result.error("无效的班级ID");
        }
        return Result.success(cohortAnalysisService.analyzeCohort(classId));
    }

    @GetMapping("/class-comparison")
    public Result<List<ClassCohortAnalysisDto>> compareClasses(@RequestParam List<Long> classIds) {
        if (cohortAnalysisService == null) {
            return Result.error("队列分析功能未启用");
        }
        if (classIds == null || classIds.isEmpty() || classIds.size() > 5) {
            return Result.error("请提供1-5个班级ID");
        }
        return Result.success(cohortAnalysisService.compareClasses(classIds));
    }

    // ── Tag Definition CRUD ──

    private static final Map<String, String> METRIC_DEFINITIONS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("activeDays7d",       "7天活跃天数");
        m.put("activeDays14d",      "14天活跃天数");
        m.put("studyMinutes14d",    "14天学习时长");
        m.put("consecutiveDays",    "连续学习天数");
        m.put("overdueReviewRatio", "逾期复习率");
        m.put("overdueReviewCount", "逾期复习数");
        m.put("dueReviewCount",     "待复习数");
        m.put("totalWords",         "总词汇量");
        m.put("weakRatio",          "词汇弱势率");
        m.put("textsRead",          "阅读篇数");
        m.put("versionDistSize",    "版本分布数");
        m.put("trendRatio",         "活跃趋势比");
        m.put("oldActiveDays14d",   "前14天活跃");
        m.put("snap0OverdueRatio",  "最近快照逾期率");
        m.put("snap1OverdueRatio",  "次近快照逾期率");
        m.put("snap2OverdueRatio",  "第3快照逾期率");
        METRIC_DEFINITIONS = Collections.unmodifiableMap(m);
    }

    @GetMapping("/tag-definitions")
    public Result<List<LearnerCompositeTag>> listAllTags() {
        return Result.success(compositeTagMapper.selectAllOrdered());
    }

    @PostMapping("/tag-definitions")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<LearnerCompositeTag> createTag(@RequestBody LearnerCompositeTag tag) {
        if (tag.getCode() == null || tag.getCode().isBlank()) {
            return Result.error("标签编码不能为空");
        }
        if (tag.getDisplayName() == null || tag.getDisplayName().isBlank()) {
            return Result.error("标签名称不能为空");
        }
        if (compositeTagMapper.countByCode(tag.getCode()) > 0) {
            return Result.error("标签编码已存在: " + tag.getCode());
        }
        if (!isValidRuleJson(tag.getRuleDefinition())) {
            return Result.error("规则定义JSON格式不合法");
        }
        if (tag.getEnabled() == null) tag.setEnabled(true);
        if (tag.getPriority() == null) tag.setPriority(100);
        compositeTagMapper.insert(tag);
        evictProfileCaches();
        return Result.success(tag);
    }

    @PutMapping("/tag-definitions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<LearnerCompositeTag> updateTag(@PathVariable Long id, @RequestBody LearnerCompositeTag tag) {
        LearnerCompositeTag existing = compositeTagMapper.selectById(id);
        if (existing == null) {
            return Result.error("标签不存在");
        }
        if (tag.getDisplayName() == null || tag.getDisplayName().isBlank()) {
            return Result.error("标签名称不能为空");
        }
        if (tag.getCode() != null && !tag.getCode().equals(existing.getCode())) {
            if (compositeTagMapper.countByCodeExcluding(tag.getCode(), id) > 0) {
                return Result.error("标签编码已存在: " + tag.getCode());
            }
            existing.setCode(tag.getCode());
        }
        if (!isValidRuleJson(tag.getRuleDefinition())) {
            return Result.error("规则定义JSON格式不合法");
        }
        existing.setDisplayName(tag.getDisplayName());
        existing.setTagType(tag.getTagType());
        existing.setPriority(tag.getPriority());
        existing.setRuleDefinition(tag.getRuleDefinition());
        existing.setActionHint(tag.getActionHint());
        compositeTagMapper.updateById(existing);
        evictProfileCaches();
        return Result.success(existing);
    }

    @DeleteMapping("/tag-definitions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteTag(@PathVariable Long id) {
        LearnerCompositeTag existing = compositeTagMapper.selectById(id);
        if (existing == null) {
            return Result.error("标签不存在");
        }
        compositeTagMapper.deleteById(id);
        evictProfileCaches();
        return Result.success(null);
    }

    @PatchMapping("/tag-definitions/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> toggleTag(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        LearnerCompositeTag existing = compositeTagMapper.selectById(id);
        if (existing == null) {
            return Result.error("标签不存在");
        }
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return Result.error("缺少enabled参数");
        }
        existing.setEnabled(enabled);
        compositeTagMapper.updateById(existing);
        evictProfileCaches();
        return Result.success(null);
    }

    @GetMapping("/tag-definitions/metrics")
    public Result<Map<String, String>> listMetrics() {
        return Result.success(METRIC_DEFINITIONS);
    }

    private boolean isValidRuleJson(String json) {
        if (json == null || json.isBlank()) return false;
        try {
            var root = objectMapper.readTree(json);
            return root.has("conditions") && root.get("conditions").isArray();
        } catch (Exception e) {
            return false;
        }
    }

    private void evictProfileCaches() {
        if (cacheManager != null) {
            var userCache = cacheManager.getCache("learner-profile:user");
            if (userCache != null) userCache.clear();
            var classCache = cacheManager.getCache("learner-profile:class");
            if (classCache != null) classCache.clear();
        }
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate) return (LocalDate) value;
        if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate();
        return LocalDate.parse(value.toString());
    }
}
