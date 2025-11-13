package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生词本服务实现类
 */
@Service
public class VocabularyBooksServiceImpl implements VocabularyBooksService {

    @Autowired
    private VocabularyBookMapper vocabularyBookMapper;

    @Autowired
    private WordMapper wordMapper;

    @Autowired
    private UnitMapper unitMapper;

    @Autowired
    private UnitWordMapper unitWordMapper;

    @Override
    public int countByStudentId(Long studentId) {
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);
        return Math.toIntExact(vocabularyBookMapper.selectCount(wrapper));
    }

    @Override
    public List<Map<String, Object>> getRecentWords(Long studentId, int limit) {
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
              .orderByDesc("added_time")
              .last("LIMIT " + limit);

        List<VocabularyBook> vocabularyBooks = vocabularyBookMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();

        for (VocabularyBook vb : vocabularyBooks) {
            Word word = wordMapper.selectById(vb.getWordId());
            if (word == null) continue;

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
    public Map<String, Object> getVocabularyWithPagination(Map<String, Object> params) {
        Long studentId = (Long) params.get("studentId");
        Integer page = (Integer) params.get("page");
        Integer size = (Integer) params.get("size");
        String keyword = (String) params.get("keyword");
        String masteryLevel = (String) params.get("masteryLevel");
        String sortBy = (String) params.get("sortBy");
        String difficulty = (String) params.get("difficulty");

        // 构建查询条件
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);

        if (masteryLevel != null && !masteryLevel.isEmpty()) {
            wrapper.eq("mastery_level", masteryLevel);
        }

        // 分页查询
        Page<VocabularyBook> pageParam = new Page<>(page, size);
        Page<VocabularyBook> result = vocabularyBookMapper.selectPage(pageParam, wrapper);

        List<Map<String, Object>> records = new ArrayList<>();
        for (VocabularyBook vb : result.getRecords()) {
            Word word = wordMapper.selectById(vb.getWordId());
            if (word == null) continue;

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

            records.add(wordMap);
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
    public void updateMasteryLevel(Long studentId, Long wordId, String masteryLevel) {
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
              .eq("word_id", wordId);

        VocabularyBook vocabularyBook = vocabularyBookMapper.selectOne(wrapper);
        if (vocabularyBook != null) {
            vocabularyBook.setMasteryLevel(masteryLevel);
            vocabularyBook.setReviewCount(vocabularyBook.getReviewCount() + 1);
            vocabularyBook.setLastReviewed(LocalDateTime.now());
            vocabularyBookMapper.updateById(vocabularyBook);
        }
    }

    @Override
    public Map<String, Object> getWordDetailWithLearningState(Long studentId, Long wordId) {
        Word word = wordMapper.selectById(wordId);
        if (word == null) {
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

        // 获取单词ID列表
        List<Long> wordIds = unitWords.stream()
                .map(UnitWord::getWordId)
                .collect(Collectors.toList());

        // 过滤已掌握的单词
        QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
        vocabWrapper.eq("student_id", studentId)
                    .eq("mastery_level", "MASTERED")
                    .in("word_id", wordIds);
        List<VocabularyBook> masteredWords = vocabularyBookMapper.selectList(vocabWrapper);

        Set<Long> masteredWordIds = masteredWords.stream()
                .map(VocabularyBook::getWordId)
                .collect(Collectors.toSet());

        // 构建学习单词列表
        List<Map<String, Object>> studyWords = new ArrayList<>();
        for (UnitWord uw : unitWords) {
            if (!masteredWordIds.contains(uw.getWordId())) {
                Word word = wordMapper.selectById(uw.getWordId());
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
    public void processStudyResults(Long studentId, Map<String, Object> results) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> studyResults = (List<Map<String, Object>>) results.get("results");

        for (Map<String, Object> result : studyResults) {
            Long wordId = Long.valueOf(result.get("wordId").toString());
            String outcome = (String) result.get("outcome"); // KNOWN, UNCERTAIN, UNKNOWN

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
                vb.setReviewCount(vb.getReviewCount() + 1);
            }

            // 更新掌握程度
            if ("KNOWN".equals(outcome)) {
                vb.setMasteryLevel("MASTERED");
            } else {
                vb.setMasteryLevel("LEARNING");
            }
            vb.setLastReviewed(LocalDateTime.now());

            if (vb.getId() == null) {
                vocabularyBookMapper.insert(vb);
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
    public void batchDeleteVocabulary(Long studentId, List<Long> wordIds) {
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
              .in("word_id", wordIds);
        vocabularyBookMapper.delete(wrapper);
    }

    @Override
    public void batchUpdateMasteryLevel(Long studentId, List<Long> wordIds, String masteryLevel) {
        QueryWrapper<VocabularyBook> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
              .in("word_id", wordIds);

        VocabularyBook updateEntity = new VocabularyBook();
        updateEntity.setMasteryLevel(masteryLevel);
        updateEntity.setLastReviewed(LocalDateTime.now());

        vocabularyBookMapper.update(updateEntity, wrapper);
    }
}