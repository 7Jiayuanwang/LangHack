package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.dto.learner.LearnerProfileDto;
import com.english.dto.learner.LearnerTagDto;
import com.english.entity.InteractionScore;
import com.english.entity.Text;
import com.english.entity.TextSimilarityMatrix;
import com.english.entity.UserSimilarityMatrix;
import com.english.mapper.InteractionScoreMapper;
import com.english.mapper.TextSimilarityMatrixMapper;
import com.english.mapper.UserSimilarityMatrixMapper;
import com.english.mapper.UserTextReadMapper;
import com.english.service.LearnerProfileService;
import com.english.service.RecommendationService;
import com.english.service.RecommendationTrackingService;
import com.english.service.TextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 混合推荐实现（Item-CF + User-CF + 多级兜底）
 * <p>
 * 相似度矩阵只存 a&lt;b 的一半对（对称矩阵减半存储），单次查询用 `text_id = X OR similar_text_id = X`
 * 一次拿到两侧，拿到行后取"非 seed 的那一端"作为候选。
 */
@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationServiceImpl.class);

    @Value("${recommendation.weight.item-cf:0.6}")
    private double itemCfWeight;

    @Value("${recommendation.weight.user-cf:0.4}")
    private double userCfWeight;

    @Value("${recommendation.similarity.text-threshold:0.2}")
    private double textSimilarityThreshold;

    @Value("${recommendation.similarity.user-threshold:0.3}")
    private double userSimilarityThreshold;

    @Value("${recommendation.cold-start.min-reads:3}")
    private int coldStartMinReads;

    @Value("${recommendation.user-cf.min-score:0.5}")
    private double userCfMinScore;

    @Value("${recommendation.item-cf.per-seed-limit:20}")
    private int itemCfPerSeedLimit;

    @Value("${recommendation.user-cf.per-user-limit:10}")
    private int userCfPerUserLimit;

    @Value("${recommendation.persona.enabled:false}")
    private boolean personaEnabled;

    @Value("${recommendation.persona.max-adjustment:0.12}")
    private double personaMaxAdjustment;

    @Value("${recommendation.weight.manual-featured-prior:3.0}")
    private double manualFeaturedPrior;

    @Value("${recommendation.weight.script-import-prior:0.15}")
    private double scriptImportPrior;

    @Value("${recommendation.weight.normal-text-prior:1.0}")
    private double normalTextPrior;

    @Value("${recommendation.quality-prior.manual-featured-min-priority:1}")
    private int manualFeaturedMinPriority;

    @Value("${recommendation.quality-prior.script-uploaders:24,420}")
    private String scriptUploadersRaw;

    @Value("${recommendation.quality-prior.script-source-domains:theguardian.com,time.com,newsweek.com,nytimes.com,scientificamerican.com,phys.org,futurity.org}")
    private String scriptSourceDomainsRaw;

    @Value("${recommendation.quality-prior.script-import-hours:21,22,23,0,1,2,3,4,8,9,10}")
    private String scriptImportHoursRaw;

    @Autowired
    private TextSimilarityMatrixMapper textSimilarityMatrixMapper;

    @Autowired
    private UserSimilarityMatrixMapper userSimilarityMatrixMapper;

    @Autowired
    private InteractionScoreMapper interactionScoreMapper;

    @Autowired
    private UserTextReadMapper userTextReadMapper;

    @Autowired
    private TextService textService;

    @Autowired(required = false)
    private LearnerProfileService learnerProfileService;

    @Autowired(required = false)
    private RecommendationTrackingService trackingService;

    @Override
    public List<Text> recommend(Long userId, int count, Collection<Long> excludeIds) {
        if (userId == null || count <= 0) {
            return Collections.emptyList();
        }

        List<Long> readTextIds = userTextReadMapper.selectReadTextIds(userId);
        Set<Long> excludeSet = new HashSet<>(readTextIds);
        if (excludeIds != null && !excludeIds.isEmpty()) {
            excludeSet.addAll(excludeIds);
        }

        PersonaSignals signals = personaEnabled ? safeLoadPersonaSignals(userId) : null;
        List<Text> result;

        if (readTextIds.size() < coldStartMinReads) {
            log.debug("[Recommend] userId={} 冷启动 (reads={} < {}), 走兜底策略",
                    userId, readTextIds.size(), coldStartMinReads);
            result = fallback(userId, count, excludeSet, new LinkedHashSet<>());
        } else {
            Map<Long, Double> scoreMap = new HashMap<>();
            accumulateItemCfScores(readTextIds, excludeSet, scoreMap);
            accumulateUserCfScores(userId, excludeSet, scoreMap);

            applyPersonaRerank(scoreMap, signals);
            applyQualityPriorRerank(scoreMap);

            LinkedHashSet<Long> topIds = scoreMap.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .limit(count)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (topIds.size() < count) {
                result = fallback(userId, count, excludeSet, topIds);
            } else {
                result = materializeInOrder(topIds);
            }
        }

        trackImpressions(userId, result, signals);
        return result;
    }

    private PersonaSignals safeLoadPersonaSignals(Long userId) {
        if (learnerProfileService == null) return null;
        try {
            return loadPersonaSignals(userId);
        } catch (Exception e) {
            log.warn("[Persona] 加载画像失败, 跳过重排 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private void trackImpressions(Long userId, List<Text> result, PersonaSignals signals) {
        if (trackingService == null || result.isEmpty()) return;
        try {
            List<Long> textIds = result.stream().map(Text::getId).collect(Collectors.toList());
            String personaTags = (signals != null && !signals.tags.isEmpty())
                    ? String.join(",", signals.tags)
                    : null;
            trackingService.trackBatchImpressions(userId, textIds, "RECOMMEND", personaTags);
        } catch (Exception e) {
            log.warn("[Tracking] 批量记录推荐IMPRESSION失败: userId={}", userId, e);
        }
    }

    private void accumulateItemCfScores(List<Long> readTextIds, Set<Long> excludeSet, Map<Long, Double> scoreMap) {
        BigDecimal threshold = BigDecimal.valueOf(textSimilarityThreshold);
        for (Long seedId : readTextIds) {
            List<TextSimilarityMatrix> rows = textSimilarityMatrixMapper.selectList(
                    new LambdaQueryWrapper<TextSimilarityMatrix>()
                            .and(w -> w.eq(TextSimilarityMatrix::getTextId, seedId)
                                    .or().eq(TextSimilarityMatrix::getSimilarTextId, seedId))
                            .gt(TextSimilarityMatrix::getSimilarity, threshold)
                            .orderByDesc(TextSimilarityMatrix::getSimilarity)
                            .last("LIMIT " + itemCfPerSeedLimit));
            for (TextSimilarityMatrix row : rows) {
                Long candidate = seedId.equals(row.getTextId()) ? row.getSimilarTextId() : row.getTextId();
                if (!excludeSet.contains(candidate)) {
                    scoreMap.merge(candidate, row.getSimilarity().doubleValue() * itemCfWeight, Double::sum);
                }
            }
        }
    }

    private void accumulateUserCfScores(Long userId, Set<Long> excludeSet, Map<Long, Double> scoreMap) {
        BigDecimal threshold = BigDecimal.valueOf(userSimilarityThreshold);
        List<UserSimilarityMatrix> rows = userSimilarityMatrixMapper.selectList(
                new LambdaQueryWrapper<UserSimilarityMatrix>()
                        .and(w -> w.eq(UserSimilarityMatrix::getUserId, userId)
                                .or().eq(UserSimilarityMatrix::getSimilarUserId, userId))
                        .gt(UserSimilarityMatrix::getSimilarity, threshold));

        Map<Long, Double> similarUserMap = new HashMap<>();
        for (UserSimilarityMatrix row : rows) {
            Long other = userId.equals(row.getUserId()) ? row.getSimilarUserId() : row.getUserId();
            similarUserMap.merge(other, row.getSimilarity().doubleValue(), Double::max);
        }

        for (Map.Entry<Long, Double> entry : similarUserMap.entrySet()) {
            Long similarUserId = entry.getKey();
            double userSim = entry.getValue();
            List<InteractionScore> highScoreTexts = interactionScoreMapper.selectList(
                    new LambdaQueryWrapper<InteractionScore>()
                            .eq(InteractionScore::getUserId, similarUserId)
                            .gt(InteractionScore::getInteractionScore, BigDecimal.valueOf(userCfMinScore))
                            .orderByDesc(InteractionScore::getInteractionScore)
                            .last("LIMIT " + userCfPerUserLimit));
            for (InteractionScore is : highScoreTexts) {
                if (!excludeSet.contains(is.getTextId())) {
                    double weighted = userSim * is.getInteractionScore().doubleValue() * userCfWeight;
                    scoreMap.merge(is.getTextId(), weighted, Double::sum);
                }
            }
        }
    }

    private List<Text> fallback(Long userId, int count, Set<Long> excludeSet, LinkedHashSet<Long> accumulatedIds) {
        fillFeatured(count, excludeSet, accumulatedIds);
        fillPreferredContentLevel(userId, count, excludeSet, accumulatedIds);
        fillLatest(count, excludeSet, accumulatedIds);
        return materializeInOrder(accumulatedIds);
    }

    private void fillFeatured(int count, Set<Long> excludeSet, LinkedHashSet<Long> accumulatedIds) {
        if (accumulatedIds.size() >= count) return;
        int need = count - accumulatedIds.size();
        Set<Long> skipIds = mergeExclusions(excludeSet, accumulatedIds);
        List<Text> featured = textService.lambdaQuery()
                .eq(Text::getDeleted, 0)
                .eq(Text::getIsFeatured, 1)
                .gt(Text::getFeaturedPriority, 0)
                .and(w -> w.isNull(Text::getUploadedBy)
                        .or(u -> u.eq(Text::getIsPublic, 1).eq(Text::getStatus, "APPROVED")))
                .notIn(!skipIds.isEmpty(), Text::getId, skipIds)
                .orderByDesc(Text::getFeaturedPriority)
                .orderByDesc(Text::getCreatedAt)
                .last("LIMIT " + need)
                .list();
        featured.forEach(t -> accumulatedIds.add(t.getId()));
    }

    private void fillPreferredContentLevel(Long userId, int count, Set<Long> excludeSet, LinkedHashSet<Long> accumulatedIds) {
        if (accumulatedIds.size() >= count) return;
        int need = count - accumulatedIds.size();
        String preferredDifficulty = userTextReadMapper.selectMostReadDifficulty(userId);
        Set<Long> skipIds = mergeExclusions(excludeSet, accumulatedIds);
        List<Text> byLevel = textService.lambdaQuery()
                .eq(Text::getDeleted, 0)
                .eq(preferredDifficulty != null, Text::getDifficulty, preferredDifficulty)
                .and(w -> w.isNull(Text::getUploadedBy)
                        .or(u -> u.eq(Text::getIsPublic, 1).eq(Text::getStatus, "APPROVED")))
                .notIn(!skipIds.isEmpty(), Text::getId, skipIds)
                .orderByDesc(Text::getCreatedAt)
                .last("LIMIT " + need)
                .list();
        byLevel.forEach(t -> accumulatedIds.add(t.getId()));
    }

    private void fillLatest(int count, Set<Long> excludeSet, LinkedHashSet<Long> accumulatedIds) {
        if (accumulatedIds.size() >= count) return;
        int need = count - accumulatedIds.size();
        Set<Long> skipIds = mergeExclusions(excludeSet, accumulatedIds);
        List<Text> latest = textService.lambdaQuery()
                .eq(Text::getDeleted, 0)
                .and(w -> w.isNull(Text::getUploadedBy)
                        .or(u -> u.eq(Text::getIsPublic, 1).eq(Text::getStatus, "APPROVED")))
                .notIn(!skipIds.isEmpty(), Text::getId, skipIds)
                .orderByDesc(Text::getCreatedAt)
                .last("LIMIT " + need)
                .list();
        latest.forEach(t -> accumulatedIds.add(t.getId()));
    }

    private Set<Long> mergeExclusions(Set<Long> excludeSet, LinkedHashSet<Long> accumulatedIds) {
        if (accumulatedIds.isEmpty()) return excludeSet;
        Set<Long> merged = new HashSet<>(excludeSet.size() + accumulatedIds.size());
        merged.addAll(excludeSet);
        merged.addAll(accumulatedIds);
        return merged;
    }

    private List<Text> materializeInOrder(LinkedHashSet<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        Map<Long, Text> byId = textService.listByIds(ids).stream()
                .filter(this::isFeedEligible)
                .collect(Collectors.toMap(Text::getId, t -> t));
        return ids.stream().map(byId::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private boolean isFeedEligible(Text text) {
        return text != null
                && !Integer.valueOf(1).equals(text.getDeleted())
                && (text.getUploadedBy() == null
                    || (Integer.valueOf(1).equals(text.getIsPublic())
                        && "APPROVED".equals(text.getStatus())));
    }

    // ── Persona reranking ──

    private void applyPersonaRerank(Map<Long, Double> scoreMap, PersonaSignals signals) {
        if (!personaEnabled || signals == null || scoreMap.isEmpty()) {
            return;
        }

        Set<Long> candidateIds = new HashSet<>(scoreMap.keySet());
        Map<Long, Text> textMap = textService.listByIds(candidateIds).stream()
                .collect(Collectors.toMap(Text::getId, t -> t));

        for (Map.Entry<Long, Double> entry : scoreMap.entrySet()) {
            Text text = textMap.get(entry.getKey());
            if (text == null) continue;
            double adj = computePersonaAdjustment(text, signals);
            if (adj != 0.0) {
                entry.setValue(entry.getValue() + adj);
            }
        }
        log.debug("[Persona] 重排完成, candidates={}", scoreMap.size());
    }

private void applyQualityPriorRerank(Map<Long, Double> scoreMap) {
    if (scoreMap.isEmpty()) {
        return;
    }

    Map<Long, Text> textMap = textService.listByIds(new HashSet<>(scoreMap.keySet())).stream()
            .collect(Collectors.toMap(Text::getId, t -> t));

    for (Map.Entry<Long, Double> entry : scoreMap.entrySet()) {
        Text text = textMap.get(entry.getKey());
        if (text == null) {
            continue;
        }
        entry.setValue(entry.getValue() * computeQualityPrior(text));
    }
}

private double computeQualityPrior(Text text) {
    if (isManualFeatured(text)) {
        return manualFeaturedPrior;
    }
    if (isScriptImport(text)) {
        return scriptImportPrior;
    }
    return normalTextPrior;
}

private boolean isManualFeatured(Text text) {
    Integer featured = text.getIsFeatured();
    Integer priority = text.getFeaturedPriority();
    return featured != null
            && featured == 1
            && priority != null
            && priority >= manualFeaturedMinPriority;
}

private boolean isScriptImport(Text text) {
    boolean byUploader = text.getUploadedBy() != null
            && parseLongSet(scriptUploadersRaw).contains(text.getUploadedBy());

    String host = extractSourceHost(text.getMarkdownContent());
    boolean bySource = host != null && matchesScriptSource(host, parseStringSet(scriptSourceDomainsRaw));
    boolean byHour = text.getCreatedAt() != null
            && parseIntSet(scriptImportHoursRaw).contains(text.getCreatedAt().getHour());

    return byUploader || (bySource && byHour);
}

private boolean matchesScriptSource(String host, Set<String> configuredDomains) {
    for (String domain : configuredDomains) {
        if (host.equals(domain) || host.endsWith("." + domain)) {
            return true;
        }
    }
    return false;
}

private String extractSourceHost(String markdownContent) {
    if (markdownContent == null || markdownContent.isEmpty()) {
        return null;
    }
    for (String line : markdownContent.split("\\R")) {
        String trimmed = line.trim();
        if (trimmed.startsWith("> 来源:") || trimmed.startsWith("> Source:")) {
            int idx = trimmed.indexOf(':');
            if (idx < 0 || idx + 1 >= trimmed.length()) {
                return null;
            }
            String url = trimmed.substring(idx + 1).trim();
            try {
                String host = URI.create(url).getHost();
                if (host == null) {
                    return null;
                }
                host = host.toLowerCase(Locale.ROOT);
                return host.startsWith("www.") ? host.substring(4) : host;
            } catch (Exception ignored) {
                return null;
            }
        }
    }
    return null;
}

private Set<Long> parseLongSet(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
        return Collections.emptySet();
    }
    return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Long::valueOf)
            .collect(Collectors.toSet());
}

private Set<Integer> parseIntSet(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
        return Collections.emptySet();
    }
    return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Integer::valueOf)
            .collect(Collectors.toSet());
}

private Set<String> parseStringSet(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
        return Collections.emptySet();
    }
    return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toLowerCase(Locale.ROOT))
            .map(s -> s.startsWith("www.") ? s.substring(4) : s)
            .collect(Collectors.toSet());
}
    
    private PersonaSignals loadPersonaSignals(Long userId) {
        LearnerProfileDto profile = learnerProfileService.getUserProfile(userId);
        if (profile == null || "empty".equals(profile.getProfileStatus())) {
            return null;
        }
        PersonaSignals signals = new PersonaSignals();
        if (profile.getPreference() != null) {
            signals.primaryContentVersion = profile.getPreference().getPrimaryContentVersion();
            signals.targetExamLevel = profile.getPreference().getTargetExamLevel();
        }
        if (profile.getRisk() != null) {
            signals.riskLevel = profile.getRisk().getRiskLevel();
        }
        if (profile.getTags() != null) {
            signals.tags = profile.getTags().stream()
                    .map(LearnerTagDto::getCode)
                    .collect(Collectors.toSet());
        }
        return signals;
    }

    private double computePersonaAdjustment(Text text, PersonaSignals signals) {
        double adjustment = 0.0;
        String difficulty = text.getDifficulty();

        String expectedDifficulty = mapVersionToDifficulty(signals.primaryContentVersion);
        if (expectedDifficulty != null && difficulty != null) {
            int distance = difficultyDistance(expectedDifficulty, difficulty);
            if (distance == 0) {
                adjustment += 0.04;
            } else if (distance >= 2) {
                adjustment -= 0.04;
            }
        }

        if (signals.tags.contains("VOCAB_WEAK") && difficulty != null) {
            if ("HARD".equalsIgnoreCase(difficulty)) {
                adjustment -= 0.04;
            } else if ("EASY".equalsIgnoreCase(difficulty)) {
                adjustment += 0.02;
            }
        }

        if (signals.tags.contains("INACTIVE_RISK") && difficulty != null) {
            if ("EASY".equalsIgnoreCase(difficulty) || difficulty.equalsIgnoreCase(expectedDifficulty)) {
                adjustment += 0.03;
            }
        }

        if (signals.tags.contains("CONTENT_LEVEL_STUCK") && difficulty != null && expectedDifficulty != null) {
            String nextLevel = nextDifficultyLevel(expectedDifficulty);
            if (nextLevel != null && nextLevel.equalsIgnoreCase(difficulty)) {
                adjustment += 0.04;
            }
        }

        if (signals.tags.contains("CONTENT_LEVEL_JUMP") && difficulty != null && expectedDifficulty != null) {
            if (expectedDifficulty.equalsIgnoreCase(difficulty)) {
                adjustment += 0.03;
            }
        }

        if ("high".equals(signals.riskLevel) && difficulty != null) {
            if ("EASY".equalsIgnoreCase(difficulty)) {
                adjustment += 0.02;
            } else if ("HARD".equalsIgnoreCase(difficulty)) {
                adjustment -= 0.02;
            }
        }

        return Math.max(-personaMaxAdjustment, Math.min(personaMaxAdjustment, adjustment));
    }

    private static String mapVersionToDifficulty(String contentVersion) {
        if (contentVersion == null) return null;
        switch (contentVersion) {
            case "cet4":
            case "chinese_parallel":
                return "EASY";
            case "cet6":
                return "MEDIUM";
            case "academic":
                return "HARD";
            default:
                return null;
        }
    }

    private static int difficultyDistance(String a, String b) {
        return Math.abs(difficultyOrdinal(a) - difficultyOrdinal(b));
    }

    private static int difficultyOrdinal(String d) {
        if (d == null) return -1;
        switch (d.toUpperCase()) {
            case "EASY":   return 0;
            case "MEDIUM": return 1;
            case "HARD":   return 2;
            default:       return -1;
        }
    }

    private static String nextDifficultyLevel(String current) {
        if (current == null) return null;
        switch (current.toUpperCase()) {
            case "EASY":   return "MEDIUM";
            case "MEDIUM": return "HARD";
            default:       return null;
        }
    }

    private static class PersonaSignals {
        String primaryContentVersion;
        String targetExamLevel;
        Set<String> tags = Collections.emptySet();
        String riskLevel;
    }
}
