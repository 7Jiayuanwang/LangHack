package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.dto.learner.ArticleFeedCandidate;
import com.english.dto.learner.ArticleFeedEventRequest;
import com.english.dto.learner.ArticleFeedItemDto;
import com.english.dto.learner.ArticleFeedPageDto;
import com.english.entity.ArticleInsightMessage;
import com.english.entity.LearnerGoal;
import com.english.entity.LearnerInterest;
import com.english.entity.RecommendationEvent;
import com.english.entity.SchoolStudent;
import com.english.entity.StudentSchedule;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.ArticleFeedMapper;
import com.english.mapper.ArticleInsightMessageMapper;
import com.english.mapper.LearnerGoalMapper;
import com.english.mapper.LearnerInterestMapper;
import com.english.mapper.RecommendationEventMapper;
import com.english.mapper.SchoolStudentMapper;
import com.english.mapper.TextLearningRecordMapper;
import com.english.mapper.UserTextReadMapper;
import com.english.service.SchoolDataSyncService;
import com.english.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A small, explainable feed for the cold-start stage. Candidate queries return
 * short projections; no full article body or student profile leaves this service.
 */
@Service
@RequiredArgsConstructor
public class ArticleFeedService {
    private static final Logger log = LoggerFactory.getLogger(ArticleFeedService.class);
    private static final Set<String> CATEGORIES = Set.of(
            "technology", "business", "education", "exam", "lifestyle", "culture");
    private static final Set<String> SOURCES = Set.of(
            "FEED_FOR_YOU", "FEED_EXPLORE", "FEED_CATEGORY");
    private static final Set<String> EVENT_TYPES = Set.of("IMPRESSION", "CLICK", "DISMISS");
    private static final Map<String, List<String>> TOPIC_ALIASES = topicAliases();

    private final ArticleFeedMapper feedMapper;
    private final LearnerInterestMapper interestMapper;
    private final LearnerGoalMapper goalMapper;
    private final SchoolStudentMapper schoolStudentMapper;
    private final SchoolDataSyncService schoolDataSyncService;
    private final UserService userService;
    private final UserTextReadMapper readMapper;
    private final TextLearningRecordMapper learningRecordMapper;
    private final ArticleInsightMessageMapper insightMessageMapper;
    private final RecommendationEventMapper eventMapper;

    public ArticleFeedPageDto getPage(Long userId, String mode, String category,
                                      int page, int size, Long requestedSeed, List<Long> excludeIds) {
        if (userId == null || userId <= 0) throw new BusinessException("请先登录");
        if (page > 10_000) throw new BusinessException("页码过大");
        if (!"FOR_YOU".equalsIgnoreCase(mode) && !"EXPLORE".equalsIgnoreCase(mode)) {
            throw new BusinessException("无效的 Feed 模式");
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 20);
        long seed = requestedSeed == null || requestedSeed <= 0
                ? System.currentTimeMillis() : requestedSeed;
        String safeCategory = category != null && CATEGORIES.contains(category) ? category : null;
        boolean forYou = "FOR_YOU".equalsIgnoreCase(mode);

        ArticleFeedPageDto response = new ArticleFeedPageDto();
        response.setSeed(seed);
        response.setNextPage(safePage + 1);

        if (safePage == 1) {
            response.setContinueReading(loadContinueAction(userId));
            response.setReview(loadReviewAction(userId));
        }

        if (!forYou) {
            List<ArticleFeedCandidate> candidates = feedMapper.selectExplore(
                    safeCategory, seed, (safePage - 1) * safeSize, safeSize + 1);
            response.setHasMore(candidates.size() > safeSize);
            int limit = Math.min(safeSize, candidates.size());
            for (int i = 0; i < limit; i++) {
                response.getItems().add(toItem(candidates.get(i),
                        safeCategory == null ? "发现新的英文内容" : "更多" + categoryLabel(safeCategory) + "主题",
                        "EXPLORE", (safePage - 1) * safeSize + i));
            }
            return response;
        }

        List<Signal> signals = loadSignals(userId);
        response.setOnboardingSuggested(signals.stream()
                .noneMatch(s -> s.type == SignalType.INTEREST || s.type == SignalType.GOAL));
        Map<Long, ArticleFeedCandidate> pool = new LinkedHashMap<>();
        addCandidates(pool, feedMapper.selectFeatured(seed, 80));
        addCandidates(pool, feedMapper.selectRecent(seed, 220));
        Set<Long> skipped = new HashSet<>(safeList(eventMapper.selectRecentFeedDismissals(userId)));
        if (excludeIds != null) excludeIds.stream().filter(Objects::nonNull).limit(80).forEach(skipped::add);
        Set<Long> read = new HashSet<>(safeList(readMapper.selectReadTextIds(userId)));
        Set<Long> completed = new HashSet<>(safeList(learningRecordMapper.selectRecentlyCompletedTextIds(userId)));

        List<Ranked> ranked = pool.values().stream()
                .filter(item -> item.getId() != null && !skipped.contains(item.getId()))
                .map(item -> rank(item, signals, read, completed, userId, seed))
                .sorted(Comparator.comparingDouble(Ranked::score).reversed()
                        .thenComparing(r -> r.candidate().getId()))
                .toList();
        List<Ranked> diverse = diversifyFirstScreen(ranked);
        int from = Math.min(diverse.size(), (safePage - 1) * safeSize);
        int to = Math.min(diverse.size(), from + safeSize);
        for (int i = from; i < to; i++) {
            Ranked item = diverse.get(i);
            response.getItems().add(toItem(item.candidate(), item.reason(), "FOR_YOU", i));
        }
        response.setHasMore(to < diverse.size());
        return response;
    }

    public void recordEvent(Long userId, ArticleFeedEventRequest request) {
        if (userId == null || userId <= 0 || request == null
                || request.getTextId() == null || request.getTextId() <= 0
                || !EVENT_TYPES.contains(request.getEventType())
                || !SOURCES.contains(request.getSource())
                || request.getPosition() == null || request.getPosition() < 0
                || request.getPosition() > 600) {
            throw new BusinessException("无效的 Feed 事件");
        }
        if (feedMapper.selectEligibleById(request.getTextId()) == null) {
            throw new BusinessException("文章不可访问");
        }
        RecommendationEvent event = new RecommendationEvent();
        event.setUserId(userId);
        event.setTextId(request.getTextId());
        event.setEventType(request.getEventType());
        event.setSource(request.getSource());
        event.setPosition(request.getPosition());
        event.setCreatedTime(LocalDateTime.now());
        eventMapper.insert(event);
    }

    private ArticleFeedItemDto loadContinueAction(Long userId) {
        try {
            Long id = learningRecordMapper.selectLatestUnfinishedTextId(userId);
            ArticleFeedCandidate item = id == null ? null : feedMapper.selectEligibleById(id);
            return item == null ? null : toItem(item, "接着上次读到的地方", "CONTINUE", 0);
        } catch (Exception e) {
            log.debug("读取继续阅读失败: {}", e.getMessage());
            return null;
        }
    }

    private ArticleFeedItemDto loadReviewAction(Long userId) {
        try {
            List<ArticleInsightMessage> messages = insightMessageMapper.selectList(
                    new LambdaQueryWrapper<ArticleInsightMessage>()
                            .eq(ArticleInsightMessage::getUserId, userId)
                            .isNull(ArticleInsightMessage::getReadTime)
                            .orderByDesc(ArticleInsightMessage::getCreatedTime)
                            .last("LIMIT 5"));
            for (ArticleInsightMessage message : safeList(messages)) {
                ArticleFeedCandidate item = feedMapper.selectEligibleById(message.getTextId());
                if (item != null) return toItem(item, "小芽有这篇文章的知识点等你回顾", "REVIEW", 0);
            }
        } catch (Exception e) {
            log.debug("读取待回顾知识点失败: {}", e.getMessage());
        }
        return null;
    }

    private List<Signal> loadSignals(Long userId) {
        List<Signal> signals = new ArrayList<>();
        try {
            for (LearnerInterest interest : safeList(interestMapper.selectList(
                    new LambdaQueryWrapper<LearnerInterest>()
                            .eq(LearnerInterest::getUserId, userId)
                            .orderByDesc(LearnerInterest::getWeight)
                            .last("LIMIT 10")))) {
                addSignal(signals, interest.getName(), SignalType.INTEREST, 3.0,
                        "因为你对“" + interest.getName() + "”感兴趣");
            }
        } catch (Exception e) {
            log.debug("Feed 兴趣信号不可用: {}", e.getMessage());
        }
        try {
            for (LearnerGoal goal : safeList(goalMapper.selectList(
                    new LambdaQueryWrapper<LearnerGoal>()
                            .eq(LearnerGoal::getUserId, userId)
                            .eq(LearnerGoal::getStatus, "ACTIVE")
                            .orderByDesc(LearnerGoal::getPriority)
                            .last("LIMIT 5")))) {
                if (goal.getGoalType() == null) continue;
                String topic = switch (goal.getGoalType()) {
                    case "CET4", "CET6", "POSTGRADUATE" -> "exam";
                    case "ACADEMIC_READING" -> "science";
                    case "GENERAL" -> "culture";
                    default -> null;
                };
                if (topic != null) signals.add(new Signal(topic, "", SignalType.GOAL, 1.4,
                        "与你的“" + goal.getTitle() + "”目标相关"));
                else addSignal(signals, goal.getTitle(), SignalType.GOAL, 1.2,
                        "与你的“" + goal.getTitle() + "”目标相关");
            }
        } catch (Exception e) {
            log.debug("Feed 目标信号不可用: {}", e.getMessage());
        }
        try {
            User user = userService.getUserById(userId);
            if (user != null && user.getUsername() != null) {
                String studentId = user.getUsername();
                SchoolStudent student = schoolStudentMapper.selectByStudentId(studentId);
                if (student != null) addSignal(signals, student.getMajorName(), SignalType.MAJOR,
                        2.3, "与你的专业“" + student.getMajorName() + "”相关");
                Map<String, StudentSchedule> courses = new LinkedHashMap<>();
                for (StudentSchedule schedule : safeList(schoolDataSyncService.getStudentSchedule(
                        studentId, null, null))) {
                    if (isCurrentTerm(schedule, LocalDate.now())
                            && schedule.getCourseName() != null && !schedule.getCourseName().isBlank()) {
                        courses.putIfAbsent(schedule.getCourseName(), schedule);
                    }
                }
                courses.values().stream().limit(8).forEach(course -> {
                    boolean today = Objects.equals(course.getDayOfWeek(), LocalDate.now().getDayOfWeek().getValue());
                    addSignal(signals, course.getCourseName(),
                            today ? SignalType.TODAY_COURSE : SignalType.COURSE,
                            today ? 2.8 : 1.8,
                            today ? "与你今天的“" + course.getCourseName() + "”课程相关"
                                    : "与你本学期的“" + course.getCourseName() + "”课程相关");
                });
            }
        } catch (Exception e) {
            log.debug("Feed 学校信号不可用: {}", e.getMessage());
        }
        return signals;
    }

    private boolean isCurrentTerm(StudentSchedule schedule, LocalDate today) {
        if (schedule.getAcademicYear() == null || schedule.getSemester() == null) return false;
        int month = today.getMonthValue();
        int startYear = month >= 8 ? today.getYear() : today.getYear() - 1;
        int semester = month >= 8 || month == 1 ? 1 : 2;
        java.util.regex.Matcher year = java.util.regex.Pattern.compile("20\\d{2}")
                .matcher(schedule.getAcademicYear());
        return year.find() && Integer.parseInt(year.group()) == startYear
                && schedule.getSemester() == semester;
    }

    private void addSignal(List<Signal> signals, String raw, SignalType type,
                           double weight, String reason) {
        if (raw == null || raw.isBlank()) return;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        String topic = resolveTopic(normalized);
        signals.add(new Signal(topic, normalized, type, weight, reason));
    }

    private Ranked rank(ArticleFeedCandidate candidate, List<Signal> signals,
                        Set<Long> read, Set<Long> completed, Long userId, long seed) {
        Signal best = null;
        double match = 0;
        for (Signal signal : signals) {
            if (matches(candidate, signal)) {
                match += signal.weight;
                if (best == null || signal.weight > best.weight) best = signal;
            }
        }
        double score = Math.min(match, 5.0);
        Integer priority = candidate.getFeaturedPriority();
        if (Integer.valueOf(1).equals(candidate.getIsFeatured())) {
            score += 0.7 + Math.min(0.7, Math.max(0, priority == null ? 0 : priority) * 0.08);
        }
        LocalDateTime pageTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(seed), ZoneId.systemDefault());
        if (candidate.getCreatedAt() != null
                && !candidate.getCreatedAt().isAfter(pageTime)
                && ChronoUnit.DAYS.between(candidate.getCreatedAt(), pageTime) <= 30) {
            score += 0.25;
        }
        if (read.contains(candidate.getId())) score -= 0.7;
        if (completed.contains(candidate.getId())) score -= 2.0;
        score += Math.floorMod(Objects.hash(userId, candidate.getId(), seed), 1000) / 2500.0;
        String reason = best != null ? best.reason
                : Integer.valueOf(1).equals(candidate.getIsFeatured()) ? "小芽精选的英文内容"
                : "换个主题，用英语发现新信息";
        return new Ranked(candidate, score, reason);
    }

    private boolean matches(ArticleFeedCandidate candidate, Signal signal) {
        String haystack = (nullToEmpty(candidate.getTitle()) + " " + nullToEmpty(candidate.getCategory())
                + " " + nullToEmpty(candidate.getTags())).toLowerCase(Locale.ROOT);
        if (signal.raw.length() >= 3 && haystack.contains(signal.raw)) return true;
        if (signal.topic == null) return false;
        for (String alias : TOPIC_ALIASES.getOrDefault(signal.topic, List.of(signal.topic))) {
            if (alias.length() <= 2) {
                if (haystack.matches("(?s).*\\b" + alias + "\\b.*")) return true;
            } else if (haystack.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private String resolveTopic(String raw) {
        for (Map.Entry<String, List<String>> entry : TOPIC_ALIASES.entrySet()) {
            if (raw.contains(entry.getKey())) return entry.getKey();
            for (String alias : entry.getValue()) {
                if (raw.contains(alias)) return entry.getKey();
            }
        }
        return null;
    }

    private List<Ranked> diversifyFirstScreen(List<Ranked> ranked) {
        List<Ranked> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        Map<String, Integer> categoryCounts = new HashMap<>();
        int firstScreen = Math.min(8, ranked.size());
        for (Ranked item : ranked) {
            String category = nullToEmpty(item.candidate().getCategory());
            if (result.size() >= firstScreen) break;
            if (categoryCounts.getOrDefault(category, 0) >= 2) continue;
            result.add(item);
            used.add(item.candidate().getId());
            categoryCounts.merge(category, 1, Integer::sum);
        }
        for (Ranked item : ranked) {
            if (used.add(item.candidate().getId())) result.add(item);
        }
        return result;
    }

    private ArticleFeedItemDto toItem(ArticleFeedCandidate candidate, String reason,
                                      String slot, int position) {
        ArticleFeedItemDto item = new ArticleFeedItemDto();
        item.setId(candidate.getId());
        item.setTitle(candidate.getTitle());
        item.setExcerpt(candidate.getExcerpt());
        item.setCategory(candidate.getCategory());
        item.setTags(candidate.getTags());
        item.setDifficulty(candidate.getDifficulty());
        item.setCoverUrl(candidate.getCoverUrl());
        item.setEstimatedMinutes(Math.max(1, (int) Math.ceil(
                (candidate.getContentLength() == null ? 800 : candidate.getContentLength()) / 800.0)));
        item.setReason(reason);
        item.setSlot(slot);
        item.setPosition(position);
        return item;
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "technology" -> "科技";
            case "business" -> "商务";
            case "education" -> "教育";
            case "exam" -> "考试";
            case "lifestyle" -> "生活";
            case "culture" -> "文化";
            default -> "英文";
        };
    }

    private void addCandidates(Map<Long, ArticleFeedCandidate> pool,
                               List<ArticleFeedCandidate> candidates) {
        for (ArticleFeedCandidate candidate : safeList(candidates)) {
            if (candidate != null && candidate.getId() != null) pool.putIfAbsent(candidate.getId(), candidate);
        }
    }

    private static <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, List<String>> topicAliases() {
        Map<String, List<String>> topics = new LinkedHashMap<>();
        topics.put("artificial intelligence", List.of("人工智能", "machine learning", "artificial intelligence", "ai"));
        topics.put("computer science", List.of("计算机", "软件", "编程", "computer", "software", "coding"));
        topics.put("agriculture", List.of("农学", "农业", "植物", "草业", "食品", "agriculture", "farming", "plant"));
        topics.put("environment", List.of("环境", "生态", "environment", "climate", "ecology"));
        topics.put("technology", List.of("科技", "游戏", "technology", "tech", "game"));
        topics.put("business", List.of("商务", "经济", "金融", "管理", "business", "finance", "economy"));
        topics.put("psychology", List.of("心理", "psychology", "mind"));
        topics.put("sport", List.of("运动", "体育", "篮球", "足球", "sport", "basketball", "football"));
        topics.put("culture", List.of("文化", "音乐", "电影", "文学", "旅行", "历史", "culture", "music", "film", "travel", "history"));
        topics.put("exam", List.of("四六级", "四级", "六级", "考研", "exam", "cet4", "cet6", "test"));
        topics.put("science", List.of("论文", "科研", "学术", "science", "research", "academic"));
        return topics;
    }

    private enum SignalType { INTEREST, MAJOR, TODAY_COURSE, COURSE, GOAL }
    private record Signal(String topic, String raw, SignalType type, double weight, String reason) { }
    private record Ranked(ArticleFeedCandidate candidate, double score, String reason) { }
}
