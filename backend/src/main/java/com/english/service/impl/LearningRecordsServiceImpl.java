package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.*;
import com.english.enums.TaskType;
import com.english.mapper.*;
import com.english.service.CampaignStatsService;
import com.english.service.LearningRecordsService;
import com.english.exception.BusinessException;
import com.english.datasource.UseMaster;
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
    private TextUnitMapper textUnitMapper;

    @Autowired
    private UnitMapper unitMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private WordMapper wordMapper;

    @Autowired
    private UnitWordMapper unitWordMapper;

    @Autowired
    private CampaignStatsService campaignStatsService;

    @Autowired
    private VocabularyBookMapper vocabularyBookMapper;

    @Autowired
    private StudentClassMapper studentClassMapper;

    @Autowired
    private ClassMapper classMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private com.english.service.AuthorEnricher authorEnricher;

    @Override
    public int getTodayStudyTime(Long studentId) {
        if (studentId == null) {
            throw new BusinessException("studentId 不能为空");
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
        // 若超过上限，按最大合理值 720 分钟返回，避免异常数据污染前端显示
        if (minutes > 720) {
            logger.warn("检测到异常学习时长: 学生ID={}, 今日学习时长={}分钟(约{}小时), 记录数={}, 可能存在问题",
                    studentId, minutes, minutes / 60.0, records.size());
            // 记录详细信息用于调试
            logger.debug("学习记录详情: {}", records.stream()
                    .map(r -> String.format("ID=%d, 类型=%s, 时长=%d秒",
                            r.getId(), r.getRecordType(), r.getDurationSeconds() != null ? r.getDurationSeconds() : 0))
                    .collect(java.util.stream.Collectors.joining(", ")));
            // 异常数据: 返回上限值，而非异常值，防止前端显示错误数据
            minutes = 720;
        }

        logger.debug("计算今日学习时长: 学生ID={}, 记录数={}, 总秒数={}, 总分钟数={}",
                studentId, records.size(), totalSeconds, minutes);

        return (int) Math.min(minutes, Integer.MAX_VALUE);
    }

    @Override
    public List<Map<String, Object>> getUnitsWithLearningStats(Long studentId, Long courseId) {
        if (studentId == null) {
            throw new BusinessException("studentId 不能为空");
        }
        if (courseId == null) {
            throw new BusinessException("courseId 不能为空");
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
            throw new BusinessException("studentId 不能为空");
        }
        if (unitId == null) {
            throw new BusinessException("unitId 不能为空");
        }

        Map<String, Object> content = new HashMap<>();
        content.put("unitId", unitId);

        logger.debug("获取单元内容 - 学生ID: {}, 单元ID: {}", studentId, unitId);

        // 获取课文列表
        QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
        textWrapper.and(w -> w.eq("unit_id", unitId)
                .or().inSql("id", "SELECT text_id FROM text_units WHERE unit_id = " + unitId))
                .eq("deleted", 0);
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
    @UseMaster
    public Map<String, Object> getTextDetailWithAnnotation(Long studentId, Long textId, Long assignmentId) {
        if (studentId == null) {
            throw new BusinessException("studentId 不能为空");
        }
        if (textId == null) {
            throw new BusinessException("textId 不能为空");
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
            // Keep the article view counter in the student detail response. The
            // controller increments it before loading this map, so callers see
            // the current value instead of falling back to zero in the client.
            detail.put("viewCount", text.getViewCount() != null ? text.getViewCount() : 0);
            detail.put("isPublic", text.getIsPublic());
            detail.put("isFeatured", text.getIsFeatured());
            List<Long> relatedUnitIds = textUnitMapper.selectUnitIdsByTextId(textId);
            detail.put("unitIds", relatedUnitIds == null ? Collections.emptyList() : relatedUnitIds);

            // 填充上传者（作者）信息：无上传者标记为官方内容
            authorEnricher.enrichText(text);
            detail.put("author", text.getAuthor());

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
            detail.put("readTimeSeconds", getTextReadTimeSecondsForSpecificText(studentId, textId, assignmentId));
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
        recordLearning(studentId, type, wordId, duration, textId, assignmentId, null);
    }

    @Override
    @Transactional
    public void recordLearning(Long studentId, String type, Long wordId, Integer duration, Long textId,
            Long assignmentId, String contentVersion) {
        LearningRecord record = new LearningRecord();
        record.setStudentId(studentId);
        record.setAssignmentId(assignmentId);

        // 设置wordId（word_id 列为 DEFAULT NULL，无外键约束，允许为 null）
        if (wordId != null) {
            record.setWordId(wordId);
        }

        // 设置课文ID
        record.setTextId(textId);

        record.setRecordType(type);
        record.setDurationSeconds(duration != null && duration > 0 ? duration : 1);
        record.setCorrectCount(0);
        record.setTotalCount(1);
        record.setCreatedTime(LocalDateTime.now());

        learningRecordMapper.insert(record);

        // 版本切换时更新最近的 TextLearningRecord
        if ("TEXT_VIEW".equals(type) && textId != null && contentVersion != null && !contentVersion.isEmpty()) {
            updateLatestTextRecordVersion(studentId, textId, contentVersion);
        }

        logger.debug("记录学习活动: 学生={}, 类型={}, 时长={}秒{}",
                studentId, type, duration,
                textId != null ? ", 课文ID=" + textId
                        : wordId != null ? ", 单词ID=" + wordId : assignmentId != null ? ", 任务ID=" + assignmentId : "");
    }

    private void updateLatestTextRecordVersion(Long studentId, Long textId, String contentVersion) {
        QueryWrapper<TextLearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .eq("text_id", textId)
                .eq("is_completed", 0)
                .orderByDesc("start_time")
                .last("LIMIT 1");
        TextLearningRecord tlr = textLearningRecordMapper.selectOne(wrapper);
        if (tlr != null) {
            tlr.setContentVersion(contentVersion);
            textLearningRecordMapper.updateById(tlr);
        }
    }

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

        // 事件驱动: 触发活动数据采集
        campaignStatsService.collectUserTasksByTypeAsync(studentId, TaskType.WORD_LEARNING);
    }

    @Override
    public Long startReadingSession(Long studentId, Long textId, Long assignmentId) {
        return startReadingSession(studentId, textId, assignmentId, null);
    }

    @Override
    public Long startReadingSession(Long studentId, Long textId, Long assignmentId, String contentVersion) {
        TextLearningRecord record = new TextLearningRecord();
        record.setStudentId(studentId);
        record.setTextId(textId);
        record.setAssignmentId(assignmentId);
        record.setStartTime(LocalDateTime.now());
        record.setIsCompleted(0);
        if (contentVersion != null && !contentVersion.isEmpty()) {
            record.setContentVersion(contentVersion);
        }

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

            // 计算时长（秒），防止 long→int 溢出
            long duration = java.time.Duration.between(record.getStartTime(), now).getSeconds();
            int durationSeconds = (int) Math.min(duration, Integer.MAX_VALUE);
            record.setDurationSeconds(durationSeconds);
            record.setUpdatedTime(now);

            textLearningRecordMapper.updateById(record);

            // ⚠️ 不再在此处同步写入 learning_records.TEXT_VIEW：
            // 前端 InteractiveText.vue 已通过 /record/learning 周期性保存增量时长到 learning_records，
            // 此处再用墙上时钟时长（startTime→now）写入会与已保存时长重叠，导致阅读时长重复计算。
            // text_learning_records 表仍保留会话级记录用于其他用途。
            // 事件驱动: 触发活动数据采集（采集时会从 learning_records 汇总 TEXT_VIEW 时长）
            campaignStatsService.collectUserTasksByTypeAsync(record.getStudentId(), TaskType.READING_TIME);
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

        // 事件驱动: 触发活动数据采集
        campaignStatsService.collectUserTasksByTypeAsync(studentId, TaskType.WORD_LEARNING);
    }

    @Override
    public Map<String, Object> getTodayLearningStats(Long studentId) {
        if (studentId == null) {
            throw new BusinessException("studentId 不能为空");
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
            throw new BusinessException("studentId 不能为空");
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
            throw new BusinessException("参数不能为空");
        }
        Long studentId = params.get("studentId") != null ? Long.valueOf(params.get("studentId").toString()) : null;
        if (studentId == null) {
            throw new BusinessException("studentId 不能为空");
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
            throw new BusinessException("studentId 不能为空");
        }
        if (courseId == null) {
            throw new BusinessException("courseId 不能为空");
        }

        Map<String, Object> stats = new HashMap<>();

        // 查询课程下的所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);
        if (units == null) {
            units = new ArrayList<>();
        }

        stats.put("totalUnits", 0);
        stats.put("totalWords", 0);
        stats.put("learnedWords", 0);
        stats.put("progress", 0.0);
        stats.put("studyTime", 0);

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

        // 计算总单词数：需要过滤已删除的单词，并获取有效 wordId 集合
        int totalWords = 0;
        Set<Long> validWordIds = new HashSet<>();
        if (!wordIds.isEmpty()) {
            QueryWrapper<Word> wordWrapper = new QueryWrapper<>();
            wordWrapper.in("id", wordIds)
                    .eq("deleted", 0)
                    .select("id");
            List<Word> validWords = wordMapper.selectList(wordWrapper);
            if (validWords != null) {
                for (Word w : validWords) {
                    validWordIds.add(w.getId());
                }
            }
            totalWords = validWordIds.size();
        }
        stats.put("totalWords", totalWords);

        // 学生已掌握的单词（只统计有效未删除的单词）
        int learnedWords = 0;
        if (!validWordIds.isEmpty()) {
            QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
            vocabWrapper.eq("student_id", studentId)
                    .in("word_id", validWordIds)
                    .ne("mastery_level", "NEW");
            learnedWords = Math.toIntExact(vocabularyBookMapper.selectCount(vocabWrapper));
        }
        stats.put("learnedWords", Math.min(learnedWords, totalWords));

        // 学习进度，clamp 到 [0, 100]，保留两位小数
        double progress = totalWords > 0 ? (double) Math.min(learnedWords, totalWords) / totalWords * 100 : 0.0;
        stats.put("progress", Math.round(Math.max(0.0, Math.min(progress, 100.0)) * 100.0) / 100.0);

        // 学习时间
        long studySeconds = calculateCourseStudySeconds(studentId, courseId);
        stats.put("studyTime", (int) Math.min(studySeconds / 60, Integer.MAX_VALUE));

        return stats;
    }

    @Override
    public Map<String, Object> getAllCoursesLearningStats(Long studentId) {
        if (studentId == null) {
            throw new BusinessException("studentId 不能为空");
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
            throw new BusinessException("studentId 不能为空");
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
            throw new BusinessException("studentId 不能为空");
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
            throw new BusinessException("studentId 不能为空");
        }

        Map<String, Object> achievements = new HashMap<>();

        // 连续学习天数
        int consecutiveDays = calculateConsecutiveDays(studentId);
        achievements.put("consecutiveDays", consecutiveDays);

        // 总学习记录数（粗略估算为单词数，后续可精确查询生词本）
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);
        long totalRecords = learningRecordMapper.selectCount(wrapper);
        achievements.put("totalWordsLearned", (int) Math.min(totalRecords, Integer.MAX_VALUE));

        // 总学习时间（小时）
        QueryWrapper<LearningRecord> timeWrapper = new QueryWrapper<>();
        timeWrapper.eq("student_id", studentId)
                .select("COALESCE(SUM(duration_seconds), 0) as total_time");

        List<Map<String, Object>> timeResults = learningRecordMapper.selectMaps(timeWrapper);
        long totalStudySeconds = 0;
        if (timeResults != null && !timeResults.isEmpty()) {
            Map<String, Object> timeResult = timeResults.get(0);
            Number totalTime = (Number) timeResult.get("total_time");
            totalStudySeconds = totalTime != null ? totalTime.longValue() : 0;
        }
        achievements.put("totalStudyHours", (int) Math.min(totalStudySeconds / 3600, Integer.MAX_VALUE));

        // 已完成任务数（统计该学生在 learning_records 中涉及的不同 assignment_id 数量）
        try {
            QueryWrapper<LearningRecord> arWrapper = new QueryWrapper<>();
            arWrapper.eq("student_id", studentId);
            arWrapper.isNotNull("assignment_id");
            arWrapper.gt("assignment_id", 0);
            arWrapper.select("DISTINCT assignment_id");
            long completedAssignments = learningRecordMapper.selectCount(arWrapper);
            achievements.put("completedAssignments", (int) completedAssignments);
        } catch (Exception e) {
            achievements.put("completedAssignments", 0);
        }

        // 生词本中已掌握的单词数
        try {
            QueryWrapper<com.english.entity.VocabularyBook> vbWrapper = new QueryWrapper<>();
            vbWrapper.eq("student_id", studentId);
            vbWrapper.ge("mastery_level", 4);
            long masteredWords = vocabularyBookMapper.selectCount(vbWrapper);
            achievements.put("masteredWords", (int) masteredWords);
        } catch (Exception e) {
            achievements.put("masteredWords", 0);
        }

        // 生词本中的总单词数
        try {
            QueryWrapper<com.english.entity.VocabularyBook> vbTotalWrapper = new QueryWrapper<>();
            vbTotalWrapper.eq("student_id", studentId);
            long totalVocabWords = vocabularyBookMapper.selectCount(vbTotalWrapper);
            achievements.put("totalVocabWords", (int) totalVocabWords);
        } catch (Exception e) {
            achievements.put("totalVocabWords", 0);
        }

        // 生成分级成就列表（所有可能成就 + 是否已解锁状态）
        List<Map<String, Object>> achievementList = new ArrayList<>();
        int completedAssignments = (int) achievements.getOrDefault("completedAssignments", 0);
        int masteredWordsCount = (int) achievements.getOrDefault("masteredWords", 0);
        int totalVocab = (int) achievements.getOrDefault("totalVocabWords", 0);

        // 打卡类成就
        achievementList.add(buildAchievement("firstStudy", "初入学海", "完成第一次线上学习任务", "📖", totalRecords > 0));
        achievementList.add(buildAchievement("day1", "学习启程", "开启属于你的学习之旅", "🚀", consecutiveDays >= 1));
        achievementList.add(buildAchievement("day3", "勤学萌新", "连续3天准时打卡学习", "🌱", consecutiveDays >= 3));
        achievementList.add(buildAchievement("day7", "自律达人", "连续一周坚持学习不断档", "🏆", consecutiveDays >= 7));
        achievementList.add(buildAchievement("day15", "学习先锋", "连续半月稳定学习超厉害", "⭐", consecutiveDays >= 15));
        achievementList.add(buildAchievement("day30", "月度学霸", "整整三十天从未间断", "👑", consecutiveDays >= 30));
        achievementList.add(buildAchievement("day60", "深耕学者", "两个月持之以恒坚持学习", "🎓", consecutiveDays >= 60));
        achievementList.add(buildAchievement("day90", "学识积淀", "百日坚持，厚积薄发", "💎", consecutiveDays >= 90));

        // 单词类成就
        achievementList.add(buildAchievement("word50", "单词入门", "累计熟记50个实用单词", "📝", masteredWordsCount >= 50));
        achievementList.add(buildAchievement("word100", "百词小能手", "成功攻克100个单词大关", "💪", masteredWordsCount >= 100));
        achievementList.add(buildAchievement("word200", "词汇新星", "累计背诵单词突破200", "🌟", masteredWordsCount >= 200));
        achievementList.add(buildAchievement("word500", "词汇达人", "掌握五百单词，词汇量起飞", "🔥", masteredWordsCount >= 500));
        achievementList.add(buildAchievement("word1000", "单词宗师", "千词在手，外语无忧", "🏅", masteredWordsCount >= 1000));
        achievementList.add(buildAchievement("word2000", "词汇大神", "两千词汇储备，从容应对", "🎖️", masteredWordsCount >= 2000));

        // 任务类成就
        achievementList.add(buildAchievement("task10", "小有收获", "累计完成10项学习任务", "✅", completedAssignments >= 10));
        achievementList.add(buildAchievement("task30", "稳步前行", "累计完成30项学习任务", "📊", completedAssignments >= 30));
        achievementList.add(buildAchievement("task50", "实干学习者", "累计完成50项学习任务", "💼", completedAssignments >= 50));
        achievementList.add(buildAchievement("task100", "任务达人", "圆满完成百项学习任务", "🎯", completedAssignments >= 100));
        achievementList.add(buildAchievement("task200", "学习劳模", "累计完成200项学习任务", "🏭", completedAssignments >= 200));

        achievements.put("achievementList", achievementList);
        achievements.put("unlockedCount", achievementList.stream().filter(a -> (boolean) a.get("unlocked")).count());
        achievements.put("totalAchievements", achievementList.size());

        return achievements;
    }

    private Map<String, Object> buildAchievement(String key, String title, String desc, String icon, boolean unlocked) {
        Map<String, Object> ach = new HashMap<>();
        ach.put("key", key);
        ach.put("title", title);
        ach.put("desc", desc);
        ach.put("icon", icon);
        ach.put("unlocked", unlocked);
        return ach;
    }

    @Override
    public Map<String, Object> getLearningRanking(Long studentId, String type, Long targetId) {
        if (studentId == null) {
            throw new BusinessException("studentId 不能为空");
        }

        Map<String, Object> ranking = new HashMap<>();
        String scopeType = type != null ? type : "global";
        ranking.put("type", scopeType);

        List<Map<String, Object>> rankingData;

        if ("class".equals(scopeType) && targetId != null) {
            // 班级内排名
            List<StudentClass> classStudents = studentClassMapper.selectStudentsByClassId(targetId);
            List<Long> studentIds = classStudents.stream()
                    .map(StudentClass::getStudentId)
                    .collect(Collectors.toList());
            if (studentIds.isEmpty()) {
                ranking.put("studentRank", 0);
                ranking.put("totalStudents", 0);
                ranking.put("percentile", 0.0);
                ranking.put("masteredWords", 0L);
                return ranking;
            }
            rankingData = vocabularyBookMapper.selectMasteredWordRankingByStudentIds(studentIds);
        } else if ("course".equals(scopeType) && targetId != null) {
            // 课程内排名：先找该课程下所有班级，再找这些班级里的所有学生
            QueryWrapper<com.english.entity.Class> classWrapper = new QueryWrapper<>();
            classWrapper.select("id").eq("course_id", targetId);
            List<Object> classIdObjs = classMapper.selectObjs(classWrapper);
            if (classIdObjs.isEmpty()) {
                ranking.put("studentRank", 0);
                ranking.put("totalStudents", 0);
                ranking.put("percentile", 0.0);
                ranking.put("masteredWords", 0L);
                return ranking;
            }
            List<Long> classIds = classIdObjs.stream()
                    .map(o -> ((Number) o).longValue())
                    .collect(Collectors.toList());
            List<Long> studentIds = studentClassMapper.selectStudentIdsByClassIds(classIds);
            if (studentIds.isEmpty()) {
                ranking.put("studentRank", 0);
                ranking.put("totalStudents", 0);
                ranking.put("percentile", 0.0);
                ranking.put("masteredWords", 0L);
                return ranking;
            }
            rankingData = vocabularyBookMapper.selectMasteredWordRankingByStudentIds(studentIds);
        } else {
            // 全局排名
            rankingData = vocabularyBookMapper.selectAllMasteredWordRanking();
        }

        // 在排名表中定位当前学生
        long masteredWords = 0;
        int studentRank = 0;
        int totalInRanking = rankingData.size();

        for (int i = 0; i < rankingData.size(); i++) {
            Map<String, Object> row = rankingData.get(i);
            Long sid = ((Number) row.get("student_id")).longValue();
            if (sid.equals(studentId)) {
                studentRank = i + 1;
                masteredWords = ((Number) row.get("mastered_count")).longValue();
                break;
            }
        }

        // 学生不在排名表中 → 掌握词数为 0，排最后
        int totalStudents = totalInRanking;
        if (studentRank == 0) {
            studentRank = totalInRanking + 1;
            totalStudents = totalInRanking + 1;
        }

        double percentile = totalStudents > 1
                ? Math.round((1.0 - (double) studentRank / (double) totalStudents) * 10000.0) / 100.0
                : 100.0;

        ranking.put("studentRank", studentRank);
        ranking.put("totalStudents", totalStudents);
        ranking.put("percentile", percentile);
        ranking.put("masteredWords", masteredWords);

        return ranking;
    }

    @Override
    public Map<String, Object> exportLearningData(Long studentId, Map<String, Object> params) {
        if (studentId == null) {
            throw new BusinessException("studentId 不能为空");
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
            throw new BusinessException("assignmentId 和 studentId 不能为空");
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
            // 保留原始秒数，供前端精确展示；duration 继续作为旧接口的分钟字段。
            recordMap.put("durationSeconds", record.getDurationSeconds());
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

        long correct = records.stream()
                .mapToLong(r -> r.getCorrectCount() != null ? r.getCorrectCount() : 0)
                .sum();
        long total = records.stream()
                .mapToLong(r -> r.getTotalCount() != null ? r.getTotalCount() : 0)
                .sum();

        return total > 0 ? (double) correct / total * 100 : 0.0;
    }

    @Override
    public int countActiveDays(Long studentId, int lastNDays) {
        LocalDateTime since = LocalDate.now().minusDays(lastNDays).atStartOfDay();
        return learningRecordMapper.countActiveDaysSince(studentId, since);
    }

    @Override
    public int getConsecutiveDays(Long studentId) {
        return calculateConsecutiveDays(studentId);
    }

    @Override
    public long getStudyMinutesInRange(Long studentId, LocalDateTime from, LocalDateTime to) {
        long totalSeconds = learningRecordMapper.sumDurationSecondsBetween(studentId, from, to);
        return totalSeconds / 60;
    }

    @Override
    public long getReadingMinutesInRange(Long studentId, LocalDateTime from, LocalDateTime to) {
        long totalSeconds = learningRecordMapper.sumReadingSecondsBetween(studentId, from, to);
        return totalSeconds / 60;
    }

    private int calculateConsecutiveDays(Long studentId) {
        List<LocalDate> dates = learningRecordMapper.selectDistinctStudyDates(studentId);
        if (dates == null || dates.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        int consecutive = 0;
        for (LocalDate date : dates) {
            if (date.equals(today.minusDays(consecutive))) {
                consecutive++;
            } else {
                break;
            }
        }
        return consecutive;
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
    private long calculateCourseStudySeconds(Long studentId, Long courseId) {
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId).select("id");
        List<Unit> units = unitMapper.selectList(unitWrapper);
        if (units == null || units.isEmpty()) {
            return 0L;
        }

        List<Long> unitIds = units.stream()
                .map(Unit::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (unitIds.isEmpty()) {
            return 0L;
        }

        long totalSeconds = 0L;

        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.in("unit_id", unitIds).select("word_id");
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);
        if (unitWords != null && !unitWords.isEmpty()) {
            List<Long> wordIds = unitWords.stream()
                    .map(UnitWord::getWordId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!wordIds.isEmpty()) {
                QueryWrapper<LearningRecord> wordRecordWrapper = new QueryWrapper<>();
                wordRecordWrapper.eq("student_id", studentId).in("word_id", wordIds);
                List<LearningRecord> wordRecords = learningRecordMapper.selectList(wordRecordWrapper);
                if (wordRecords != null && !wordRecords.isEmpty()) {
                    totalSeconds += wordRecords.stream()
                            .mapToLong(record -> record.getDurationSeconds() != null ? record.getDurationSeconds() : 0)
                            .sum();
                }
            }
        }

        QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
        String unitIdSql = unitIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        textWrapper.and(w -> w.in("unit_id", unitIds)
                .or().inSql("id", "SELECT text_id FROM text_units WHERE unit_id IN (" + unitIdSql + ")"))
                .eq("deleted", 0).select("id");
        List<com.english.entity.Text> texts = textMapper.selectList(textWrapper);
        if (texts != null && !texts.isEmpty()) {
            List<Long> textIds = texts.stream()
                    .map(com.english.entity.Text::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!textIds.isEmpty()) {
                QueryWrapper<LearningRecord> textRecordWrapper = new QueryWrapper<>();
                textRecordWrapper.eq("student_id", studentId)
                        .eq("record_type", "TEXT_VIEW")
                        .in("text_id", textIds);
                List<LearningRecord> textRecords = learningRecordMapper.selectList(textRecordWrapper);
                if (textRecords != null && !textRecords.isEmpty()) {
                    totalSeconds += textRecords.stream()
                            .mapToLong(record -> record.getDurationSeconds() != null ? record.getDurationSeconds() : 0)
                            .sum();
                }
            }
        }

        return totalSeconds;
    }

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
        textWrapper.and(w -> w.eq("unit_id", unitId)
                .or().inSql("id", "SELECT text_id FROM text_units WHERE unit_id = " + unitId))
                .eq("deleted", 0);
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

        // 使用标准整除转换为分钟（与其他方法保持一致）
        // 防止整数溢出
        return (int) Math.min(totalSeconds / 60, Integer.MAX_VALUE);
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

        // 如果只有单词或只有课文，直接返回对应进度（保留两位小数）
        if (totalWords == 0) {
            return Math.round(textProgress * 100.0) / 100.0;
        }
        if (totalTexts == 0) {
            return Math.round(wordProgress * 100.0) / 100.0;
        }

        // 综合进度：单词和课文各占50%，保留两位小数
        return Math.round((wordProgress * 0.5 + textProgress * 0.5) * 100.0) / 100.0;
    }

    /**
     * 计算学生在单元中已阅读的课文数量
     */
    private int calculateReadTextsInUnit(Long studentId, Long unitId) {
        // 查询单元下的所有非删除课文
        QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
        textWrapper.and(w -> w.eq("unit_id", unitId)
                .or().inSql("id", "SELECT text_id FROM text_units WHERE unit_id = " + unitId))
                .eq("deleted", 0);
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
        return (int) Math.min(getTextReadTimeSecondsForSpecificText(studentId, textId, assignmentId) / 60,
                Integer.MAX_VALUE);
    }

    @Override
    public long getTextReadTimeSecondsForSpecificText(Long studentId, Long textId, Long assignmentId) {
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

        return Math.max(totalSeconds, 0);
    }

    /**
     * 标注课文中的生词
     */
    private String annotateTextWithVocabulary(Long studentId, String content) {
        // 简单实现：返回原内容，可以后续完善标注逻辑
        return content;
    }
}
