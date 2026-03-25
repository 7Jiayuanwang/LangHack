package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.english.entity.UnitWord;
import com.english.entity.VocabularyBook;
import com.english.entity.Word;
import com.english.entity.Unit;
import com.english.mapper.VocabularyBookMapper;
import com.english.mapper.WordMapper;
import com.english.mapper.UnitMapper;
import com.english.mapper.UnitWordMapper;
import com.english.service.VocabularyBooksService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生词本服务实现类
 */
@Service
public class VocabularyBooksServiceImpl implements VocabularyBooksService {

    private static final Logger logger = LoggerFactory.getLogger(VocabularyBooksServiceImpl.class);

    // SM-2 参数边界值
    private static final double MIN_EF = 1.3;
    private static final double MAX_EF = 5.0;

    @Autowired
    private VocabularyBookMapper vocabularyBookMapper;

    @Autowired
    private WordMapper wordMapper;

    @Autowired
    private UnitMapper unitMapper;

    @Autowired
    private UnitWordMapper unitWordMapper;

    /**
     * 私有方法：过滤已删除的单词
     */
    private List<Word> filterNotDeleted(List<Word> words) {
        if (words == null) {
            return Collections.emptyList();
        }
        return words.stream()
                .filter(w -> w.getDeleted() == null || w.getDeleted() == 0)
                .collect(Collectors.toList());
    }

    @Override
    public int countByStudentId(Long studentId) {
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);
        return Math.toIntExact(vocabularyBookMapper.selectCount(wrapper));
    }

    @Override
    public List<Map<String, Object>> getRecentWords(Long studentId, int limit) {
        // 边界校验防止负数或过大值
        int safeLimit = Math.max(1, Math.min(limit, 100));

        Page<VocabularyBook> pageParam = new Page<>(1, safeLimit);
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .orderByDesc("added_time");

        IPage<VocabularyBook> pageResult = vocabularyBookMapper.selectPage(pageParam, wrapper);
        List<VocabularyBook> vocabularyBooks = pageResult.getRecords();
        if (vocabularyBooks == null || vocabularyBooks.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch fetch all words in one query instead of N individual queries
        List<Long> wordIds = vocabularyBooks.stream()
                .map(VocabularyBook::getWordId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Word> selectedWords = wordMapper.selectBatchIds(wordIds);
        Map<Long, Word> wordById = filterNotDeleted(selectedWords).stream()
                .collect(Collectors.toMap(Word::getId, w -> w, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (VocabularyBook vb : vocabularyBooks) {
            Word word = wordById.get(vb.getWordId());
            if (word == null)
                continue;

            Map<String, Object> wordMap = new HashMap<>();
            wordMap.put("id", vb.getId());
            wordMap.put("wordId", word.getId());
            wordMap.put("word", word.getWord());
            wordMap.put("pronunciation", word.getPronunciation());
            wordMap.put("definition", word.getDefinition());
            wordMap.put("audioUrl", word.getAudioUrl());
            wordMap.put("masteryLevel", vb.getMasteryLevel());
            wordMap.put("reviewCount", vb.getReviewCount());
            wordMap.put("addedTime", vb.getAddedTime());
            wordMap.put("lastReviewed", vb.getLastReviewed());

            result.add(wordMap);
        }

        return result;
    }

    @Override
    public boolean existsByStudentAndWord(Long studentId, Long wordId) {
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .eq("word_id", wordId);
        return vocabularyBookMapper.selectCount(wrapper) > 0;
    }

    @Override
    @Transactional
    public void addToVocabulary(Long studentId, Long wordId) {
        VocabularyBook vocabularyBook = new VocabularyBook();
        vocabularyBook.setStudentId(studentId);
        vocabularyBook.setWordId(wordId);
        vocabularyBook.setMasteryLevel("NEW");
        vocabularyBook.setReviewCount(0);
        vocabularyBook.setAddedTime(LocalDateTime.now());

        vocabularyBookMapper.insert(vocabularyBook);
    }

    @Override
    @Transactional
    public void addToVocabularyWithLevel(Long studentId, Long wordId, String masteryLevel) {
        VocabularyBook vocabularyBook = new VocabularyBook();
        vocabularyBook.setStudentId(studentId);
        vocabularyBook.setWordId(wordId);
        vocabularyBook.setMasteryLevel(masteryLevel);
        vocabularyBook.setReviewCount(0);
        vocabularyBook.setAddedTime(LocalDateTime.now());

        vocabularyBookMapper.insert(vocabularyBook);
    }

    @Override
    public Map<String, Object> getVocabularyWithPagination(Map<String, Object> params) {
        Long studentId = params.get("studentId") != null ? Long.valueOf(params.get("studentId").toString()) : null;
        int page = params.get("page") != null ? ((Number) params.get("page")).intValue() : 1;
        int size = params.get("size") != null ? ((Number) params.get("size")).intValue() : 10;
        // Boundary validation to prevent negative offset/limit
        page = Math.max(1, page);
        size = Math.max(1, Math.min(size, 100));
        String keyword = (String) params.get("keyword");
        String masteryLevel = (String) params.get("masteryLevel");
        String sortBy = (String) params.get("sortBy");
        String difficulty = (String) params.get("difficulty");
        String courseId = (String) params.get("courseId");

        // 构建查询条件
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        if (studentId == null) {
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put("records", new ArrayList<>());
            emptyResult.put("total", 0);
            emptyResult.put("size", size);
            emptyResult.put("current", page);
            emptyResult.put("pages", 0);
            return emptyResult;
        }
        wrapper.eq("student_id", studentId);

        if (masteryLevel != null && !masteryLevel.isEmpty()) {
            wrapper.eq("mastery_level", masteryLevel);
        }

        // 排序：支持多种排序方式，默认按添加时间降序
        if ("reviewCount".equals(sortBy)) {
            wrapper.orderByDesc("review_count");
        } else if ("word".equals(sortBy)) {
            // 按字母排序需要在后处理中完成（word 列在另一张表）
            wrapper.orderByDesc("added_time");
        } else if ("nextReviewTime".equals(sortBy)) {
            // 艾宾浩斯遗忘曲线调度：到期的词优先（NULL视为最高优先级）
            wrapper.orderByAsc("next_review_time");
        } else {
            // 默认：按添加时间降序
            wrapper.orderByDesc("added_time");
        }

        // 分页查询生词本记录
        Page<VocabularyBook> pageParam = new Page<>(page, size);
        Page<VocabularyBook> result = vocabularyBookMapper.selectPage(pageParam, wrapper);

        List<VocabularyBook> pageRecords = result.getRecords();
        List<Map<String, Object>> records = new ArrayList<>();

        if (!pageRecords.isEmpty()) {
            // Batch fetch all words in one query instead of N individual queries
            List<Long> pageWordIds = pageRecords.stream()
                    .map(VocabularyBook::getWordId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<Word> selectedWords = wordMapper.selectBatchIds(pageWordIds);
            Map<Long, Word> wordById = filterNotDeleted(selectedWords).stream()
                    .collect(Collectors.toMap(Word::getId, w -> w, (a, b) -> a));

            // If courseId filter is active, build a Set of word IDs that belong to the course
            // using batch queries: 1 query for UnitWords + 1 query for Units
            Set<Long> wordIdsInCourse = null;
            if (courseId != null && !courseId.isEmpty()) {
                Long parsedCourseId;
                try {
                    parsedCourseId = Long.parseLong(courseId);
                    // 验证 courseId 有效性
                    if (parsedCourseId <= 0) {
                        logger.warn("Invalid courseId: {} (must be positive)", courseId);
                        parsedCourseId = null;
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid courseId format: {}, skipping course filter", courseId);
                    parsedCourseId = null;
                }

                if (parsedCourseId != null && !pageWordIds.isEmpty()) {
                    // One query: all UnitWord entries for these words
                    QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
                    unitWordWrapper.in("word_id", pageWordIds);
                    List<UnitWord> allUnitWords = unitWordMapper.selectList(unitWordWrapper);

                    if (allUnitWords != null && !allUnitWords.isEmpty()) {
                        List<Long> unitIds = allUnitWords.stream()
                                .map(UnitWord::getUnitId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .collect(Collectors.toList());

                        // One query: fetch all relevant units
                        List<Unit> selectedUnits = unitMapper.selectBatchIds(unitIds);
                        Map<Long, Unit> unitById = (selectedUnits != null ? selectedUnits : Collections.<Unit>emptyList()).stream()
                                .collect(Collectors.toMap(Unit::getId, u -> u, (a, b) -> a));

                        // Determine which words belong to the requested course
                        final Long finalParsedCourseId = parsedCourseId;
                        wordIdsInCourse = allUnitWords.stream()
                                .filter(uw -> {
                                    Unit unit = unitById.get(uw.getUnitId());
                                    return unit != null && finalParsedCourseId.equals(unit.getCourseId());
                                })
                                .map(UnitWord::getWordId)
                                .collect(Collectors.toSet());
                    } else {
                        wordIdsInCourse = Collections.emptySet();
                    }
                } else {
                    // parsedCourseId 无效或 pageWordIds 为空，跳过课程过滤
                    wordIdsInCourse = null;
                }
            }

            final Set<Long> finalWordIdsInCourse = wordIdsInCourse;
            for (VocabularyBook vb : pageRecords) {
                Word word = wordById.get(vb.getWordId());
                if (word == null)
                    continue;

                // Apply courseId filter if active
                if (finalWordIdsInCourse != null && !finalWordIdsInCourse.contains(word.getId())) {
                    continue;
                }

                Map<String, Object> wordMap = new HashMap<>();
                wordMap.put("id", vb.getId());
                wordMap.put("wordId", word.getId());
                wordMap.put("word", word.getWord());
                wordMap.put("pronunciation", word.getPronunciation());
                wordMap.put("definition", word.getDefinition());
                wordMap.put("exampleSentence", word.getExampleSentence());
                wordMap.put("audioUrl", word.getAudioUrl());
                wordMap.put("partOfSpeech", word.getPartOfSpeech());
                wordMap.put("difficulty", word.getDifficulty());
                wordMap.put("masteryLevel", vb.getMasteryLevel());
                wordMap.put("reviewCount", vb.getReviewCount());
                wordMap.put("addedTime", vb.getAddedTime());
                wordMap.put("lastReviewed", vb.getLastReviewed());
                wordMap.put("repetition", vb.getRepetition());
                wordMap.put("interval", vb.getIntervalDays());
                wordMap.put("easinessFactor", vb.getEasinessFactor());
                wordMap.put("nextReviewTime", vb.getNextReviewTime());

                records.add(wordMap);
            }
        }

        Map<String, Object> paginationResult = new HashMap<>();
        paginationResult.put("records", records);
        paginationResult.put("total", result.getTotal());
        paginationResult.put("size", result.getSize());
        paginationResult.put("current", result.getCurrent());
        paginationResult.put("pages", result.getPages());

        return paginationResult;
    }

    @Override
    @Transactional
    public void updateMasteryLevel(Long studentId, Long wordId, String masteryLevel, Map<String, Object> sm2Params) {
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .eq("word_id", wordId);

        VocabularyBook vocabularyBook = vocabularyBookMapper.selectOne(wrapper);
        if (vocabularyBook != null) {
            vocabularyBook.setMasteryLevel(masteryLevel);
            vocabularyBook.setReviewCount((vocabularyBook.getReviewCount() != null ? vocabularyBook.getReviewCount() : 0) + 1);
            vocabularyBook.setLastReviewed(LocalDateTime.now());

            // 更新 SM-2 参数
            if (sm2Params != null) {
                if (sm2Params.containsKey("repetition")) {
                    Object rep = sm2Params.get("repetition");
                    if (rep instanceof Number) {
                        int repetition = Math.max(0, ((Number) rep).intValue());
                        vocabularyBook.setRepetition(repetition);
                    }
                }
                if (sm2Params.containsKey("interval")) {
                    Object intv = sm2Params.get("interval");
                    if (intv instanceof Number) {
                        int intervalDays = Math.max(0, ((Number) intv).intValue());
                        vocabularyBook.setIntervalDays(intervalDays);
                    }
                }
                if (sm2Params.containsKey("easinessFactor")) {
                    Object ef = sm2Params.get("easinessFactor");
                    if (ef instanceof Number) {
                        double easinessFactor = Math.max(MIN_EF, Math.min(((Number) ef).doubleValue(), MAX_EF));
                        vocabularyBook.setEasinessFactor(easinessFactor);
                    }
                }
                if (sm2Params.containsKey("nextReviewTime")) {
                    Object nrtObj = sm2Params.get("nextReviewTime");
                    if (nrtObj != null) {
                        try {
                            String timeStr = nrtObj.toString().replace("Z", "").replace("[UTC]", "");
                            vocabularyBook.setNextReviewTime(LocalDateTime.parse(timeStr));
                        } catch (java.time.format.DateTimeParseException e) {
                            logger.warn("Invalid nextReviewTime format: {}, skipping", nrtObj);
                        } catch (Exception e) {
                            logger.warn("Error parsing nextReviewTime: {}, skipping", nrtObj, e);
                        }
                    }
                }
            }

            vocabularyBookMapper.updateById(vocabularyBook);
        }
    }

    @Override
    public Map<String, Object> getWordDetailWithLearningState(Long studentId, Long wordId) {
        Word word = wordMapper.selectById(wordId);
        if (word == null || (word.getDeleted() != null && word.getDeleted() == 1)) {
            return Collections.emptyMap();
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("id", word.getId());
        detail.put("word", word.getWord());
        detail.put("pronunciation", word.getPronunciation());
        detail.put("definition", word.getDefinition());
        detail.put("exampleSentence", word.getExampleSentence());
        detail.put("audioUrl", word.getAudioUrl());
        detail.put("partOfSpeech", word.getPartOfSpeech());
        detail.put("difficulty", word.getDifficulty());

        // 查询生词本状态
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .eq("word_id", wordId);
        VocabularyBook vb = vocabularyBookMapper.selectOne(wrapper);

        if (vb != null) {
            detail.put("inVocabulary", true);
            detail.put("masteryLevel", vb.getMasteryLevel());
            detail.put("reviewCount", vb.getReviewCount());
            detail.put("addedTime", vb.getAddedTime());
            detail.put("lastReviewed", vb.getLastReviewed());
            detail.put("repetition", vb.getRepetition());
            detail.put("interval", vb.getIntervalDays());
            detail.put("easinessFactor", vb.getEasinessFactor());
            detail.put("nextReviewTime", vb.getNextReviewTime());
        } else {
            detail.put("inVocabulary", false);
            detail.put("masteryLevel", null);
            detail.put("reviewCount", 0);
        }

        return detail;
    }

    @Override
    public Map<String, Object> createStudySession(Long studentId, Long unitId) {
        // 查询单元下的所有单词
        QueryWrapper<UnitWord> wrapper = new QueryWrapper<>();
        wrapper.eq("unit_id", unitId);
        List<UnitWord> unitWords = unitWordMapper.selectList(wrapper);

        // Null safety check
        if (unitWords == null) {
            unitWords = Collections.emptyList();
        }

        // 获取单词ID列表
        List<Long> wordIds = unitWords.stream()
                .map(UnitWord::getWordId)
                .collect(Collectors.toList());

        // 防止空集合导致 IN 子句 SQL 异常
        if (wordIds.isEmpty()) {
            Map<String, Object> session = new HashMap<>();
            session.put("sessionId", UUID.randomUUID().toString());
            session.put("unitId", unitId);
            session.put("totalWords", 0);
            session.put("words", Collections.emptyList());
            return session;
        }

        // 过滤已掌握的单词
        QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
        vocabWrapper.eq("student_id", studentId)
                .eq("mastery_level", "MASTERED")
                .in("word_id", wordIds);
        List<VocabularyBook> masteredWords = vocabularyBookMapper.selectList(vocabWrapper);

        Set<Long> masteredWordIds = (masteredWords != null ? masteredWords : Collections.<VocabularyBook>emptyList()).stream()
                .map(VocabularyBook::getWordId)
                .collect(Collectors.toSet());

        // Collect the IDs of words that are not yet mastered
        List<Long> studyWordIds = unitWords.stream()
                .map(UnitWord::getWordId)
                .filter(wid -> wid != null && !masteredWordIds.contains(wid))
                .collect(Collectors.toList());

        // Batch fetch all non-mastered words in one query instead of N individual queries
        List<Map<String, Object>> studyWords = new ArrayList<>();
        if (!studyWordIds.isEmpty()) {
            List<Word> selectedWords = wordMapper.selectBatchIds(studyWordIds);
            Map<Long, Word> wordById = filterNotDeleted(selectedWords).stream()
                    .collect(Collectors.toMap(Word::getId, w -> w, (a, b) -> a));

            for (Long wid : studyWordIds) {
                Word word = wordById.get(wid);
                if (word != null) {
                    Map<String, Object> wordMap = new HashMap<>();
                    wordMap.put("id", word.getId());
                    wordMap.put("word", word.getWord());
                    wordMap.put("pronunciation", word.getPronunciation());
                    wordMap.put("definition", word.getDefinition());
                    wordMap.put("exampleSentence", word.getExampleSentence());
                    wordMap.put("audioUrl", word.getAudioUrl());
                    studyWords.add(wordMap);
                }
            }
        }

        // 创建学习会话
        Map<String, Object> session = new HashMap<>();
        session.put("sessionId", UUID.randomUUID().toString());
        session.put("unitId", unitId);
        session.put("totalWords", studyWords.size());
        session.put("words", studyWords);

        return session;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public void processStudyResults(Long studentId, Map<String, Object> results) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> studyResults = results.get("results") instanceof List
                ? (List<Map<String, Object>>) results.get("results")
                : null;

        if (studyResults == null) {
            return;
        }

        // 限制批量大小，防止 DoS
        if (studyResults.size() > 200) {
            throw new com.english.exception.BusinessException("单次提交学习结果不能超过200条");
        }

        for (Map<String, Object> result : studyResults) {
            Object wordIdObj = result.get("wordId");
            if (wordIdObj == null) {
                continue;
            }
            Long wordId;
            try {
                wordId = Long.valueOf(wordIdObj.toString());
            } catch (NumberFormatException e) {
                logger.warn("Invalid wordId format: {}, skipping", wordIdObj);
                continue;
            }
            String outcome = result.get("outcome") != null ? result.get("outcome").toString() : null; // KNOWN, UNCERTAIN, UNKNOWN

            // 检查是否已在生词本中
            QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
            wrapper.eq("student_id", studentId)
                    .eq("word_id", wordId);
            VocabularyBook vb = vocabularyBookMapper.selectOne(wrapper);

            if (vb == null) {
                // 添加到生词本
                vb = new VocabularyBook();
                vb.setStudentId(studentId);
                vb.setWordId(wordId);
                vb.setAddedTime(LocalDateTime.now());
                vb.setReviewCount(1);
            } else {
                vb.setReviewCount((vb.getReviewCount() != null ? vb.getReviewCount() : 0) + 1);
            }

            // 更新掌握程度
            if ("KNOWN".equals(outcome)) {
                vb.setMasteryLevel("MASTERED");
            } else {
                vb.setMasteryLevel("LEARNING");
            }
            vb.setLastReviewed(LocalDateTime.now());

            if (vb.getId() == null) {
                try {
                    vocabularyBookMapper.insert(vb);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    // 并发竞态：另一个线程已插入，回退为更新
                    logger.info("生词本并发竞态：student={}, word={}, 回退为更新", studentId, wordId);
                    QueryWrapper<VocabularyBook> retryWrapper = new QueryWrapper<>();
                    retryWrapper.eq("student_id", studentId)
                            .eq("word_id", wordId);
                    VocabularyBook existing = vocabularyBookMapper.selectOne(retryWrapper);
                    if (existing != null) {
                        existing.setMasteryLevel(vb.getMasteryLevel());
                        existing.setReviewCount((existing.getReviewCount() != null ? existing.getReviewCount() : 0) + 1);
                        existing.setLastReviewed(vb.getLastReviewed());
                        vocabularyBookMapper.updateById(existing);
                    }
                }
            } else {
                vocabularyBookMapper.updateById(vb);
            }
        }
    }

    @Override
    public Map<String, Object> getWordStudyStats(Long studentId, String period, Long courseId, Long unitId) {
        Map<String, Object> stats = new HashMap<>();

        // 统计生词本总数
        QueryWrapper<VocabularyBook> totalWrapper = new QueryWrapper<>();
        totalWrapper.eq("student_id", studentId);
        stats.put("totalWords", vocabularyBookMapper.selectCount(totalWrapper));

        // 按掌握程度统计
        QueryWrapper<VocabularyBook> newWrapper = new QueryWrapper<>();
        newWrapper.eq("student_id", studentId).eq("mastery_level", "NEW");
        stats.put("newWords", vocabularyBookMapper.selectCount(newWrapper));

        QueryWrapper<VocabularyBook> learningWrapper = new QueryWrapper<>();
        learningWrapper.eq("student_id", studentId).eq("mastery_level", "LEARNING");
        stats.put("learningWords", vocabularyBookMapper.selectCount(learningWrapper));

        QueryWrapper<VocabularyBook> masteredWrapper = new QueryWrapper<>();
        masteredWrapper.eq("student_id", studentId).eq("mastery_level", "MASTERED");
        stats.put("masteredWords", vocabularyBookMapper.selectCount(masteredWrapper));

        return stats;
    }

    @Override
    public Map<String, Object> getWordStudyStats(Long studentId, String period) {
        // 调用完整版本的方法，courseId和unitId传null
        return getWordStudyStats(studentId, period, null, null);
    }

    @Override
    @Transactional
    public void batchDeleteVocabulary(Long studentId, List<Long> wordIds) {
        // 防止空集合导致的SQL语法错误
        if (wordIds == null || wordIds.isEmpty()) {
            return;
        }
        if (studentId == null) {
            return;
        }

        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .in("word_id", wordIds);
        vocabularyBookMapper.delete(wrapper);
    }

    @Override
    @Transactional
    public void batchUpdateMasteryLevel(Long studentId, List<Long> wordIds, String masteryLevel) {
        // 防止空集合导致的SQL语法错误
        if (wordIds == null || wordIds.isEmpty()) {
            return;
        }
        if (studentId == null) {
            return;
        }

        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .in("word_id", wordIds);

        VocabularyBook updateEntity = new VocabularyBook();
        updateEntity.setMasteryLevel(masteryLevel);
        updateEntity.setLastReviewed(LocalDateTime.now());

        vocabularyBookMapper.update(updateEntity, wrapper);
    }

    @Override
    @Transactional
    public void removeFromVocabulary(Long studentId, Long wordId) {
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .eq("word_id", wordId);
        vocabularyBookMapper.delete(wrapper);
    }

    @Override
    public Map<String, Object> getVocabularyStats(Long studentId) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 统计生词本总数
            QueryWrapper<VocabularyBook> totalWrapper = new QueryWrapper<>();
            totalWrapper.eq("student_id", studentId);
            long totalCount = vocabularyBookMapper.selectCount(totalWrapper);
            stats.put("totalVocabulary", totalCount);

            // 按掌握程度统计
            QueryWrapper<VocabularyBook> newWrapper = new QueryWrapper<>();
            newWrapper.eq("student_id", studentId).eq("mastery_level", "NEW");
            long newCount = vocabularyBookMapper.selectCount(newWrapper);
            stats.put("newCount", newCount);

            QueryWrapper<VocabularyBook> learningWrapper = new QueryWrapper<>();
            learningWrapper.eq("student_id", studentId).eq("mastery_level", "LEARNING");
            long learningCount = vocabularyBookMapper.selectCount(learningWrapper);
            stats.put("learningCount", learningCount);

            QueryWrapper<VocabularyBook> masteredWrapper = new QueryWrapper<>();
            masteredWrapper.eq("student_id", studentId).eq("mastery_level", "MASTERED");
            long masteredCount = vocabularyBookMapper.selectCount(masteredWrapper);
            stats.put("masteredCount", masteredCount);

            // 计算掌握率
            if (totalCount > 0) {
                double masteryRate = (double) masteredCount / totalCount * 100;
                stats.put("masteryRate", Math.round(masteryRate * 100.0) / 100.0);
            } else {
                stats.put("masteryRate", 0.0);
            }

            // 今日添加的单词数量
            QueryWrapper<VocabularyBook> todayWrapper = new QueryWrapper<>();
            todayWrapper.eq("student_id", studentId)
                    .ge("added_time", LocalDateTime.now().toLocalDate().atStartOfDay());
            long todayCount = vocabularyBookMapper.selectCount(todayWrapper);
            stats.put("todayAdded", todayCount);

            // 本周复习次数
            QueryWrapper<VocabularyBook> reviewWrapper = new QueryWrapper<>();
            reviewWrapper.eq("student_id", studentId)
                    .ge("last_reviewed", LocalDateTime.now().minusDays(7))
                    .gt("review_count", 0);
            long reviewCount = vocabularyBookMapper.selectCount(reviewWrapper);
            stats.put("weeklyReviews", reviewCount);

        } catch (Exception e) {
            logger.error("获取生词本统计失败", e);
            // 返回默认值
            stats.put("totalVocabulary", 0);
            stats.put("newCount", 0);
            stats.put("learningCount", 0);
            stats.put("masteredCount", 0);
            stats.put("masteryRate", 0.0);
            stats.put("todayAdded", 0);
            stats.put("weeklyReviews", 0);
        }

        return stats;
    }
}