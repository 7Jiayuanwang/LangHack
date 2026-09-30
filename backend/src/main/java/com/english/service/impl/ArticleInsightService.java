package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.english.datasource.UseMaster;
import com.english.dto.learner.ArticleInsightMessageDto;
import com.english.dto.learner.ArticleLearningInsightDto;
import com.english.dto.learner.ArticleLearningInsightDto.KnowledgePoint;
import com.english.entity.ArticleInsightMessage;
import com.english.entity.ArticleLearningInsight;
import com.english.entity.Text;
import com.english.entity.TextLearningRecord;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.ArticleInsightMessageMapper;
import com.english.mapper.ArticleLearningInsightMapper;
import com.english.mapper.TextLearningRecordMapper;
import com.english.mapper.TextMapper;
import com.english.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Service
@UseMaster
public class ArticleInsightService {
    private final ArticleLearningInsightMapper insightMapper;
    private final ArticleInsightMessageMapper messageMapper;
    private final TextLearningRecordMapper readingMapper;
    private final TextMapper textMapper;
    private final UserService userService;
    private final ArticleInsightLevelService levelService;
    private final ArticleInsightGenerationService generationService;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Long> retryAfter = new ConcurrentHashMap<>();
    private final Object[] keyLocks = new Object[64];

    public ArticleInsightService(ArticleLearningInsightMapper insightMapper,
                                 ArticleInsightMessageMapper messageMapper,
                                 TextLearningRecordMapper readingMapper,
                                 TextMapper textMapper,
                                 UserService userService,
                                 ArticleInsightLevelService levelService,
                                 ArticleInsightGenerationService generationService,
                                 ObjectMapper objectMapper,
                                 @Qualifier("articleInsightExecutor") Executor executor) {
        this.insightMapper = insightMapper;
        this.messageMapper = messageMapper;
        this.readingMapper = readingMapper;
        this.textMapper = textMapper;
        this.userService = userService;
        this.levelService = levelService;
        this.generationService = generationService;
        this.objectMapper = objectMapper;
        this.executor = executor;
        for (int i = 0; i < keyLocks.length; i++) keyLocks[i] = new Object();
    }

    /** 阅读已落库后调用；知识点失败不能影响阅读完成。 */
    public void requestAfterCompletion(Long userId, Long textId) {
        try {
            User user = userService.getUserById(userId);
            if (user == null || !("STUDENT".equals(user.getRole()) || "USER".equals(user.getRole()))) return;
            Prepared prepared = prepare(userId, textId);
            if (prepared != null) ensureGenerated(userId, prepared, true);
        } catch (Exception e) {
            log.warn("小芽知识点任务未启动: userId={}, textId={}, reason={}", userId, textId, e.getMessage());
        }
    }

    public ArticleLearningInsightDto getForCompletedArticle(Long userId, Long textId) {
        return resolve(userId, prepare(userId, textId), true);
    }

    /** Listening to an article can reveal its points without falsely marking reading as complete. */
    public ArticleLearningInsightDto getForListening(Long userId, Long textId, String version) {
        return resolve(userId, prepareForVersion(userId, textId, version), false);
    }

    private ArticleLearningInsightDto resolve(Long userId, Prepared prepared, boolean deliverMessage) {
        ArticleLearningInsightDto result = new ArticleLearningInsightDto();
        if (prepared == null) {
            result.setStatus("UNAVAILABLE");
            result.setMessage("这篇文章暂时没有可用的英文正文");
            return result;
        }
        result.setTargetLevel(prepared.level());
        result.setLevelReason(prepared.levelReason());
        ArticleLearningInsight cached = findCached(prepared);
        if (cached != null) {
            if (deliverMessage) deliver(userId, prepared.textId(), cached.getId());
            result.setStatus("READY");
            result.setPoints(readPoints(cached));
            return result;
        }
        String flightKey = userId + ":" + prepared.cacheKey();
        if (retryAfter.getOrDefault(flightKey, 0L) > System.currentTimeMillis()) {
            result.setStatus("UNAVAILABLE");
            result.setMessage("小芽暂时没能提炼出可靠的知识点，稍后可以重试");
        } else {
            ensureGenerated(userId, prepared, deliverMessage);
            result.setStatus("PENDING");
            result.setMessage("小芽正在从这篇文章中挑选适合你的知识点…");
        }
        return result;
    }

    public List<ArticleInsightMessageDto> getUnread(Long userId) {
        List<ArticleInsightMessage> unread = messageMapper.selectList(new QueryWrapper<ArticleInsightMessage>()
                .eq("user_id", userId).isNull("read_time")
                .orderByDesc("created_time").orderByDesc("id").last("LIMIT 30"));
        List<ArticleInsightMessageDto> result = new ArrayList<>();
        for (ArticleInsightMessage message : unread) {
            ArticleLearningInsight insight = insightMapper.selectById(message.getInsightId());
            if (insight == null) continue;
            String title = textMapper.selectActiveTitleById(message.getTextId());
            ArticleInsightMessageDto dto = new ArticleInsightMessageDto();
            dto.setId(message.getId());
            dto.setTextId(message.getTextId());
            dto.setTitle("小芽 · 《" + (title == null || title.isBlank() ? "已读文章" : title) + "》的知识点");
            StringBuilder content = new StringBuilder("读完这篇文章，你可以重点掌握：\n");
            for (KnowledgePoint point : readPoints(insight)) {
                content.append("\n• ").append(point.getExpression()).append("：")
                        .append(point.getExplanation()).append("\n原句：")
                        .append(point.getSourceSentence()).append("\n");
            }
            dto.setContent(content.toString());
            result.add(dto);
        }
        return result;
    }

    public void markRead(Long userId, Long messageId) {
        ArticleInsightMessage message = messageMapper.selectById(messageId);
        if (message == null || !userId.equals(message.getUserId())) {
            throw new BusinessException("消息不存在或无权查看");
        }
        if (message.getReadTime() == null) {
            messageMapper.update(null, new UpdateWrapper<ArticleInsightMessage>()
                    .eq("id", messageId).eq("user_id", userId).isNull("read_time")
                    .set("read_time", LocalDateTime.now()));
        }
    }

    private Prepared prepare(Long userId, Long textId) {
        if (textId == null || textId <= 0) throw new BusinessException("文章不存在");
        TextLearningRecord reading = readingMapper.selectOne(new QueryWrapper<TextLearningRecord>()
                .eq("student_id", userId).eq("text_id", textId).eq("is_completed", 1)
                .orderByDesc("end_time").orderByDesc("id").last("LIMIT 1"));
        if (reading == null) throw new BusinessException("请先完成这篇文章的阅读");
        return prepareForVersion(userId, textId, reading.getContentVersion());
    }

    private Prepared prepareForVersion(Long userId, Long textId, String selectedVersion) {
        if (textId == null || textId <= 0) throw new BusinessException("文章不存在");
        Text text = textMapper.selectById(textId);
        if (text == null) throw new BusinessException("文章不存在");
        ArticleInsightLevelService.Decision decision = levelService.resolve(userId);
        String version = normalizeVersion(selectedVersion);
        String content = contentFor(text, version);
        if (content == null || content.isBlank()) {
            version = "original";
            content = contentFor(text, version);
        }
        if (content == null || content.isBlank()) return null;
        return new Prepared(textId, version, decision.level(), decision.reason(), content, hash(content));
    }

    private String normalizeVersion(String version) {
        if (version == null) return "original";
        return switch (version.toLowerCase()) {
            case "cet4", "cet6", "academic", "chinese_parallel" -> version.toLowerCase();
            default -> "original";
        };
    }

    private String contentFor(Text text, String version) {
        boolean markdown = "MARKDOWN".equalsIgnoreCase(text.getContentType());
        return switch (version) {
            case "cet4" -> markdown ? text.getCet4MarkdownContent() : text.getCet4Content();
            case "cet6" -> markdown ? text.getCet6MarkdownContent() : text.getCet6Content();
            case "academic" -> markdown ? text.getAcademicMarkdownContent() : text.getAcademicContent();
            case "chinese_parallel" -> markdown ? text.getChineseParallelMarkdownContent() : text.getChineseParallelContent();
            default -> markdown ? text.getMarkdownContent() : text.getContent();
        };
    }

    private String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("文章哈希计算失败", e);
        }
    }

    private ArticleLearningInsight findCached(Prepared prepared) {
        return insightMapper.selectOne(new QueryWrapper<ArticleLearningInsight>()
                .eq("text_id", prepared.textId()).eq("content_version", prepared.version())
                .eq("target_level", prepared.level()).eq("content_hash", prepared.hash())
                .last("LIMIT 1"));
    }

    private void ensureGenerated(Long userId, Prepared prepared, boolean deliverMessage) {
        String flightKey = userId + ":" + prepared.cacheKey();
        if (!inFlight.add(flightKey)) return;
        try {
            executor.execute(() -> {
                try {
                    ArticleLearningInsight cached;
                    Object lock = keyLocks[Math.floorMod(prepared.cacheKey().hashCode(), keyLocks.length)];
                    synchronized (lock) {
                        cached = findCached(prepared);
                        if (cached == null) {
                            List<KnowledgePoint> points = generationService.generate(prepared.content(), prepared.level());
                            cached = new ArticleLearningInsight();
                            cached.setTextId(prepared.textId());
                            cached.setContentVersion(prepared.version());
                            cached.setTargetLevel(prepared.level());
                            cached.setContentHash(prepared.hash());
                            cached.setPointsJson(objectMapper.writeValueAsString(points));
                            cached.setCreatedTime(LocalDateTime.now());
                            try {
                                insightMapper.insert(cached);
                            } catch (DuplicateKeyException race) {
                                cached = findCached(prepared);
                            }
                        }
                    }
                    if (cached != null && deliverMessage) deliver(userId, prepared.textId(), cached.getId());
                    retryAfter.remove(flightKey);
                } catch (Exception e) {
                    retryAfter.put(flightKey, System.currentTimeMillis() + 60_000L);
                    log.warn("小芽知识点生成失败: userId={}, textId={}, reason={}",
                            userId, prepared.textId(), e.getMessage());
                } finally {
                    inFlight.remove(flightKey);
                }
            });
        } catch (RuntimeException rejected) {
            inFlight.remove(flightKey);
            retryAfter.put(flightKey, System.currentTimeMillis() + 60_000L);
            log.warn("小芽知识点任务队列暂不可用: {}", rejected.getMessage());
        }
    }

    private void deliver(Long userId, Long textId, Long insightId) {
        Long count = messageMapper.selectCount(new QueryWrapper<ArticleInsightMessage>()
                .eq("user_id", userId).eq("insight_id", insightId));
        if (count != null && count > 0) return;
        ArticleInsightMessage message = new ArticleInsightMessage();
        message.setUserId(userId);
        message.setTextId(textId);
        message.setInsightId(insightId);
        message.setCreatedTime(LocalDateTime.now());
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException ignored) {
            // 与另一次完成阅读并发时只保留一条个人消息。
        }
    }

    private List<KnowledgePoint> readPoints(ArticleLearningInsight insight) {
        try {
            return objectMapper.readValue(insight.getPointsJson(), new TypeReference<List<KnowledgePoint>>() { });
        } catch (Exception e) {
            throw new BusinessException("已保存的文章知识点暂时无法读取");
        }
    }

    private record Prepared(Long textId, String version, String level, String levelReason,
                            String content, String hash) {
        private String cacheKey() { return textId + ":" + version + ":" + level + ":" + hash; }
    }
}
