package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.LearningRecordsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习记录服务实现类
 */
@Service
public class LearningRecordsServiceImpl implements LearningRecordsService {

    @Autowired
    private LearningRecordMapper learningRecordMapper;

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private UnitMapper unitMapper;

    @Autowired
    private WordMapper wordMapper;

    @Autowired
    private UnitWordMapper unitWordMapper;

    @Autowired
    private VocabularyBookMapper vocabularyBookMapper;

    @Override
    public int getTodayStudyTime(Long studentId) {
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
              .ge("created_time", LocalDateTime.of(LocalDate.now(), LocalTime.MIN))
              .select("COALESCE(SUM(duration_seconds), 0) as total_time");

        Map<String, Object> result = learningRecordMapper.selectMaps(wrapper).get(0);
        Number totalTime = (Number) result.get("total_time");
        return totalTime != null ? totalTime.intValue() / 60 : 0; // 转换为分钟
    }

    @Override
    public List<Map<String, Object>> getUnitsWithLearningStats(Long studentId, Long courseId) {
        // 查询课程下的所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId).eq("deleted", 0);
        unitWrapper.orderByAsc("order_num");
        List<Unit> units = unitMapper.selectList(unitWrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Unit unit : units) {
            Map<String, Object> unitMap = new HashMap<>();
            unitMap.put("id", unit.getId());
            unitMap.put("name", unit.getName());
            unitMap.put("description", unit.getDescription());
            unitMap.put("orderNum", unit.getOrderNum());

            // 添加学习统计逻辑
            unitMap.put("textCount", calculateTextCount(unit.getId()));
            unitMap.put("wordCount", calculateWordCount(unit.getId()));
            unitMap.put("learnedWords", calculateLearnedWordsInUnit(studentId, unit.getId()));
            unitMap.put("studyTime", calculateStudyTimeInUnit(studentId, unit.getId()));
            unitMap.put("progress", calculateProgressInUnit(studentId, unit.getId()));

            result.add(unitMap);
        }

        return result;
    }

    @Override
    public Map<String, Object> getUnitContentWithLearningStates(Long studentId, Long unitId) {
        Map<String, Object> content = new HashMap<>();
        content.put("unitId", unitId);

        // 获取课文列表
        QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
        textWrapper.eq("unit_id", unitId);
        List<com.english.entity.Text> texts = textMapper.selectList(textWrapper);

        // 获取课文信息
        List<Map<String, Object>> textList = new ArrayList<>();
        for (com.english.entity.Text text : texts) {
            Map<String, Object> textMap = new HashMap<>();
            textMap.put("id", text.getId());
            textMap.put("title", text.getTitle());
            textMap.put("content", text.getContent());
            textMap.put("difficulty", text.getDifficulty());
            textMap.put("audioUrl", text.getAudioUrl());

            // 检查学生的学习状态
            textMap.put("isRead", checkTextReadStatus(studentId, text.getId()));
            textMap.put("readTime", getTextReadTime(studentId, text.getId()));

            textList.add(textMap);
        }
        content.put("texts", textList);

        // 获取单元单词列表
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.eq("unit_id", unitId);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

        // 获取单词详细信息
        List<Map<String, Object>> wordList = new ArrayList<>();
        for (UnitWord unitWord : unitWords) {
            Word word = wordMapper.selectById(unitWord.getWordId());
            if (word != null) {
                Map<String, Object> wordMap = new HashMap<>();
                wordMap.put("id", word.getId());
                wordMap.put("word", word.getWord());
                wordMap.put("pronunciation", word.getPronunciation());
                wordMap.put("definition", word.getDefinition());
                wordMap.put("exampleSentence", word.getExampleSentence());
                wordMap.put("audioUrl", word.getAudioUrl());
                wordMap.put("partOfSpeech", word.getPartOfSpeech());
                wordMap.put("difficulty", word.getDifficulty());

                // 检查学生的生词本状态
                QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
                vocabWrapper.eq("student_id", studentId).eq("word_id", word.getId());
                VocabularyBook vocabBook = vocabularyBookMapper.selectOne(vocabWrapper);

                wordMap.put("inVocabulary", vocabBook != null);
                if (vocabBook != null) {
                    wordMap.put("masteryLevel", vocabBook.getMasteryLevel());
                    wordMap.put("reviewCount", vocabBook.getReviewCount());
                    wordMap.put("lastReviewed", vocabBook.getLastReviewed());
                } else {
                    wordMap.put("masteryLevel", null);
                    wordMap.put("reviewCount", 0);
                }

                wordList.add(wordMap);
            }
        }
        content.put("words", wordList);

        return content;
    }

    @Override
    public Map<String, Object> getTextDetailWithAnnotation(Long studentId, Long textId) {
        Map<String, Object> detail = new HashMap<>();

        // 这里需要实现课文详情和单词标注
        // 目前返回基本结构
        detail.put("id", textId);
        detail.put("title", "");
        detail.put("content", "");
        detail.put("annotatedContent", "");

        return detail;
    }

    @Override
    public void recordTextView(Long studentId, Long textId) {
        LearningRecord record = new LearningRecord();
        record.setStudentId(studentId);
        record.setWordId(0L); // 课文学习可能不需要关联具体单词
        record.setRecordType("VIEW");
        record.setDurationSeconds(0); // 可以根据实际学习时长设置
        record.setCorrectCount(0);
        record.setTotalCount(1);
        record.setCreatedTime(LocalDateTime.now());

        learningRecordMapper.insert(record);
    }

    @Override
    public void recordWordView(Long studentId, Long wordId) {
        LearningRecord record = new LearningRecord();
        record.setStudentId(studentId);
        record.setWordId(wordId);
        record.setRecordType("VIEW");
        record.setDurationSeconds(30); // 默认查看时长30秒
        record.setCorrectCount(0);
        record.setTotalCount(1);
        record.setCreatedTime(LocalDateTime.now());

        learningRecordMapper.insert(record);
    }

    @Override
    public void recordVocabularyAdd(Long studentId, Long wordId) {
        LearningRecord record = new LearningRecord();
        record.setStudentId(studentId);
        record.setWordId(wordId);
        record.setRecordType("VIEW");
        record.setDurationSeconds(10);
        record.setCorrectCount(0);
        record.setTotalCount(1);
        record.setCreatedTime(LocalDateTime.now());

        learningRecordMapper.insert(record);
    }

    @Override
    public void recordWordReview(Long studentId, Long wordId, boolean isCorrect) {
        LearningRecord record = new LearningRecord();
        record.setStudentId(studentId);
        record.setWordId(wordId);
        record.setRecordType("REVIEW");
        record.setDurationSeconds(15);
        record.setCorrectCount(isCorrect ? 1 : 0);
        record.setTotalCount(1);
        record.setCreatedTime(LocalDateTime.now());

        learningRecordMapper.insert(record);
    }

    @Override
    public Map<String, Object> getTodayLearningStats(Long studentId) {
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
              .ge("created_time", LocalDateTime.of(LocalDate.now(), LocalTime.MIN));

        List<LearningRecord> records = learningRecordMapper.selectList(wrapper);

        Map<String, Object> stats = new HashMap<>();
        stats.put("wordsLearned", records.size());
        stats.put("reviewCount", (int) records.stream().filter(r -> "REVIEW".equals(r.getRecordType())).count());
        stats.put("studyTime", records.stream().mapToInt(r -> r.getDurationSeconds()).sum() / 60);
        stats.put("accuracy", calculateAccuracy(records));

        return stats;
    }

    @Override
    public Map<String, Object> getLearningOverview(Long studentId, Map<String, Object> params) {
        Map<String, Object> overview = new HashMap<>();

        // 基本统计
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);

        List<LearningRecord> allRecords = learningRecordMapper.selectList(wrapper);

        overview.put("totalWordsLearned", allRecords.size());
        overview.put("totalStudyTime", allRecords.stream().mapToInt(r -> r.getDurationSeconds()).sum() / 60);
        overview.put("averageAccuracy", calculateAccuracy(allRecords));

        // 可以添加更多统计维度

        return overview;
    }

    @Override
    public Map<String, Object> getLearningRecordsWithPagination(Map<String, Object> params) {
        // 简化实现，返回分页结构
        Map<String, Object> result = new HashMap<>();
        result.put("records", new ArrayList<>());
        result.put("total", 0);
        result.put("size", (Integer) params.get("size"));
        result.put("current", (Integer) params.get("page"));
        result.put("pages", 0);

        return result;
    }

    @Override
    public Map<String, Object> getCourseLearningStats(Long studentId, Long courseId) {
        Map<String, Object> stats = new HashMap<>();
        // 这里需要实现具体的课程统计逻辑
        return stats;
    }

    @Override
    public Map<String, Object> getAllCoursesLearningStats(Long studentId) {
        Map<String, Object> stats = new HashMap<>();
        // 这里需要实现所有课程的统计逻辑
        return stats;
    }

    @Override
    public Map<String, Object> getStudyTimeStats(Long studentId, Map<String, Object> params) {
        Map<String, Object> stats = new HashMap<>();
        // 这里需要实现学习时长统计逻辑
        return stats;
    }

    @Override
    public Map<String, Object> generateProgressReport(Long studentId, String type) {
        Map<String, Object> report = new HashMap<>();
        // 这里需要实现进度报告生成逻辑
        return report;
    }

    @Override
    public Map<String, Object> getAchievementStats(Long studentId) {
        Map<String, Object> achievements = new HashMap<>();
        // 这里需要实现成就统计逻辑
        return achievements;
    }

    @Override
    public Map<String, Object> getLearningRanking(Long studentId, String type, Long targetId) {
        Map<String, Object> ranking = new HashMap<>();
        // 这里需要实现学习排名逻辑
        return ranking;
    }

    @Override
    public Map<String, Object> exportLearningData(Long studentId, Map<String, Object> params) {
        Map<String, Object> exportResult = new HashMap<>();
        // 这里需要实现数据导出逻辑
        return exportResult;
    }

    @Override
    public Map<String, Object> getAssignmentLearningRecords(Long assignmentId, Long studentId, int page, int size) {
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
              .eq("assignment_id", assignmentId)
              .orderByDesc("created_time");

        // 分页查询
        Page<LearningRecord> pageParam = new Page<>(page, size);
        Page<LearningRecord> pageResult = learningRecordMapper.selectPage(pageParam, wrapper);

        List<Map<String, Object>> recordList = new ArrayList<>();
        for (LearningRecord record : pageResult.getRecords()) {
            Map<String, Object> recordMap = new HashMap<>();
            recordMap.put("id", record.getId());
            recordMap.put("actionType", record.getRecordType());
            recordMap.put("duration", record.getDurationSeconds() != null ? record.getDurationSeconds() / 60 : 0); // 转换为分钟
            recordMap.put("createdAt", record.getCreatedTime());
            recordMap.put("correctCount", record.getCorrectCount());
            recordMap.put("totalCount", record.getTotalCount());

            // 获取关联的单词信息
            if (record.getWordId() != null && record.getWordId() > 0) {
                Word word = wordMapper.selectById(record.getWordId());
                if (word != null) {
                    recordMap.put("content", "学习单词：" + word.getWord() + " - " + word.getDefinition());
                } else {
                    recordMap.put("content", "学习单词（ID: " + record.getWordId() + "）");
                }
            } else {
                recordMap.put("content", "任务学习活动");
            }

            recordList.add(recordMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", recordList);
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    /**
     * 计算正确率
     */
    private double calculateAccuracy(List<LearningRecord> records) {
        if (records.isEmpty()) return 0.0;

        int correct = records.stream()
                .mapToInt(r -> r.getCorrectCount())
                .sum();
        int total = records.stream()
                .mapToInt(r -> r.getTotalCount())
                .sum();

        return total > 0 ? (double) correct / total * 100 : 0.0;
    }

    /**
     * 计算单元中的课文数量
     */
    private int calculateTextCount(Long unitId) {
        QueryWrapper<com.english.entity.Text> wrapper = new QueryWrapper<>();
        wrapper.eq("unit_id", unitId);
        return Math.toIntExact(textMapper.selectCount(wrapper));
    }

    /**
     * 计算单元中的单词数量
     */
    private int calculateWordCount(Long unitId) {
        QueryWrapper<UnitWord> wrapper = new QueryWrapper<>();
        wrapper.eq("unit_id", unitId);
        return Math.toIntExact(unitWordMapper.selectCount(wrapper));
    }

    /**
     * 计算学生在单元中学习的单词数量
     */
    private int calculateLearnedWordsInUnit(Long studentId, Long unitId) {
        // 获取单元下的所有单词
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.eq("unit_id", unitId);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

        if (unitWords.isEmpty()) {
            return 0;
        }

        List<Long> wordIds = unitWords.stream().map(UnitWord::getWordId).collect(Collectors.toList());

        // 查询学生已学习的单词
        QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
        vocabWrapper.eq("student_id", studentId)
                   .in("word_id", wordIds)
                   .ne("mastery_level", "NEW");

        return Math.toIntExact(vocabularyBookMapper.selectCount(vocabWrapper));
    }

    /**
     * 计算学生在单元中的学习时间（分钟）
     */
    private int calculateStudyTimeInUnit(Long studentId, Long unitId) {
        // 获取单元下的所有单词
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.eq("unit_id", unitId);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

        if (unitWords.isEmpty()) {
            return 0;
        }

        List<Long> wordIds = unitWords.stream().map(UnitWord::getWordId).collect(Collectors.toList());

        // 查询学习记录
        QueryWrapper<LearningRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("student_id", studentId)
                   .in("word_id", wordIds);
        List<LearningRecord> records = learningRecordMapper.selectList(recordWrapper);

        // 计算总学习时间（转换为分钟）
        return records.stream().mapToInt(record ->
            record.getDurationSeconds() != null ? record.getDurationSeconds() / 60 : 0
        ).sum();
    }

    /**
     * 计算学生在单元中的学习进度
     */
    private double calculateProgressInUnit(Long studentId, Long unitId) {
        int totalWords = calculateWordCount(unitId);
        int learnedWords = calculateLearnedWordsInUnit(studentId, unitId);

        if (totalWords == 0) {
            return 0.0;
        }

        return (double) learnedWords / totalWords * 100;
    }

    /**
     * 检查课文是否已阅读
     * 暂时简化实现，返回默认值false
     */
    private boolean checkTextReadStatus(Long studentId, Long textId) {
        // 简化实现：暂时返回false
        // 实际项目中可以根据需要添加text_view_records表
        return false;
    }

    /**
     * 获取课文阅读时间
     * 暂时简化实现，返回默认值0
     */
    private int getTextReadTime(Long studentId, Long textId) {
        // 简化实现：暂时返回0分钟
        // 实际项目中可以根据需要添加text_view_records表
        return 0;
    }
}