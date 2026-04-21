package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.entity.InteractionScore;
import com.english.entity.Text;
import com.english.entity.TextSimilarityMatrix;
import com.english.entity.UserSimilarityMatrix;
import com.english.mapper.InteractionScoreMapper;
import com.english.mapper.TextSimilarityMatrixMapper;
import com.english.mapper.UserSimilarityMatrixMapper;
import com.english.mapper.UserTextReadMapper;
import com.english.service.RecommendationService;
import com.english.service.TextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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

    @Override
    public List<Text> recommend(Long userId, int count) {
        if (userId == null || count <= 0) {
            return Collections.emptyList();
        }

        List<Long> readTextIds = userTextReadMapper.selectReadTextIds(userId);
        Set<Long> excludeSet = new HashSet<>(readTextIds);

        if (readTextIds.size() < coldStartMinReads) {
            log.debug("[Recommend] userId={} 冷启动 (reads={} < {}), 走兜底策略",
                    userId, readTextIds.size(), coldStartMinReads);
            return fallback(userId, count, excludeSet, new LinkedHashSet<>());
        }

        Map<Long, Double> scoreMap = new HashMap<>();
        accumulateItemCfScores(readTextIds, excludeSet, scoreMap);
        accumulateUserCfScores(userId, excludeSet, scoreMap);

        LinkedHashSet<Long> topIds = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (topIds.size() < count) {
            return fallback(userId, count, excludeSet, topIds);
        }
        return materializeInOrder(topIds);
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
        if (accumulatedIds.size() < count) {
            int need = count - accumulatedIds.size();
            Set<Long> skipIds = mergeExclusions(excludeSet, accumulatedIds);
            List<Text> featured = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .eq(Text::getIsFeatured, 1)
                    .gt(Text::getFeaturedPriority, 0)
                    .notIn(!skipIds.isEmpty(), Text::getId, skipIds)
                    .orderByDesc(Text::getFeaturedPriority)
                    .orderByDesc(Text::getCreatedAt)
                    .last("LIMIT " + need)
                    .list();
            featured.forEach(t -> accumulatedIds.add(t.getId()));
        }

        if (accumulatedIds.size() < count) {
            int need = count - accumulatedIds.size();
            String preferredDifficulty = userTextReadMapper.selectMostReadDifficulty(userId);
            Set<Long> skipIds = mergeExclusions(excludeSet, accumulatedIds);
            List<Text> byLevel = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .eq(preferredDifficulty != null, Text::getDifficulty, preferredDifficulty)
                    .notIn(!skipIds.isEmpty(), Text::getId, skipIds)
                    .orderByDesc(Text::getCreatedAt)
                    .last("LIMIT " + need)
                    .list();
            byLevel.forEach(t -> accumulatedIds.add(t.getId()));

            // 众数难度下仍不足（或用户没有已读记录），忽略难度取最新
            if (accumulatedIds.size() < count) {
                int stillNeed = count - accumulatedIds.size();
                Set<Long> skipIds2 = mergeExclusions(excludeSet, accumulatedIds);
                List<Text> latest = textService.lambdaQuery()
                        .eq(Text::getDeleted, 0)
                        .notIn(!skipIds2.isEmpty(), Text::getId, skipIds2)
                        .orderByDesc(Text::getCreatedAt)
                        .last("LIMIT " + stillNeed)
                        .list();
                latest.forEach(t -> accumulatedIds.add(t.getId()));
            }
        }
        return materializeInOrder(accumulatedIds);
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
                .collect(Collectors.toMap(Text::getId, t -> t));
        return ids.stream().map(byId::get).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
