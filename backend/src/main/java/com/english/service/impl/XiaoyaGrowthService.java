package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.datasource.UseMaster;
import com.english.dto.learner.XiaoyaExternalLearningPageDto;
import com.english.dto.learner.XiaoyaExternalLearningRequest;
import com.english.dto.learner.XiaoyaGrowthDto;
import com.english.entity.XiaoyaExternalLearning;
import com.english.exception.BusinessException;
import com.english.mapper.XiaoyaGrowthMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 学习行为驱动的小芽成长。只向学生本人返回站外英文收获，不提供教师明细接口。 */
@Service
@RequiredArgsConstructor
public class XiaoyaGrowthService {
    private static final Set<String> SOURCE_TYPES = Set.of("ARTICLE", "VIDEO", "OTHER");
    private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z]+(?:['’-][A-Za-z]+)?");
    private static final int MAX_NOTES_PER_DAY = 3;

    private final XiaoyaGrowthMapper mapper;

    @UseMaster
    @Transactional(readOnly = true)
    public XiaoyaGrowthDto forUser(Long userId) {
        return buildGrowth(userId, true);
    }

    /** 全站悬浮入口只需要成长阶段，不读取学生写下的私人英文收获。 */
    @UseMaster
    @Transactional(readOnly = true)
    public XiaoyaGrowthDto summaryForUser(Long userId) {
        return buildGrowth(userId, false);
    }

    private XiaoyaGrowthDto buildGrowth(Long userId, boolean includeNotes) {
        LocalDate today = LocalDate.now();
        int daysAll = mapper.countMeaningfulDaysSince(userId, LocalDate.of(2000, 1, 1).atStartOfDay());
        int days7d = mapper.countMeaningfulDaysSince(userId, today.minusDays(6).atStartOfDay());
        long articles = mapper.countCompletedArticles(userId);
        long practices = mapper.countPracticedArticles(userId);
        long externalDays = mapper.countExternalDays(userId);
        long rawPoints = 2L * daysAll + articles + practices + externalDays;
        int points = (int) Math.min(Integer.MAX_VALUE, rawPoints);

        XiaoyaGrowthDto result = new XiaoyaGrowthDto();
        result.setGrowthPoints(points);
        result.setMeaningfulDaysAll(daysAll);
        result.setMeaningfulDays7d(days7d);
        result.setCompletedArticles(articles);
        result.setPracticedArticles(practices);
        result.setExternalDays(externalDays);
        result.setTodayExternalCount(mapper.countExternalNotesOnDay(userId, today));

        if (points >= 80) {
            result.setStage("BLOOM");
            result.setStageLabel("开花的小芽");
        } else if (points >= 30) {
            result.setStage("LEAF");
            result.setStageLabel("舒展的小芽");
            result.setNextStageAt(80);
        } else if (points >= 10) {
            result.setStage("SPROUT");
            result.setStageLabel("长高的小芽");
            result.setNextStageAt(30);
        } else {
            result.setStage("SEED");
            result.setStageLabel("刚发芽的小芽");
            result.setNextStageAt(10);
        }

        result.setEncouragement(days7d >= 3
                ? "这一周你已经多次用英语获取或表达信息，小芽正在和你一起成长。"
                : "今天有空时读一点、练一题，或用一句英文记下收获；休息几天也不会让小芽退步。");

        if (includeNotes) {
            List<XiaoyaExternalLearning> notes = mapper.selectList(new QueryWrapper<XiaoyaExternalLearning>()
                    .eq("user_id", userId).orderByDesc("created_time", "id").last("LIMIT 5"));
            if (notes != null) {
                for (XiaoyaExternalLearning note : notes) {
                    result.getRecentExternalLearning().add(toExternalLearning(note));
                }
            }
        }
        return result;
    }

    private XiaoyaGrowthDto.ExternalLearning toExternalLearning(XiaoyaExternalLearning note) {
        XiaoyaGrowthDto.ExternalLearning item = new XiaoyaGrowthDto.ExternalLearning();
        item.setId(note.getId());
        item.setLearningDay(note.getLearningDay());
        item.setSourceType(note.getSourceType());
        item.setSourceTitle(note.getSourceTitle());
        item.setTakeawayEn(note.getTakeawayEn());
        return item;
    }

    @UseMaster
    @Transactional(readOnly = true)
    public XiaoyaExternalLearningPageDto listExternalLearning(Long userId, int requestedPage) {
        int pageNumber = Math.max(1, requestedPage);
        Page<XiaoyaExternalLearning> page = mapper.selectPage(new Page<>(pageNumber, 10),
                new QueryWrapper<XiaoyaExternalLearning>().eq("user_id", userId)
                        .orderByDesc("created_time", "id"));
        XiaoyaExternalLearningPageDto result = new XiaoyaExternalLearningPageDto();
        result.setPage(pageNumber);
        result.setSize(10);
        result.setTotal(page.getTotal());
        for (XiaoyaExternalLearning note : page.getRecords()) {
            result.getItems().add(toExternalLearning(note));
        }
        return result;
    }

    @UseMaster
    @Transactional
    public XiaoyaGrowthDto addExternalLearning(Long userId, XiaoyaExternalLearningRequest request) {
        if (request == null) throw new BusinessException("请填写学习收获");
        String sourceType = request.getSourceType() == null ? "" : request.getSourceType().trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_TYPES.contains(sourceType)) throw new BusinessException("请选择文章、视频或其他英文内容");
        String title = request.getSourceTitle() == null ? "" : request.getSourceTitle().trim();
        if (title.length() > 120) throw new BusinessException("内容标题不能超过 120 字");
        String takeaway = request.getTakeawayEn() == null ? "" : request.getTakeawayEn().trim();
        if (takeaway.length() < 20 || takeaway.length() > 500 || countEnglishWords(takeaway) < 5) {
            throw new BusinessException("请用至少 5 个英文单词写下具体收获，长度 20–500 字");
        }

        LocalDate today = LocalDate.now();
        // Serialize submissions for one learner, so the daily limit survives concurrent requests.
        mapper.lockUserForExternalLearning(userId);
        if (mapper.countExternalNotesOnDay(userId, today) >= MAX_NOTES_PER_DAY) {
            throw new BusinessException("今天已记录 3 条站外学习收获，明天再继续吧");
        }

        XiaoyaExternalLearning note = new XiaoyaExternalLearning();
        note.setUserId(userId);
        note.setLearningDay(today);
        note.setSourceType(sourceType);
        note.setSourceTitle(title.isEmpty() ? null : title);
        note.setTakeawayEn(takeaway);
        note.setTakeawayHash(sha256(takeaway.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)));
        note.setCreatedTime(LocalDateTime.now());
        try {
            mapper.insert(note);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("今天已经记录过相同的英文收获", e);
        }
        return forUser(userId);
    }

    @UseMaster
    @Transactional
    public XiaoyaGrowthDto deleteExternalLearning(Long userId, Long noteId) {
        XiaoyaExternalLearning note = noteId == null ? null : mapper.selectOne(
                new QueryWrapper<XiaoyaExternalLearning>().eq("id", noteId).eq("user_id", userId));
        if (note == null) throw new BusinessException("这条学习收获不存在或不属于你");
        mapper.deleteById(noteId);
        return forUser(userId);
    }

    private int countEnglishWords(String text) {
        Matcher matcher = ENGLISH_WORD.matcher(text);
        int count = 0;
        while (matcher.find() && count < 5) count++;
        return count;
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
