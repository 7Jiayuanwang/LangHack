package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.learner.InternetContentRecommendationDto;
import com.english.entity.LearnerGoal;
import com.english.entity.LearnerInterest;
import com.english.entity.SchoolStudent;
import com.english.entity.StudentSchedule;
import com.english.entity.Text;
import com.english.entity.User;
import com.english.mapper.LearnerGoalMapper;
import com.english.mapper.LearnerInterestMapper;
import com.english.mapper.SchoolStudentMapper;
import com.english.service.InternetContentRecommendationService;
import com.english.service.RecommendationService;
import com.english.service.SchoolDataSyncService;
import com.english.service.SharedOpenContentPool;
import com.english.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternetContentRecommendationServiceImpl implements InternetContentRecommendationService {

    private static final Map<String, String> TOPIC_RULES = topicRules();
    private final SharedOpenContentPool sharedContentPool;
    private final LearnerInterestMapper interestMapper;
    private final LearnerGoalMapper goalMapper;
    private final SchoolStudentMapper schoolStudentMapper;
    private final SchoolDataSyncService schoolDataSyncService;
    private final UserService userService;
    private final RecommendationService recommendationService;

    @Override
    public List<InternetContentRecommendationDto> recommendForUser(Long userId, int limit) {
        if (userId == null || userId <= 0 || limit <= 0) return Collections.emptyList();
        int safeLimit = Math.min(limit, 12);
        Map<String, TopicSignal> signals = loadSignals(userId);

        List<InternetContentRecommendationDto> ranked = sharedContentPool.currentCandidates().stream()
                .map(candidate -> scoreCandidate(userId, candidate, signals))
                .filter(item -> item.getScore() > 0.18)
                .sorted(Comparator.comparingDouble(InternetContentRecommendationDto::getScore).reversed())
                .collect(Collectors.toList());

        List<InternetContentRecommendationDto> diverse = takeWithTopicDiversity(ranked, safeLimit);
        if (diverse.size() < safeLimit) {
            fillWithPlatformContent(userId, safeLimit, diverse);
        }
        return diverse;
    }

    private Map<String, TopicSignal> loadSignals(Long userId) {
        Map<String, TopicSignal> signals = new LinkedHashMap<>();
        try {
            List<LearnerInterest> interests = interestMapper.selectList(new QueryWrapper<LearnerInterest>()
                    .eq("user_id", userId).orderByDesc("weight").last("LIMIT 12"));
            for (LearnerInterest interest : interests) {
                addSignal(signals, interest.getName(), 0.92,
                        "因为你对“" + interest.getName() + "”感兴趣", SignalSource.INTEREST);
            }
        } catch (Exception e) {
            log.debug("读取内容推荐兴趣失败: userId={}, reason={}", userId, e.getMessage());
        }

        User user = null;
        try {
            user = userService.getUserById(userId);
            if (user != null && user.getUsername() != null) {
                SchoolStudent student = schoolStudentMapper.selectByStudentId(user.getUsername());
                if (student != null && student.getMajorName() != null && !student.getMajorName().isBlank()) {
                    addSignal(signals, student.getMajorName(), 0.84,
                            "与你的“" + student.getMajorName() + "”专业相关", SignalSource.MAJOR);
                }
            }
        } catch (Exception e) {
            log.debug("读取内容推荐专业失败: userId={}, reason={}", userId, e.getMessage());
        }

        if (user != null && user.getUsername() != null) {
            try {
                int today = java.time.LocalDate.now().getDayOfWeek().getValue();
                List<StudentSchedule> schedules = schoolDataSyncService.getStudentSchedule(
                        user.getUsername(), null, null);
                if (schedules != null) {
                    schedules.stream()
                            .filter(item -> item.getCourseName() != null && !item.getCourseName().isBlank())
                            .collect(Collectors.toMap(
                                    StudentSchedule::getCourseName,
                                    item -> item,
                                    (first, ignored) -> first,
                                    LinkedHashMap::new))
                            .values().stream().limit(8)
                            .forEach(item -> {
                                boolean isToday = item.getDayOfWeek() != null && item.getDayOfWeek() == today;
                                addSignal(signals, item.getCourseName(), isToday ? 1.0 : 0.78,
                                        isToday
                                                ? "与你今天的“" + item.getCourseName() + "”课程相关"
                                                : "与你本学期的“" + item.getCourseName() + "”课程相关",
                                        SignalSource.COURSE);
                            });
                }
            } catch (Exception e) {
                log.debug("读取内容推荐课表失败: userId={}, reason={}", userId, e.getMessage());
            }
        }

        try {
            List<LearnerGoal> goals = goalMapper.selectList(new QueryWrapper<LearnerGoal>()
                    .eq("user_id", userId).eq("status", "ACTIVE")
                    .orderByDesc("priority").last("LIMIT 5"));
            for (LearnerGoal goal : goals) {
                if ("ACADEMIC_READING".equals(goal.getGoalType())) {
                    addResolvedSignal(signals, "technology", 0.55,
                            "适合你的英文论文阅读目标", SignalSource.GOAL);
                } else if ("GENERAL".equals(goal.getGoalType())) {
                    addResolvedSignal(signals, "culture", 0.48,
                            "帮助你用英语探索更大的世界", SignalSource.GOAL);
                }
            }
        } catch (Exception e) {
            log.debug("读取内容推荐目标失败: userId={}, reason={}", userId, e.getMessage());
        }

        if (signals.isEmpty()) {
            addResolvedSignal(signals, "culture", 0.45,
                    "为你探索一个新的英语内容方向", SignalSource.EXPLORE);
            addResolvedSignal(signals, "technology", 0.4,
                    "看看英语可以怎样帮助你获取新信息", SignalSource.EXPLORE);
        }
        return signals;
    }

    private void addSignal(Map<String, TopicSignal> signals, String raw, double weight,
                           String reason, SignalSource source) {
        String topic = resolveCatalogTopic(raw);
        if (topic != null) addResolvedSignal(signals, topic, weight, reason, source);
    }

    private void addResolvedSignal(Map<String, TopicSignal> signals, String topic, double weight,
                                   String reason, SignalSource source) {
        TopicSignal existing = signals.get(topic);
        if (existing == null || weight > existing.weight) {
            signals.put(topic, new TopicSignal(topic, weight, reason, source));
        }
    }

    private String resolveCatalogTopic(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> rule : TOPIC_RULES.entrySet()) {
            if (value.contains(rule.getKey())) return rule.getValue();
        }
        return null;
    }

    private InternetContentRecommendationDto scoreCandidate(
            Long userId, SharedOpenContentPool.ContentCandidate candidate,
            Map<String, TopicSignal> signals) {
        InternetContentRecommendationDto item = new InternetContentRecommendationDto();
        item.setId(stableId(candidate.getUrl()));
        item.setTitle(candidate.getTitle());
        item.setSummary(candidate.getSummary());
        item.setUrl(candidate.getUrl().replace("http://arxiv.org/", "https://arxiv.org/"));
        item.setSourceName(candidate.getSourceName());
        item.setSourceType(candidate.getSourceType());
        item.setTopic(candidate.getCatalogTopic());
        item.setPublishedTime(candidate.getPublishedTime());
        item.setExternal(true);

        TopicSignal exact = signals.get(candidate.getCatalogTopic());
        double score = 0.12;
        if (exact != null) {
            score += exact.weight * 0.66;
            item.setReason(exact.reason);
            if ("RESEARCH".equals(candidate.getSourceType())
                    && (exact.source == SignalSource.MAJOR || exact.source == SignalSource.COURSE)) {
                score += 0.08;
            }
        } else {
            TopicSignal semantic = bestTextMatch(candidate, signals);
            if (semantic != null) {
                score += semantic.weight * 0.34;
                item.setReason(semantic.reason);
            } else {
                item.setReason("为你保留的探索性英语内容");
                score += 0.04;
            }
        }
        if ("ENCYCLOPEDIA".equals(candidate.getSourceType())) score += 0.07;
        if (candidate.getPublishedTime() != null) {
            long days = Math.max(0, Duration.between(candidate.getPublishedTime(), LocalDateTime.now()).toDays());
            score += days <= 30 ? 0.09 : days <= 180 ? 0.05 : 0.01;
        }
        score += Math.floorMod((userId + item.getId()).hashCode(), 100) / 5000.0;
        item.setScore(Math.min(1.0, score));
        return item;
    }

    private TopicSignal bestTextMatch(SharedOpenContentPool.ContentCandidate candidate,
                                      Map<String, TopicSignal> signals) {
        String haystack = ((candidate.getTitle() == null ? "" : candidate.getTitle()) + " "
                + (candidate.getSummary() == null ? "" : candidate.getSummary())).toLowerCase(Locale.ROOT);
        return signals.values().stream()
                .filter(signal -> containsMeaningfulToken(haystack, signal.topic))
                .max(Comparator.comparingDouble(signal -> signal.weight))
                .orElse(null);
    }

    private boolean containsMeaningfulToken(String haystack, String topic) {
        if (haystack.contains(topic)) return true;
        return java.util.Arrays.stream(topic.split(" "))
                .filter(token -> token.length() >= 5)
                .anyMatch(haystack::contains);
    }

    private List<InternetContentRecommendationDto> takeWithTopicDiversity(
            List<InternetContentRecommendationDto> ranked, int limit) {
        List<InternetContentRecommendationDto> result = new ArrayList<>();
        Map<String, Integer> topicCounts = new HashMap<>();
        for (InternetContentRecommendationDto item : ranked) {
            int count = topicCounts.getOrDefault(item.getTopic(), 0);
            if (count >= 2) continue;
            result.add(item);
            topicCounts.put(item.getTopic(), count + 1);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private void fillWithPlatformContent(Long userId, int limit,
                                         List<InternetContentRecommendationDto> result) {
        try {
            int need = limit - result.size();
            if (need <= 0) return;
            List<Text> fallback = recommendationService.recommend(userId, need);
            for (Text text : fallback) {
                InternetContentRecommendationDto item = new InternetContentRecommendationDto();
                item.setId("text-" + text.getId());
                item.setTitle(text.getTitle());
                item.setSummary(trim(text.getContent(), 260));
                item.setUrl("/article/" + text.getId());
                item.setSourceName("LangHack");
                item.setSourceType("PLATFORM");
                item.setTopic(text.getCategory() == null ? "learning" : text.getCategory());
                item.setReason("根据你在平台内的学习记录推荐");
                item.setPublishedTime(text.getCreatedAt());
                item.setScore(0.2);
                item.setExternal(false);
                result.add(item);
                if (result.size() >= limit) break;
            }
        } catch (Exception e) {
            log.debug("平台内容兜底失败: userId={}, reason={}", userId, e.getMessage());
        }
    }

    private String stableId(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String trim(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength - 1).trim() + "…";
    }

    private static Map<String, String> topicRules() {
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("人工智能", "artificial intelligence");
        rules.put("machine learning", "artificial intelligence");
        rules.put("artificial intelligence", "artificial intelligence");
        rules.put("计算机", "computer science");
        rules.put("数据结构", "computer science");
        rules.put("算法", "computer science");
        rules.put("软件", "computer science");
        rules.put("网络", "computer science");
        rules.put("computer", "computer science");
        rules.put("游戏", "technology");
        rules.put("科技", "technology");
        rules.put("technology", "technology");
        rules.put("农学", "agriculture");
        rules.put("农业", "agriculture");
        rules.put("植物", "agriculture");
        rules.put("动物", "agriculture");
        rules.put("草业", "agriculture");
        rules.put("食品", "agriculture");
        rules.put("agriculture", "agriculture");
        rules.put("环境", "environmental science");
        rules.put("生态", "environmental science");
        rules.put("environment", "environmental science");
        rules.put("经济", "business");
        rules.put("金融", "business");
        rules.put("管理", "business");
        rules.put("商业", "business");
        rules.put("business", "business");
        rules.put("心理", "psychology");
        rules.put("psychology", "psychology");
        rules.put("体育", "sports science");
        rules.put("篮球", "sports science");
        rules.put("足球", "sports science");
        rules.put("运动", "sports science");
        rules.put("sport", "sports science");
        rules.put("音乐", "culture");
        rules.put("影视", "culture");
        rules.put("电影", "culture");
        rules.put("文学", "culture");
        rules.put("旅行", "culture");
        rules.put("历史", "culture");
        rules.put("culture", "culture");
        rules.put("music", "culture");
        rules.put("film", "culture");
        rules.put("travel", "culture");
        return Collections.unmodifiableMap(rules);
    }

    private enum SignalSource { INTEREST, MAJOR, COURSE, GOAL, EXPLORE }

    private static class TopicSignal {
        private final String topic;
        private final double weight;
        private final String reason;
        private final SignalSource source;

        private TopicSignal(String topic, double weight, String reason, SignalSource source) {
            this.topic = topic;
            this.weight = weight;
            this.reason = reason;
            this.source = source;
        }
    }
}
