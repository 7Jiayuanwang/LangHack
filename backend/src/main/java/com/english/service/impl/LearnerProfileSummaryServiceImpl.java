package com.english.service.impl;

import com.english.dto.learner.LearnerProfileDto;
import com.english.dto.learner.LearnerTagDto;
import com.english.entity.LearnerProfileSummary;
import com.english.mapper.LearnerProfileSummaryMapper;
import com.english.service.LearnerProfileService;
import com.english.service.LearnerProfileSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LearnerProfileSummaryServiceImpl implements LearnerProfileSummaryService {

    private static final Logger log = LoggerFactory.getLogger(LearnerProfileSummaryServiceImpl.class);

    @Value("${learner-profile.llm-summary.enabled:false}")
    private boolean llmSummaryEnabled;

    @Autowired
    private LearnerProfileService learnerProfileService;

    @Autowired
    private LearnerProfileSummaryMapper summaryMapper;

    @Autowired(required = false)
    private AiProviderRuntimeService aiProviderRuntimeService;

    @Override
    public LearnerProfileSummary generateSummary(Long userId, String summaryType) {
        if (!llmSummaryEnabled || aiProviderRuntimeService == null) {
            log.debug("[Summary] LLM摘要功能未启用或AI服务不可用");
            return null;
        }

        try {
            LearnerProfileDto profile = learnerProfileService.getUserProfile(userId);
            if (profile == null || "empty".equals(profile.getProfileStatus())) {
                return null;
            }

            String prompt = buildSummaryPrompt(profile, summaryType);
            long startMs = System.currentTimeMillis();

            Optional<String> result = aiProviderRuntimeService.completeIfConfigured(
                    "article_generation", "learner_profile_summary", prompt);

            if (result.isEmpty()) {
                log.warn("[Summary] AI服务返回空结果: userId={}", userId);
                return null;
            }

            long elapsedMs = System.currentTimeMillis() - startMs;

            LearnerProfileSummary summary = new LearnerProfileSummary();
            summary.setUserId(userId);
            summary.setSummaryType(summaryType);
            summary.setLanguage("zh");
            summary.setSummaryText(result.get());
            summary.setGenerationTimeMs((int) elapsedMs);
            if (profile.getTags() != null) {
                summary.setInputTagsJson(profile.getTags().stream()
                        .map(LearnerTagDto::getCode)
                        .collect(Collectors.joining(",")));
            }
            summary.setCreatedTime(LocalDateTime.now());

            summaryMapper.insert(summary);
            return summary;
        } catch (Exception e) {
            log.error("[Summary] 生成画像摘要失败: userId={}", userId, e);
            return null;
        }
    }

    @Override
    public LearnerProfileSummary getLatestSummary(Long userId, String summaryType) {
        return summaryMapper.selectLatestByUserAndType(userId, summaryType);
    }

    private String buildSummaryPrompt(LearnerProfileDto profile, String summaryType) {
        StringBuilder sb = new StringBuilder();
        sb.append("请基于以下学习者画像数据，生成简洁的中文学习摘要（3段，每段2-3句话）：\n\n");

        if (profile.getIdentity() != null) {
            sb.append("【身份】").append(profile.getIdentity().getRealName() != null ?
                    profile.getIdentity().getRealName() : "匿名用户").append("\n");
        }

        if (profile.getAbility() != null) {
            sb.append("【词汇能力】总词汇: ").append(profile.getAbility().getTotalWords())
                    .append(", 已掌握: ").append(profile.getAbility().getMasteredWords())
                    .append(", 弱势率: ").append(String.format("%.0f%%", profile.getAbility().getWeakRatio() * 100))
                    .append(", 阅读篇数: ").append(profile.getAbility().getTextsRead()).append("\n");
        }

        if (profile.getHabit() != null) {
            sb.append("【学习习惯】7天活跃: ").append(profile.getHabit().getActiveDays7d()).append("天")
                    .append(", 连续学习: ").append(profile.getHabit().getConsecutiveDays()).append("天")
                    .append(", 14天学习时长: ").append(profile.getHabit().getStudyMinutes14d()).append("分钟")
                    .append(", 逾期复习: ").append(profile.getHabit().getOverdueReviewCount()).append("个\n");
        }

        if (profile.getPreference() != null && !"unknown".equals(profile.getPreference().getPrimaryContentVersion())) {
            sb.append("【阅读偏好】主要版本: ").append(profile.getPreference().getPrimaryContentVersion());
            if (profile.getPreference().getTargetExamLevel() != null) {
                sb.append(", 备考目标: ").append(profile.getPreference().getTargetExamLevel());
            }
            sb.append("\n");
        }

        if (profile.getTags() != null && !profile.getTags().isEmpty()) {
            sb.append("【标签】").append(profile.getTags().stream()
                    .map(LearnerTagDto::getDisplayName)
                    .collect(Collectors.joining(", "))).append("\n");
        }

        if (profile.getRisk() != null) {
            sb.append("【风险等级】").append(profile.getRisk().getRiskLevel()).append("\n");
        }

        sb.append("\n请生成：\n");
        if ("PERSONAL".equals(summaryType)) {
            sb.append("1. 整体学习状态总结（鼓励性措辞）\n2. 你的优势和值得关注的地方\n3. 具体的学习建议\n");
        } else {
            sb.append("1. 该学生的整体学习状态\n2. 优势与关注点\n3. 教学建议\n");
        }

        return sb.toString();
    }
}
