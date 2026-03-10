package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.LearningRecordsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(LearningRecordsServiceImpl.class);

    @Autowired
    private LearningRecordMapper learningRecordMapper;

    @Autowired
    private TextLearningRecordMapper textLearningRecordMapper;

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private UnitMapper unitMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private WordMapper wordMapper;

    @Autowired
    private UnitWordMapper unitWordMapper;

    @Autowired
    private VocabularyBookMapper vocabularyBookMapper;

    @Override
    public int getTodayStudyTime(Long studentId) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }

        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .ge("created_time", LocalDateTime.of(LocalDate.now(), LocalTime.MIN));

        List<LearningRecord> records = learningRecordMapper.selectList(wrapper);
        if (records == null) {
            records = new ArrayList<>();
        }

        // 计算今日总学习时长（秒），然后转换为分钟
        // 过滤异常值：单次学习时长超过2小时（7200秒）的记录视为异常，不计算在内
        // 使用 long 避免大量记录累加导致 int 溢出
        long totalSeconds = records.stream()
                .mapToLong(r -> {
                    int duration = r.getDurationSeconds() != null ? r.getDurationSeconds() : 0;
                    // 过滤异常值：单次学习超过2小时视为异常
                    if (duration > 7200) {
                        logger.warn("检测到异常学习时长记录: 学生ID={}, 记录ID={}, 时长={}秒, 类型={}",
                                studentId, r.getId(), duration, r.getRecordType());
                        return 0L;
                    }
                    return (long) duration;
                })
                .sum();

        long minutes = totalSeconds / 60;

        // 添加合理性检查：单日学习时长超过12小时（720分钟）视为异常
        if (minutes > 720) {
            logger.warn("检测到异常学习时长: 学生ID={}, 今日学习时长={}分钟(约{}小时), 记录数={}, 可能存在问题",
                    studentId, minutes, minutes / 60.0, records.size());
            // 记录详细信息用于调试
            logger.debug("学习记录详情: {}", records.stream()
                    .map(r -> String.format("ID=%d, 类型=%s, 时长=%d秒",
                            r.getId(), r.getRecordType(), r.getDurationSeconds() != null ? r.getDurationSeconds() : 0))
                    .collect(java.util.stream.Collectors.joining(", ")));
        }

        logger.debug("计算今日学习时长: 学生ID={}, 记录数={}, 总秒数={}, 总分钟数={}",
                studentId, records.size(), totalSeconds, minutes);

        return (int) Math.min(minutes, Integer.MAX_VALUE);
    }

    @Override
    public List<Map<String, Object>> getUnitsWithLearningStats(Long studentId, Long courseId) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }
        if (courseId == null) {
            throw new IllegalArgumentException("courseId 不能为空");
        }

        // 查询课程下的所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        unitWrapper.orderByAsc("order_num");
        List<Unit> units = unitMapper.selectList(unitWrapper);
        if (units == null) {
            units = new ArrayList<>();
        }

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
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }
        if (unitId == null) {
            throw new IllegalArgumentException("unitId 不能为空");
        }

        Map<String, Object> content = new HashMap<>();
        content.put("unitId", unitId);

        logger.debug("获取单元内容 - 学生ID: {}, 单元ID: {}", studentId, unitId);

        // 获取课文列表
        QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
        textWrapper.eq("unit_id", unitId).eq("deleted", 0);
        List<com.english.entity.Text> texts = textMapper.selectList(textWrapper);

        logger.debug("获取到课文数量: {}", texts.size());

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
            textMap.put("readTime", getTextReadTime(studentId, text.getId(), null));

            textList.add(textMap);
        }
        content.put("texts", textList);

        // 获取单元单词列表
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.eq("unit_id", unitId);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

        logger.debug("获取到单元单词关联记录数量: {}", unitWords.size());

        // 获取单词详细信息（批量查询，避免 N+1）
        List<Map<String, Object>> wordList = new ArrayList<>();
        List<Long> wordIds = unitWords.stream()
                .map(UnitWord::getWordId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!wordIds.isEmpty()) {
            // 批量查询所有单词
            QueryWrapper<Word> wordBatchWrapper = new QueryWrapper<>();
            wordBatchWrapper.in("id", wordIds).eq("deleted", 0);
            Map<Long, Word> wordById = wordMapper.selectList(wordBatchWrapper).stream()
                    .collect(Collectors.toMap(Word::getId, w -> w));

            // 批量查询该学生对这些单词的生词本状态
            QueryWrapper<VocabularyBook> vocabBatchWrapper = new QueryWrapper<>();
            vocabBatchWrapper.eq("student_id", studentId).in("word_id", wordIds);
            Map<Long, VocabularyBook> vocabByWordId = vocabularyBookMapper.selectList(vocabBatchWrapper).stream()
                    .collect(Collectors.toMap(VocabularyBook::getWordId, v -> v));

            for (UnitWord unitWord : unitWords) {
                Word word = wordById.get(unitWord.getWordId());
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

                    VocabularyBook vocabBook = vocabByWordId.get(word.getId());
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
        }

        logger.debug("最终返回的单词数量: {}", wordList.size());
        content.put("words", wordList);

        return content;
    }

    @Override
    public Map<String, Object> getTextDetailWithAnnotation(Long studentId, Long textId, Long assignmentId) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }
        if (textId == null) {
            throw new IllegalArgumentException("textId 不能为空");
        }

        Map<String, Object> detail = new HashMap<>();

        // 查询课文详情
        com.english.entity.Text text = textMapper.selectById(textId);
        if (text != null) {
            detail.put("id", text.getId());
            detail.put("title", text.getTitle());
            detail.put("content", text.getContent());
            detail.put("contentType", text.getContentType());
            detail.put("markdownContent", text.getMarkdownContent());
            detail.put("difficulty", text.getDifficulty());
            detail.put("audioUrl", text.getAudioUrl());

            // 添加所有可用版本
            Map<String, String> versions = new HashMap<>();

            // 根据内容类型返回相应版本
            if ("MARKDOWN".equals(text.getContentType())) {
                // Markdown内容，返回Markdown版本
                if (text.getMarkdownContent() != null && !text.getMarkdownContent().trim().isEmpty()) {
                    versions.put("original", text.getMarkdownContent());
                }
                if (text.getCet4MarkdownContent() != null && !text.getCet4MarkdownContent().trim().isEmpty()) {
                    versions.put("cet4", text.getCet4MarkdownContent());
                }
                if (text.getCet6MarkdownContent() != null && !text.getCet6MarkdownContent().trim().isEmpty()) {
                    versions.put("cet6", text.getCet6MarkdownContent());
                }
                if (text.getAcademicMarkdownContent() != null && !text.getAcademicMarkdownContent().trim().isEmpty()) {
                    versions.put("academic", text.getAcademicMarkdownContent());
                }
                if (text.getChineseParallelMarkdownContent() != null
                        && !text.getChineseParallelMarkdownContent().trim().isEmpty()) {
                    versions.put("chinese_parallel", text.getChineseParallelMarkdownContent());
                }
            } else {
                // 普通文本内容，返回文本版本
                if (text.getContent() != null && !text.getContent().trim().isEmpty()) {
                    versions.put("original", text.getContent());
                }
                if (text.getCet4Content() != null && !text.getCet4Content().trim().isEmpty()) {
                    versions.put("cet4", text.getCet4Content());
                }
                if (text.getCet6Content() != null && !text.getCet6Content().trim().isEmpty()) {
                    versions.put("cet6", text.getCet6Content());
                }
                if (text.getAcademicContent() != null && !text.getAcademicContent().trim().isEmpty()) {
                    versions.put("academic", text.getAcademicContent());
                }
                if (text.getChineseParallelContent() != null && !text.getChineseParallelContent().trim().isEmpty()) {
                    versions.put("chinese_parallel", text.getChineseParallelContent());
                }
            }
            detail.put("versions", versions);
            detail.put("availableVersions", new ArrayList<>(versions.keySet()));

            // 获取课程和单元信息用于导航
            if (text.getUnitId() != null) {
                try {
                    // 查询单元信息
                    com.english.entity.Unit unit = unitMapper.selectById(text.getUnitId());
                    if (unit != null) {
                        detail.put("unitId", unit.getId());
                        detail.put("unitName", unit.getName());

                        // 查询课程信息
                        if (unit.getCourseId() != null) {
                            com.english.entity.Course course = courseMapper.selectById(unit.getCourseId());
                            if (course != null) {
                                detail.put("courseId", course.getId());
                                detail.put("courseName", course.getName());
                            } else {
                                detail.put("courseId", unit.getCourseId());
                                detail.put("courseName", "未知课程");
                            }
                        } else {
                            detail.put("courseId", null);
                            detail.put("courseName", "未分配课程");
                        }
                    } else {
                        detail.put("unitId", text.getUnitId());
                        detail.put("unitName", "未知单元");
                        detail.put("courseId", null);
                        detail.put("courseName", "未知课程");
                    }
                } catch (Exception e) {
                    // 如果查询课程单元信息失败，设置默认值
                    detail.put("unitId", text.getUnitId());
                    detail.put("unitName", "未知单元");
                    detail.put("courseId", null);
                    detail.put("courseName", "未知课程");
                }
            } else {
                // 如果课文没有关联单元，可能是用户上传的内容
                detail.put("unitId", null);
                detail.put("unitName", "用户上传内容");
                detail.put("courseId", null);
                detail.put("courseName", "用户上传内容");
            }

            // 获取课文中的单词并进行标注
            String content = text.getContent();
            if (content == null) {
                content = "";
            }
            String annotatedContent = annotateTextWithVocabulary(studentId, content);
            detail.put("annotatedContent", annotatedContent);

            // 检查学习状态
            detail.put("isRead", checkTextReadStatus(studentId, textId));
            detail.put("readTime", getTextReadTime(studentId, textId, assignmentId));
        } else {
            detail.put("id", textId);
            detail.put("title", "课文不存在");
            detail.put("content", "课文内容不存在");
            detail.put("annotatedContent", "课文内容不存在");
            detail.put("versions", new HashMap<>());
            detail.put("availableVersions", new ArrayList<>());
            detail.put("courseId", null);
            detail.put("courseName", "未知课程");
            detail.put("unitId", null);
            detail.put("unitName", "未知单元");
        }

        return detail;
    }

    @Override
    @Transactional
    public void recordTextView(Long studentId, Long textId) {
        // 此重载方法仅在无 wordId/assignmentId 上下文时调用
        // wordId 字段允许为 null（表结构层面若有外键约束，请修改为可空）
        // 若无法修改表结构，请直接使用带 assignmentId 的重载版本
        LearningRecord record = new LearningRecord();
        record.setStudentId(studentId);
        record.setTextId(textId); // 关联课文ID，供后续按课文查询学习时长使用
        // 不设置 wordId（保持 null），避免对 ID=1 记录的硬编码依赖
        record.setRecordType("TEXT_VIEW"); // 使用特殊类型标识课文学习
        record.setDurationSeconds(15); // 默认15秒
        record.setCorrectCount(0);
        record.setTotalCount(1);
        record.setCreatedTime(LocalDateTime.now());

        learningRecordMapper.insert(record);
    }

    @Override
    @Transactional
    public void recordTextView(Long studentId, Long textId, Long assignmentId, Integer duration) {
        // 确保有足够的时长（至少1秒）
        int finalDuration = duration != null && duration > 0 ? duration : 1;

        // 尝试原子更新现有记录（防竞态条件）
        int updatedCount = learningRecordMapper.incrementTextViewDuration(studentId, textId, assignmentId, finalDuration);

        if (updatedCount <= 0) {
            // 没有找到可更新的记录，创建新记录
            recordLearning(studentId, "TEXT_VIEW", null, finalDuration, textId, assignmentId);

            logger.info("创建课文学习记录: 学生={}, 课文ID={}, 任务ID={}, 时长={}秒",
                    studentId, textId, assignmentId, finalDuration);
        } else {
            // 成功更新现有记录
            logger.info("原子更新课文学习记录: 学生={}, 课文ID={}, 任务ID={}, 增量={}秒",
                    studentId, textId, assignmentId, finalDuration);
        }
    }

    @Override
    @Transactional
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
    @Transactional
    public void recordWordView(Long studentId, Long wordId, Long assignmentId, Integer duration) {
        LearningRecord record = new LearningRecord();
        record.setStudentId(studentId);
        record.setWordId(wordId);
        record.setAssignmentId(assignmentId);
        record.setRecordType("VIEW");
        record.setDurationSeconds(duration != null ? duration : 30); // 默认30秒
        record.setCorrectCount(0);
        record.setTotalCount(1);
        record.setCreatedTime(LocalDateTime.now());

        learningRecordMapper.insert(record);
    }

    @Override
    @Transactional
    public void recordLearning(Long studentId, String type, Long wordId, Integer duration, Long textId,
            Long assignmentId) {
        LearningRecord record = new LearningRecord();
        record.setStudentId(studentId);
        record.setAssignmentId(assignmentId);

        // 设置wordId（word_id 列为 DEFAULT NULL，无外键约束，允许为 null）
        if (wordId != null) {
            record.setWordId(wordId);
        }
        // wordId 为 null 时不设置，表示非单词学习活动（课文阅读、任务完成等）

        // 设置课文ID
        record.setTextId(textId);

        record.setRecordType(type);
        record.setDurationSeconds(duration != null && duration > 0 ? duration : 1);
        record.setCorrectCount(0);
        record.setTotalCount(1);
        record.setCreatedTime(LocalDateTime.now());

        learningRecordMapper.insert(record);

        logger.debug("记录学习活动: 学生={}, 类型={}, 时长={}秒{}",
                studentId, type, duration,
                textId != null ? ", 课文ID=" + textId
                        : wordId != null ? ", 单词ID=" + wordId : assignmentId != null ? ", 任务ID=" + assignmentId : "");
    }

    // 在类定义的开头添加这些常量
    private static final int MIN_TEXT_READ_TIME_SECONDS = 600; // 10分钟
    private static final double COMPLETION_THRESHOLD = 80.0; // 完成阈值80%

    @Override
    @Transactional
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
    public Long startReadingSession(Long studentId, Long textId, Long assignmentId) {
        TextLearningRecord record = new TextLearningRecord();
        record.setStudentId(studentId);
        record.setTextId(textId);
        record.setAssignmentId(assignmentId);
        record.setStartTime(LocalDateTime.now());
        record.setIsCompleted(0);

        textLearningRecordMapper.insert(record);
        return record.getId();
    }

    @Override
    @Transactional
    public void completeReadingSession(Long recordId) {
        TextLearningRecord record = textLearningRecordMapper.selectById(recordId);
        if (record != null && Integer.valueOf(0).equals(record.getIsCompleted())) {
            LocalDateTime now = LocalDateTime.now();
            record.setEndTime(now);
            record.setIsCompleted(1);

            // 计算时长（秒）
            long duration = java.time.Duration.between(record.getStartTime(), now).getSeconds();
            record.setDurationSeconds((int) duration);
            record.setUpdatedTime(now);

            textLearningRecordMapper.updateById(record);
        }
    }

    @Override
    @Transactional
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
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }

        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .ge("created_time", LocalDateTime.of(LocalDate.now(), LocalTime.MIN));

        List<LearningRecord> records = learningRecordMapper.selectList(wrapper);
        if (records == null) {
            records = new ArrayList<>();
        }

        Map<String, Object> stats = new HashMap<>();

        if (records.isEmpty()) {
            stats.put("wordsLearned", 0);
            stats.put("textsLearned", 0);
            stats.put("reviewCount", 0);
            stats.put("testCount", 0);
            stats.put("totalActivities", 0);
            stats.put("studyTime", 0);
            stats.put("accuracy", 0.0);
            return stats;
        }

        // 单次遍历，分类统计学习记录 + 计算学习时间 + 准备准确率数据
        long wordViewCount = 0;
        long textViewCount = 0;
        long reviewCount = 0;
        long testCount = 0;
        long totalSeconds = 0;
        List<LearningRecord> testRecords = new ArrayList<>();

        for (LearningRecord r : records) {
            String recordType = r.getRecordType();
            if ("VIEW".equals(recordType) && r.getWordId() != null && r.getWordId() > 0) {
                wordViewCount++;
            } else if ("TEXT_VIEW".equals(recordType)) {
                textViewCount++;
            } else if ("REVIEW".equals(recordType)) {
                reviewCount++;
                testRecords.add(r);
            } else if ("TEST".equals(recordType)) {
                testCount++;
                testRecords.add(r);
            }

            if (r.getDurationSeconds() != null) {
                totalSeconds += r.getDurationSeconds();
            }
        }

        stats.put("wordsLearned", (int) Math.min(wordViewCount, Integer.MAX_VALUE));
        stats.put("textsLearned", (int) Math.min(textViewCount, Integer.MAX_VALUE));
        stats.put("reviewCount", (int) Math.min(reviewCount, Integer.MAX_VALUE));
        stats.put("testCount", (int) Math.min(testCount, Integer.MAX_VALUE));
        stats.put("totalActivities", records.size());
        stats.put("studyTime", (int) Math.min(totalSeconds / 60, Integer.MAX_VALUE));
        stats.put("accuracy", calculateAccuracy(testRecords));

        return stats;
    }

    @Override
    public Map<String, Object> getLearningOverview(Long studentId, Map<String, Object> params) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }

        Map<String, Object> overview = new HashMap<>();

        // 基本统计
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);

        List<LearningRecord> allRecords = learningRecordMapper.selectList(wrapper);
        if (allRecords == null) {
            allRecords = new ArrayList<>();
        }

        if (allRecords.isEmpty()) {
            overview.put("totalWordsLearned", 0L);
            overview.put("totalStudyTime", 0L);
            overview.put("averageAccuracy", 0.0);
            return overview;
        }

        overview.put("totalWordsLearned", allRecords.stream()
                .filter(r -> r.getWordId() != null && r.getWordId() > 0)
                .map(LearningRecord::getWordId)
                .distinct()
                .count());
        long totalSeconds = allRecords.stream()
                .mapToLong(r -> r.getDurationSeconds() != null ? r.getDurationSeconds() : 0)
                .sum();
        overview.put("totalStudyTime", (int) Math.min(totalSeconds / 60, Integer.MAX_VALUE));
        overview.put("averageAccuracy", calculateAccuracy(allRecords));

        // 可以添加更多统计维度

        return overview;
    }

    @Override
    public Map<String, Object> getLearningRecordsWithPagination(Map<String, Object> params) {
        if (params == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        Long studentId = params.get("studentId") != null ? Long.valueOf(params.get("studentId").toString()) : null;
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }
        int page = params.get("page") != null ? ((Number) params.get("page")).intValue() : 1;
        int size = params.get("size") != null ? ((Number) params.get("size")).intValue() : 10;

        // 参数边界校验
        page = Math.max(1, page);
        size = Math.max(1, Math.min(size, 100));

        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .orderByDesc("created_time");

        // 分页查询
        Page<LearningRecord> pageParam = new Page<>(page, size);
        Page<LearningRecord> pageResult = learningRecordMapper.selectPage(pageParam, wrapper);

        // 转换结果
        List<Map<String, Object>> recordList = new ArrayList<>();
        for (LearningRecord record : pageResult.getRecords()) {
            Map<String, Object> recordMap = new HashMap<>();
            recordMap.put("id", record.getId());
            recordMap.put("recordType", record.getRecordType());
            recordMap.put("durationSeconds", record.getDurationSeconds());
            recordMap.put("correctCount", record.getCorrectCount());
            recordMap.put("totalCount", record.getTotalCount());
            recordMap.put("createdTime", record.getCreatedTime());

            // 获取关联的单词信息
            if (record.getWordId() != null && record.getWordId() > 0) {
                Word word = wordMapper.selectById(record.getWordId());
                if (word != null) {
                    recordMap.put("wordInfo", Map.of(
                            "id", word.getId(),
                            "word", word.getWord(),
                            "definition", word.getDefinition()));
                }
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

    @Override
    public Map<String, Object> getCourseLearningStats(Long studentId, Long courseId) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }
        if (courseId == null) {
            throw new IllegalArgumentException("courseId 不能为空");
        }

        Map<String, Object> stats = new HashMap<>();

        // 查询课程下的所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);
        if (units == null) {
            units = new ArrayList<>();
        }

        if (units.isEmpty()) {
            return stats;
        }

        List<Long> unitIds = units.stream().map(Unit::getId).collect(Collectors.toList());

        // 查询课程下的所有单词
        List<UnitWord> unitWords;
        if (unitIds.isEmpty()) {
            unitWords = new ArrayList<>();
        } else {
            QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
            unitWordWrapper.in("unit_id", unitIds);
            unitWords = unitWordMapper.selectList(unitWordWrapper);
            if (unitWords == null) {
                unitWords = new ArrayList<>();
            }
        }

        List<Long> wordIds = unitWords.stream()
                .map(UnitWord::getWordId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 统计数据
        stats.put("totalUnits", units.size());

        // 计算总单词数：需要过滤已删除的单词
        int totalWords = 0;
        if (!wordIds.isEmpty()) {
            QueryWrapper<Word> wordWrapper = new QueryWrapper<>();
            wordWrapper.in("id", wordIds)
                    .eq("deleted", 0);
            totalWords = Math.toIntExact(wordMapper.selectCount(wordWrapper));
        }
        stats.put("totalWords", totalWords);

        // 学生已掌握的单词
        int learnedWords = 0;
        if (!wordIds.isEmpty()) {
            QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
            vocabWrapper.eq("student_id", studentId)
                    .in("word_id", wordIds)
                    .ne("mastery_level", "NEW");
            learnedWords = Math.toIntExact(vocabularyBookMapper.selectCount(vocabWrapper));
        }
        stats.put("learnedWords", learnedWords);

        // 学习进度
        stats.put("progress", totalWords > 0 ? (double) learnedWords / totalWords * 100 : 0.0);

        // 学习时间
        int studyTime = 0;
        if (!wordIds.isEmpty()) {
            QueryWrapper<LearningRecord> recordWrapper = new QueryWrapper<>();
            recordWrapper.eq("student_id", studentId)
                    .in("word_id", wordIds)
                    .select("COALESCE(SUM(duration_seconds), 0) as total_time");

            List<Map<String, Object>> timeResults = learningRecordMapper.selectMaps(recordWrapper);
            if (timeResults != null && !timeResults.isEmpty()) {
                Map<String, Object> timeResult = timeResults.get(0);
                Number totalTime = (Number) timeResult.get("total_time");
                studyTime = totalTime != null ? (int) Math.min(totalTime.longValue() / 60, Integer.MAX_VALUE) : 0;
            }
        }
        stats.put("studyTime", studyTime);

        return stats;
    }

    @Override
    public Map<String, Object> getAllCoursesLearningStats(Long studentId) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }

        Map<String, Object> stats = new HashMap<>();

        // 查询学生的学习记录
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);
        List<LearningRecord> records = learningRecordMapper.selectList(wrapper);
        if (records == null) {
            records = new ArrayList<>();
        }

        // 生词本统计
        QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
        vocabWrapper.eq("student_id", studentId);
        List<VocabularyBook> vocabularyBooks = vocabularyBookMapper.selectList(vocabWrapper);
        if (vocabularyBooks == null) {
            vocabularyBooks = new ArrayList<>();
        }

        stats.put("totalLearningRecords", records.size());
        stats.put("totalVocabularyWords", vocabularyBooks.size());
        stats.put("masteredWords",
                (int) vocabularyBooks.stream().filter(v -> "MASTERED".equals(v.getMasteryLevel())).count());
        stats.put("learningWords",
                (int) vocabularyBooks.stream().filter(v -> "LEARNING".equals(v.getMasteryLevel())).count());
        stats.put("newWords", (int) vocabularyBooks.stream().filter(v -> "NEW".equals(v.getMasteryLevel())).count());

        // 总学习时间（防溢出）
        long totalSeconds = records.stream().mapToLong(r -> r.getDurationSeconds() != null ? r.getDurationSeconds() : 0)
                .sum();
        stats.put("totalStudyTime", (int) Math.min(totalSeconds / 60, Integer.MAX_VALUE)); // 分钟

        return stats;
    }

    @Override
    public Map<String, Object> getStudyTimeStats(Long studentId, Map<String, Object> params) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }
        if (params == null) {
            params = new HashMap<>();
        }

        String period = (String) params.get("period");
        String granularity = (String) params.get("granularity"); // daily, weekly, monthly

        Map<String, Object> stats = new HashMap<>();

        LocalDateTime startTime;
        switch (period != null ? period.toLowerCase() : "week") {
            case "today":
                startTime = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
                break;
            case "week":
                startTime = LocalDateTime.of(LocalDate.now().minusDays(7), LocalTime.MIN);
                break;
            case "month":
                startTime = LocalDateTime.of(LocalDate.now().minusDays(30), LocalTime.MIN);
                break;
            default:
                startTime = LocalDateTime.of(LocalDate.now().minusDays(7), LocalTime.MIN);
        }

        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .ge("created_time", startTime)
                .select("COALESCE(SUM(duration_seconds), 0) as total_time");

        List<Map<String, Object>> resultList = learningRecordMapper.selectMaps(wrapper);
        if (resultList == null || resultList.isEmpty()) {
            stats.put("totalTime", 0);
            stats.put("period", period);
            stats.put("granularity", granularity);
            return stats;
        }
        Map<String, Object> result = resultList.get(0);
        Number totalTime = (Number) result.get("total_time");
        stats.put("totalTime", totalTime != null ? (int) Math.min(totalTime.longValue() / 60, Integer.MAX_VALUE) : 0);
        stats.put("period", period);
        stats.put("granularity", granularity);

        return stats;
    }

    @Override
    public Map<String, Object> generateProgressReport(Long studentId, String type) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }

        Map<String, Object> report = new HashMap<>();

        // 基本学习统计
        Map<String, Object> allStats = getAllCoursesLearningStats(studentId);
        report.put("learningStats", allStats);

        // 最近学习记录
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .orderByDesc("created_time")
                .last("LIMIT 10");

        List<LearningRecord> recentRecords = learningRecordMapper.selectList(wrapper);
        report.put("recentRecords", recentRecords != null ? recentRecords : new ArrayList<>());

        report.put("reportType", type);
        report.put("generatedAt", LocalDateTime.now());

        return report;
    }

    @Override
    public Map<String, Object> getAchievementStats(Long studentId) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }

        Map<String, Object> achievements = new HashMap<>();

        // 连续学习天数（简化实现）
        achievements.put("consecutiveDays", calculateConsecutiveDays(studentId));

        // 总学习单词数
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);
        long totalRecords = learningRecordMapper.selectCount(wrapper);
        achievements.put("totalWordsLearned", (int) Math.min(totalRecords, Integer.MAX_VALUE));

        // 总学习时间（小时）
        QueryWrapper<LearningRecord> timeWrapper = new QueryWrapper<>();
        timeWrapper.eq("student_id", studentId)
                .select("COALESCE(SUM(duration_seconds), 0) as total_time");

        List<Map<String, Object>> timeResults = learningRecordMapper.selectMaps(timeWrapper);
        if (timeResults != null && !timeResults.isEmpty()) {
            Map<String, Object> timeResult = timeResults.get(0);
            Number totalTime = (Number) timeResult.get("total_time");
            achievements.put("totalStudyHours", totalTime != null ? (int) Math.min(totalTime.longValue() / 3600, Integer.MAX_VALUE) : 0);
        } else {
            achievements.put("totalStudyHours", 0);
        }

        return achievements;
    }

    @Override
    public Map<String, Object> getLearningRanking(Long studentId, String type, Long targetId) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }

        Map<String, Object> ranking = new HashMap<>();
        // 简化实现，返回模拟排名数据
        ranking.put("studentRank", 5);
        ranking.put("totalStudents", 20);
        ranking.put("percentile", 75.0);
        ranking.put("type", type != null ? type : "global");
        return ranking;
    }

    @Override
    public Map<String, Object> exportLearningData(Long studentId, Map<String, Object> params) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }
        if (params == null) {
            params = new HashMap<>();
        }

        Map<String, Object> exportResult = new HashMap<>();

        // 获取学习记录
        Map<String, Object> paramMap = new HashMap<>(params);
        paramMap.put("studentId", studentId);
        Map<String, Object> records = getLearningRecordsWithPagination(paramMap);

        exportResult.put("studentId", studentId);
        exportResult.put("exportTime", LocalDateTime.now());
        exportResult.put("totalRecords", records != null ? records.get("total") : 0);
        exportResult.put("data", records != null ? records.get("records") : new ArrayList<>());

        return exportResult;
    }

    @Override
    public Map<String, Object> getAssignmentLearningRecords(Long assignmentId, Long studentId, int page, int size) {
        if (assignmentId == null || studentId == null) {
            throw new IllegalArgumentException("assignmentId 和 studentId 不能为空");
        }

        // 参数边界校验
        page = Math.max(1, page);
        size = Math.max(1, Math.min(size, 100));

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
            recordMap.put("duration", record.getDurationSeconds() != null ? (record.getDurationSeconds() + 59) / 60 : 0); // 向上取整转换为分钟
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

    // ==================== 私有辅助方法 ====================

    /**
     * 计算正确率
     */
    private double calculateAccuracy(List<LearningRecord> records) {
        if (records.isEmpty())
            return 0.0;

        int correct = records.stream()
                .mapToInt(r -> r.getCorrectCount() != null ? r.getCorrectCount() : 0)
                .sum();
        int total = records.stream()
                .mapToInt(r -> r.getTotalCount() != null ? r.getTotalCount() : 0)
                .sum();

        return total > 0 ? (double) correct / total * 100 : 0.0;
    }

    /**
     * 计算连续学习天数
     */
    private int calculateConsecutiveDays(Long studentId) {
        // 简化实现，返回模拟数据
        return 7;
    }

    /**
     * 计算单元中的课文数量（过滤已删除课文）
     */
    private int calculateTextCount(Long unitId) {
        QueryWrapper<com.english.entity.Text> wrapper = new QueryWrapper<>();
        wrapper.eq("unit_id", unitId).eq("deleted", 0);
        return Math.toIntExact(textMapper.selectCount(wrapper));
    }

    /**
     * 计算单元中的单词数量（过滤已删除单词）
     */
    private int calculateWordCount(Long unitId) {
        // 先查询单元中的单词关联记录
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.eq("unit_id", unitId);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

        if (unitWords == null || unitWords.isEmpty()) {
            return 0;
        }

        // 提取所有单词ID，然后查询未删除的单词
        List<Long> wordIds = unitWords.stream()
                .map(UnitWord::getWordId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (wordIds.isEmpty()) {
            return 0;
        }

        // 查询 deleted=0 的单词（Word 实体无 @TableLogic 注解，需手动过滤）
        QueryWrapper<Word> wordWrapper = new QueryWrapper<>();
        wordWrapper.in("id", wordIds)
                .eq("deleted", 0);
        return Math.toIntExact(wordMapper.selectCount(wordWrapper));
    }

    /**
     * 计算学生在单元中学习的单词数量
     */
    private int calculateLearnedWordsInUnit(Long studentId, Long unitId) {
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.eq("unit_id", unitId);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

        if (unitWords == null || unitWords.isEmpty()) {
            return 0;
        }

        List<Long> wordIds = unitWords.stream().map(UnitWord::getWordId).filter(Objects::nonNull).collect(Collectors.toList());

        if (wordIds.isEmpty()) {
            return 0;
        }

        // 需要同时过滤：1) 学生的生词本中有该单词，且 2) 单词未删除
        QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
        vocabWrapper.eq("student_id", studentId)
                .in("word_id", wordIds)
                .ne("mastery_level", "NEW");

        // 获取生词本中的单词ID
        List<Long> vocabWordIds = vocabularyBookMapper.selectList(vocabWrapper).stream()
                .map(VocabularyBook::getWordId)
                .collect(Collectors.toList());

        if (vocabWordIds.isEmpty()) {
            return 0;
        }

        // 最后过滤未删除的单词
        QueryWrapper<Word> wordWrapper = new QueryWrapper<>();
        wordWrapper.in("id", vocabWordIds)
                .eq("deleted", 0);
        return Math.toIntExact(wordMapper.selectCount(wordWrapper));
    }

    /**
     * 计算学生在单元中的学习时间（分钟）
     * 包含单词学习时间和课文阅读时间
     */
    private int calculateStudyTimeInUnit(Long studentId, Long unitId) {
        long totalSeconds = 0;

        // 1. 单词学习时间
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.eq("unit_id", unitId);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);
        if (unitWords == null) {
            unitWords = new ArrayList<>();
        }

        if (!unitWords.isEmpty()) {
            List<Long> wordIds = unitWords.stream().map(UnitWord::getWordId).collect(Collectors.toList());
            if (!wordIds.isEmpty()) {
                QueryWrapper<LearningRecord> wordRecordWrapper = new QueryWrapper<>();
                wordRecordWrapper.eq("student_id", studentId)
                        .in("word_id", wordIds);
                List<LearningRecord> wordRecords = learningRecordMapper.selectList(wordRecordWrapper);
                if (wordRecords != null && !wordRecords.isEmpty()) {
                    totalSeconds += wordRecords.stream()
                            .mapToLong(r -> r.getDurationSeconds() != null ? r.getDurationSeconds() : 0).sum();
                }
            }
        }

        // 2. 课文阅读时间（TEXT_VIEW 记录按 text_id 关联单元课文，过滤已删除课文）
        QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
        textWrapper.eq("unit_id", unitId).eq("deleted", 0);
        List<com.english.entity.Text> texts = textMapper.selectList(textWrapper);
        if (texts == null) {
            texts = new ArrayList<>();
        }

        if (!texts.isEmpty()) {
            List<Long> textIds = texts.stream().map(com.english.entity.Text::getId).collect(Collectors.toList());
            if (!textIds.isEmpty()) {
                QueryWrapper<LearningRecord> textRecordWrapper = new QueryWrapper<>();
                textRecordWrapper.eq("student_id", studentId)
                        .eq("record_type", "TEXT_VIEW")
                        .in("text_id", textIds);
                List<LearningRecord> textRecords = learningRecordMapper.selectList(textRecordWrapper);
                if (textRecords != null && !textRecords.isEmpty()) {
                    totalSeconds += textRecords.stream()
                            .mapToLong(r -> r.getDurationSeconds() != null ? r.getDurationSeconds() : 0).sum();
                }
            }
        }

        // 使用 Math.ceil 防止整数截断，返回不小于实际分钟数的整数
        return (int) Math.ceil(totalSeconds / 60.0);
    }

    /**
     * 计算学生在单元中的学习进度
     * 综合考虑课文和单词的完成情况
     */
    private double calculateProgressInUnit(Long studentId, Long unitId) {
        int totalWords = calculateWordCount(unitId);
        int learnedWords = calculateLearnedWordsInUnit(studentId, unitId);
        int totalTexts = calculateTextCount(unitId);
        int readTexts = calculateReadTextsInUnit(studentId, unitId);

        // 如果单元既没有单词也没有课文，返回0
        if (totalWords == 0 && totalTexts == 0) {
            return 0.0;
        }

        // 计算单词进度（权重50%）
        double wordProgress = totalWords > 0 ? (double) learnedWords / totalWords * 100 : 0.0;

        // 计算课文进度（权重50%）
        double textProgress = totalTexts > 0 ? (double) readTexts / totalTexts * 100 : 0.0;

        // 如果只有单词或只有课文，直接返回对应进度
        if (totalWords == 0) {
            return textProgress;
        }
        if (totalTexts == 0) {
            return wordProgress;
        }

        // 综合进度：单词和课文各占50%
        return (wordProgress * 0.5 + textProgress * 0.5);
    }

    /**
     * 计算学生在单元中已阅读的课文数量
     */
    private int calculateReadTextsInUnit(Long studentId, Long unitId) {
        // 查询单元下的所有非删除课文
        QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
        textWrapper.eq("unit_id", unitId).eq("deleted", 0);
        List<com.english.entity.Text> texts = textMapper.selectList(textWrapper);

        if (texts.isEmpty()) {
            return 0;
        }

        int readCount = 0;
        for (com.english.entity.Text text : texts) {
            // 检查课文是否已阅读（学习时长达到10分钟）
            // getTextReadTime 返回的是分钟数
            int readTime = getTextReadTime(studentId, text.getId(), null);
            if (readTime >= 10) { // 10分钟
                readCount++;
            }
        }

        return readCount;
    }

    /**
     * 检查课文阅读状态
     */
    private boolean checkTextReadStatus(Long studentId, Long textId) {
        // 检查课文是否已阅读（学习时长达到10分钟）
        // getTextReadTime 返回的是分钟数
        int readTimeMinutes = getTextReadTime(studentId, textId, null);
        return readTimeMinutes >= 10; // 10分钟
    }

    /**
     * 获取课文阅读时间（分钟）
     * 累计所有 TEXT_VIEW 记录的时长之和（同一篇文章可能分多次阅读）
     */
    private int getTextReadTime(Long studentId, Long textId, Long assignmentId) {
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .eq("record_type", "TEXT_VIEW")
                .eq("text_id", textId);

        // 如果有任务ID，优先查询任务相关的记录
        if (assignmentId != null) {
            wrapper.eq("assignment_id", assignmentId);
        }

        List<LearningRecord> records = learningRecordMapper.selectList(wrapper);

        // 安全的 null 检查：确保 getDurationSeconds() 不为 null，防止 NPE
        long totalSeconds = records.stream()
                .mapToLong(r -> r.getDurationSeconds() != null ? r.getDurationSeconds() : 0)
                .sum();

        // 防止整数溢出，确保不超过 Integer.MAX_VALUE
        return (int) Math.min(totalSeconds / 60, Integer.MAX_VALUE);
    }

    /**
     * 获取特定课文的阅读时间（用于课文任务完成判断）
     */
    @Override
    public int getTextReadTimeForSpecificText(Long studentId, Long textId, Long assignmentId) {
        if (studentId == null || textId == null) {
            return 0;
        }

        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .eq("record_type", "TEXT_VIEW")
                .eq("text_id", textId); // 现在可以使用 text_id 字段了

        if (assignmentId != null) {
            wrapper.eq("assignment_id", assignmentId);
        } else {
            wrapper.isNull("assignment_id");
        }

        List<LearningRecord> records = learningRecordMapper.selectList(wrapper);

        long totalSeconds = records.stream()
                .mapToLong(record -> record.getDurationSeconds() != null ? record.getDurationSeconds() : 0)
                .sum();

        logger.debug("获取课文阅读时间: 学生={}, 课文={}, 任务={}, 总时长={}秒",
                studentId, textId, assignmentId, totalSeconds);

        // 转换为分钟，防止整数溢出
        return (int) Math.min(totalSeconds / 60, Integer.MAX_VALUE);
    }

    /**
     * 标注课文中的生词
     */
    private String annotateTextWithVocabulary(Long studentId, String content) {
        // 简单实现：返回原内容，可以后续完善标注逻辑
        return content;
    }
}