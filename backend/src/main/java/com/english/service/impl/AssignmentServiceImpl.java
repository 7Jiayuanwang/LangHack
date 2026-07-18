package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.*;
import com.english.entity.Class;
import com.english.mapper.*;
import com.english.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.english.exception.BusinessException;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AssignmentServiceImpl extends ServiceImpl<AssignmentMapper, Assignment> implements AssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(AssignmentServiceImpl.class);

    @Autowired
    private AssignmentMapper assignmentMapper;

    @Autowired
    private TextExerciseMapper textExerciseMapper;

    @Autowired
    private UnitWordMapper unitWordMapper;

    @Autowired
    private VocabularyBookMapper vocabularyBookMapper;

    @Autowired
    private LearningRecordMapper learningRecordMapper;

    @Autowired
    private CourseService courseService;

    @Autowired
    private StudentClassService studentClassService;

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private ClassMapper classMapper;

    @Autowired
    private UnitMapper unitMapper;

    @Autowired
    private WordMapper wordMapper;

    /**
     * 获取未删除的任务（替代 getById 以确保软删除过滤）
     */
    private Assignment getActiveAssignment(Long id) {
        if (id == null) return null;
        // @TableLogic 注解已在 getById() 时自动过滤 deleted=0，无需手动添加
        return getById(id);
    }

    @Autowired
    private StudentClassesService studentClassesService;

    @Autowired
    private LearningRecordsService learningRecordsService;

    @Autowired
    private TextExerciseService textExerciseService;

    @Autowired
    private StudentExerciseAnswerMapper studentExerciseAnswerMapper;

    @Autowired
    private ScoreRecordService scoreRecordService;

    // 在类定义的开头添加这些常量
    private static final int MIN_TEXT_READ_TIME_MINUTES = 10; // 10分钟（getTextReadTimeForSpecificText返回分钟）
    private static final double COMPLETION_THRESHOLD = 80.0; // 完成阈值80%

    @Override
    public Map<String, Object> getAssignmentsByPage(Map<String, Object> params) {
        int page = params.get("page") != null ? ((Number) params.get("page")).intValue() : 1;
        int size = params.get("size") != null ? ((Number) params.get("size")).intValue() : 10;
        Long teacherId = params.get("teacherId") != null ? Long.valueOf(params.get("teacherId").toString()) : null;
        Long classId = params.get("classId") != null ? Long.valueOf(params.get("classId").toString()) : null;
        String keyword = (String) params.get("keyword");

        Page<Assignment> pageParam = new Page<>(page, size);
        IPage<Assignment> pageResult = assignmentMapper.selectAssignmentsWithDetails(
                pageParam, teacherId, classId, keyword);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    public Assignment getAssignmentById(Long id) {
        return assignmentMapper.selectAssignmentWithDetails(id);
    }

    @Override
    @Transactional
    public void createAssignment(Assignment assignment) {
        // unit_id在数据库中是NOT NULL，确保有值
        if (assignment.getUnitId() == null) {
            throw new BusinessException("单元ID不能为空");
        }

        // 默认状态为PUBLISHED，确保学生可以看到任务
        if (assignment.getStatus() == null) {
            assignment.setStatus("PUBLISHED");
        }

        save(assignment);
    }

    @Override
    @Transactional
    public void updateAssignment(Assignment assignment) {
        updateById(assignment);
    }

    @Override
    @Transactional
    public void deleteAssignment(Long id) {
        // 软删除：@TableLogic 会将 removeById 转换为 UPDATE SET deleted=1
        removeById(id);
    }

    @Override
    public Map<String, Object> getTeacherStats(Long teacherId) {
        Map<String, Object> stats = assignmentMapper.selectTeacherStats(teacherId);
        if (stats == null) {
            stats = new HashMap<>();
        }

        // 确保返回的数值不为null
        stats.putIfAbsent("total_classes", 0);
        stats.putIfAbsent("total_students", 0);
        stats.putIfAbsent("total_assignments", 0);
        stats.putIfAbsent("completed_tasks", 0);

        return stats;
    }

    @Override
    public List<Map<String, Object>> getTeacherClassesWithStats(Long teacherId) {
        List<Map<String, Object>> classes = assignmentMapper.selectTeacherClassesWithStats(teacherId);

        // 为每个班级添加学生数量
        for (Map<String, Object> classMap : classes) {
            Object idObj = classMap.get("id");
            Long classId = null;
            if (idObj != null) {
                classId = idObj instanceof Long ? (Long) idObj : Long.valueOf(idObj.toString());
            }
            if (classId != null) {
                classMap.put("studentCount", getStudentCount(classId));
            } else {
                classMap.put("studentCount", 0);
            }
        }

        return classes;
    }

    /**
     * 获取班级学生数量
     */
    private Integer getStudentCount(Long classId) {
        if (classId == null)
            return 0;
        try {
            List<StudentClass> studentClasses = studentClassService.getStudentsByClassId(classId);
            return studentClasses.size();
        } catch (Exception e) {
            logger.error("获取班级学生数量失败", e);
            return 0;
        }
    }

    @Override
    public List<Assignment> getRecentAssignments(Long teacherId, Integer limit) {
        return assignmentMapper.selectRecentAssignments(teacherId, limit);
    }

    @Override
    public List<Map<String, Object>> getRecentAssignmentsWithStats(Long teacherId, Integer limit) {
        // 获取最近的任务列表
        List<Assignment> assignments = assignmentMapper.selectRecentAssignments(teacherId, limit);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Assignment assignment : assignments) {
            Map<String, Object> assignmentMap = new HashMap<>();
            assignmentMap.put("id", assignment.getId());
            assignmentMap.put("title", assignment.getTitle());
            assignmentMap.put("deadline", assignment.getDeadline());
            assignmentMap.put("unitId", assignment.getUnitId());

            // 获取班级信息
            Long classId = assignment.getClassId();
            Class classInfo = classMapper.selectById(classId);
            String className = classInfo != null ? classInfo.getName() : "未知班级";
            assignmentMap.put("className", className);

            // 获取学生总数
            Integer studentCount = getStudentCount(assignment.getClassId());
            assignmentMap.put("studentCount", studentCount);

            // 获取完成人数
            Integer completedCount = getCompletedCount(assignment.getId());
            assignmentMap.put("completedCount", completedCount);

            result.add(assignmentMap);
        }

        return result;
    }

    @Override
    public boolean validateTeacherPermission(Long teacherId, Long assignmentId) {
        Assignment assignment = getActiveAssignment(assignmentId);
        return assignment != null && java.util.Objects.equals(teacherId, assignment.getCreatedBy());
    }

    // ========== 学生端方法实现 ==========

    @Override
    public Map<String, Object> getStudentAssignmentsWithStatus(List<Long> classIds, Long studentId, String status) {
        QueryWrapper<Assignment> wrapper = new QueryWrapper<>();

        // 防止空集合导致的SQL语法错误
        if (classIds != null && !classIds.isEmpty()) {
            wrapper.in("class_id", classIds);
        } else {
            // 如果没有班级ID，返回空结果
            Map<String, Object> result = new HashMap<>();
            result.put("assignments", new ArrayList<>());
            result.put("total", 0);
            return result;
        }

        // 学生只能看到已发布的任务，过滤 DRAFT/CLOSED 和已删除的
        wrapper.eq("status", "PUBLISHED");
        wrapper.eq("deleted", 0);

        wrapper.orderByDesc("created_time");

        List<Assignment> assignments = list(wrapper);

        // 为每个任务添加详细信息
        List<Map<String, Object>> enrichedAssignments = new ArrayList<>();
        for (Assignment assignment : assignments) {
            Map<String, Object> assignmentMap = new HashMap<>();

            // 基本信息
            assignmentMap.put("id", assignment.getId());
            assignmentMap.put("title", assignment.getTitle());
            assignmentMap.put("description", assignment.getDescription());
            assignmentMap.put("deadline", assignment.getDeadline());
            assignmentMap.put("createdTime", assignment.getCreatedTime());
            assignmentMap.put("classId", assignment.getClassId());
            assignmentMap.put("unitId", assignment.getUnitId());

            // 获取班级名称
            Class clazz = classMapper.selectById(assignment.getClassId());
            assignmentMap.put("className", clazz != null ? clazz.getName() : "未知班级");

            // 获取单元名称
            Unit unit = unitMapper.selectById(assignment.getUnitId());
            assignmentMap.put("unitName", unit != null ? unit.getName() : "未知单元");

            // 计算学习进度
            double progress = calculateAssignmentProgress(assignment.getId(), studentId);
            assignmentMap.put("progress", Math.round(progress));

            // 计算任务状态
            String assignmentStatus = calculateAssignmentStatus(progress, assignment.getDeadline(),
                    assignment, studentId);
            if (status != null && !status.isEmpty() && !status.equals(assignmentStatus)) {
                continue; // 如果指定了状态筛选，跳过不匹配的任务
            }
            assignmentMap.put("status", assignmentStatus);

            // 统计信息
            List<Word> assignmentWords = getAssignmentWords(assignment);
            List<com.english.entity.Text> assignmentTexts = getAssignmentTexts(assignment);

            assignmentMap.put("totalWords", assignmentWords.size());
            assignmentMap.put("learnedWords", calculateWordsLearnedInAssignment(assignment.getId(), studentId));
            assignmentMap.put("totalTexts", assignmentTexts.size());
            assignmentMap.put("readTexts", calculateTextsReadInAssignment(assignment.getId(), studentId));
            assignmentMap.put("studyTime", calculateStudyTimeInAssignment(assignment.getId(), studentId));
            assignmentMap.put("assignmentType", getAssignmentType(assignment));

            enrichedAssignments.add(assignmentMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("assignments", enrichedAssignments);
        result.put("total", enrichedAssignments.size());

        return result;
    }

    @Override
    public Map<String, Object> getAssignmentDetailWithProgress(Long assignmentId, Long studentId) {
        Assignment assignment = getActiveAssignment(assignmentId);
        if (assignment == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("id", assignment.getId());
        detail.put("title", assignment.getTitle());
        detail.put("description", assignment.getDescription());
        detail.put("deadline", assignment.getDeadline());
        detail.put("createdTime", assignment.getCreatedTime());
        detail.put("classId", assignment.getClassId());
        detail.put("unitId", assignment.getUnitId());
        detail.put("createdBy", assignment.getCreatedBy());

        // 添加进度统计逻辑
        double progress = calculateAssignmentProgress(assignmentId, studentId);
        detail.put("progress", progress);
        detail.put("completed", isAssignmentCompleted(assignmentId, studentId));

        // 添加状态计算（与列表视图保持一致）
        String status = calculateAssignmentStatus(progress, assignment.getDeadline(), assignment, studentId);
        detail.put("status", status);

        return detail;
    }

    @Override
    public Map<String, Object> startAssignment(Long assignmentId, Long studentId) {
        Assignment assignment = getActiveAssignment(assignmentId);
        if (assignment == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> startInfo = new HashMap<>();
        startInfo.put("assignmentId", assignmentId);
        startInfo.put("title", assignment.getTitle());
        startInfo.put("description", assignment.getDescription());
        startInfo.put("unitId", assignment.getUnitId());
        startInfo.put("deadline", assignment.getDeadline());
        startInfo.put("startTime", LocalDateTime.now());

        // 获取单元内容
        Map<String, Object> unitContent = getAssignmentContent(assignmentId, studentId);
        startInfo.put("unitContent", unitContent);

        return startInfo;
    }

    @Override
    public Map<String, Object> getAssignmentContent(Long assignmentId, Long studentId) {
        Assignment assignment = getActiveAssignment(assignmentId);
        if (assignment == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> content = new HashMap<>();
        content.put("unitId", assignment.getUnitId());
        content.put("assignmentType", getAssignmentType(assignment));

        // 获取任务指定的课文和单词
        List<com.english.entity.Text> texts = getAssignmentTexts(assignment);
        List<Word> words = getAssignmentWords(assignment);

        // 获取课文信息并添加学生学习状态
        List<Map<String, Object>> textList = new ArrayList<>();
        for (com.english.entity.Text text : texts) {
            Map<String, Object> textMap = new HashMap<>();
            textMap.put("id", text.getId());
            textMap.put("title", text.getTitle());
            textMap.put("content", text.getContent());
            textMap.put("difficulty", text.getDifficulty());
            textMap.put("audioUrl", text.getAudioUrl());

            // 获取这篇课文的阅读时间（独立计算）
            int readTime = learningRecordsService.getTextReadTimeForSpecificText(studentId, text.getId(), assignmentId);
            boolean isRead = readTime > 0; // 有阅读时间就算读过

            boolean readingCompleted = readTime >= MIN_TEXT_READ_TIME_MINUTES;
            boolean exercisesCompleted = checkTextExercisesCompleted(studentId, text.getId(), assignmentId);

            boolean completed;
            String assignmentType = getAssignmentType(assignment);

            if ("TEXTS".equals(assignmentType) || "UNIT".equals(assignmentType)) {
                // 课文任务和单元任务：都需要阅读时间达标且完成习题
                completed = readingCompleted && exercisesCompleted;
            } else {
                // 其他任务（如单词任务）的课文完成判断：只需要有阅读记录
                completed = isRead;
            }

            textMap.put("completed", completed);
            textMap.put("viewed", isRead);
            textMap.put("studyTime", readTime);
            textMap.put("readingCompleted", readingCompleted);
            textMap.put("exercisesCompleted", exercisesCompleted);

            textList.add(textMap);
        }
        content.put("texts", textList);

        // 获取单词详细信息并添加学生学习状态
        List<Map<String, Object>> wordList = new ArrayList<>();
        for (Word word : words) {
            Map<String, Object> wordMap = new HashMap<>();
            wordMap.put("id", word.getId());
            wordMap.put("word", word.getWord());
            wordMap.put("pronunciation", word.getPronunciation());
            wordMap.put("definition", word.getDefinition());
            wordMap.put("exampleSentence", word.getExampleSentence());
            wordMap.put("audioUrl", word.getAudioUrl());
            wordMap.put("partOfSpeech", word.getPartOfSpeech());
            wordMap.put("difficulty", word.getDifficulty());

            // 检查学习记录中的单词学习状态（包括浏览和完成）
            QueryWrapper<LearningRecord> recordWrapper = new QueryWrapper<>();
            recordWrapper.eq("student_id", studentId)
                    .eq("assignment_id", assignmentId)
                    .and(wrapper -> wrapper.eq("record_type", "WORD_COMPLETE").or().eq("record_type", "VIEW"))
                    .eq("word_id", word.getId()).isNull("text_id");

            boolean learnedByTask = learningRecordMapper.selectCount(recordWrapper) > 0;

            // 检查是否完成了单词学习（通过WORD_COMPLETE记录）
            QueryWrapper<LearningRecord> completeWrapper = new QueryWrapper<>();
            completeWrapper.eq("student_id", studentId)
                    .eq("assignment_id", assignmentId)
                    .eq("record_type", "WORD_COMPLETE")
                    .eq("word_id", word.getId()).isNull("text_id");

            boolean completedByTask = learningRecordMapper.selectCount(completeWrapper) > 0;

            // 检查学生的生词本状态（作为备选）
            QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
            vocabWrapper.eq("student_id", studentId).eq("word_id", word.getId());
            VocabularyBook vocabBook = vocabularyBookMapper.selectOne(vocabWrapper);

            boolean completed = completedByTask || (vocabBook != null && !"NEW".equals(vocabBook.getMasteryLevel()));
            int reviewCount = vocabBook != null ? vocabBook.getReviewCount() : 0;

            wordMap.put("completed", completed);
            wordMap.put("reviewCount", reviewCount);
            wordMap.put("masteryLevel", vocabBook != null ? vocabBook.getMasteryLevel() : "NEW");
            wordMap.put("learnedByTask", learnedByTask); // 标记是否通过任务学习过
            wordMap.put("completedByTask", completedByTask); // 标记是否通过任务完成

            wordList.add(wordMap);
        }
        content.put("words", wordList);

        return content;
    }

    @Override
    @Transactional
    public void completeAssignment(Long assignmentId, Long studentId, Map<String, Object> completionData) {
        // 检查是否已提交（防止重复提交）
        QueryWrapper<LearningRecord> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("student_id", studentId)
                .eq("assignment_id", assignmentId)
                .eq("record_type", "COMPLETE");
        long existingCount = learningRecordMapper.selectCount(checkWrapper);
        if (existingCount > 0) {
            throw new BusinessException("任务已提交，请勿重复提交");
        }

        // 记录任务完成的学习记录
        LearningRecord record = new LearningRecord();
        record.setStudentId(studentId);
        record.setAssignmentId(assignmentId);
        record.setWordId(null); // 任务完成记录，不关联具体单词
        record.setRecordType("COMPLETE");
        record.setDurationSeconds(
                completionData.get("studyTime") != null ? ((Number) completionData.get("studyTime")).intValue() * 60
                        : 0); // 分钟转秒
        record.setCorrectCount(completionData.get("completedItems") != null
                ? ((Number) completionData.get("completedItems")).intValue()
                : 0);
        record.setTotalCount(
                completionData.get("totalItems") != null ? ((Number) completionData.get("totalItems")).intValue() : 1);
        record.setCreatedTime(LocalDateTime.now());

        try {
            learningRecordMapper.insert(record);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException("任务已提交，请勿重复提交");
        }

        // 触发分数计算
        scoreRecordService.updateAssignmentScore(assignmentId, studentId);
    }

    @Override
    public Map<String, Object> getAssignmentProgress(Long assignmentId, Long studentId) {
        Map<String, Object> progress = new HashMap<>();
        progress.put("assignmentId", assignmentId);
        progress.put("studentId", studentId);

        // 实现具体的进度计算逻辑
        // calculateAssignmentProgress 返回 0-100 百分比
        double progressPercent = calculateAssignmentProgress(assignmentId, studentId);
        // completionRate 输出为 0-1 比率，供 ScoreRecordService 分数计算使用
        progress.put("completionRate", progressPercent / 100.0);
        progress.put("wordsLearned", calculateWordsLearnedInAssignment(assignmentId, studentId));
        progress.put("textsRead", calculateTextsReadInAssignment(assignmentId, studentId));
        progress.put("studyTime", calculateStudyTimeInAssignment(assignmentId, studentId));

        return progress;
    }

    @Override
    public boolean isAssignmentInClasses(Long assignmentId, List<Long> classIds) {
        Assignment assignment = getActiveAssignment(assignmentId);
        return assignment != null && classIds.contains(assignment.getClassId());
    }

    /**
     * 计算任务进度
     */
    private double calculateAssignmentProgress(Long assignmentId, Long studentId) {
        Assignment assignment = getActiveAssignment(assignmentId);
        if (assignment == null) {
            return 0.0;
        }

        // 根据任务类型获取要学习的内容
        List<Word> words = getAssignmentWords(assignment);
        List<com.english.entity.Text> texts = getAssignmentTexts(assignment);

        if (words == null) words = new ArrayList<>();
        if (texts == null) texts = new ArrayList<>();

        int totalWords = words.size();
        int totalTexts = texts.size();

        // 计算单词学习进度
        double wordProgress = 0.0;
        if (totalWords > 0) {
            List<Long> wordIds = words.stream().map(Word::getId).collect(Collectors.toList());

            // 通过学习记录或生词本计算学习进度
            int learnedWords = calculateWordsLearnedInAssignment(assignmentId, studentId);
            wordProgress = (double) learnedWords / totalWords * 100;
        }

        // 根据任务类型计算进度
        String assignmentType = getAssignmentType(assignment);

        // 计算课文学习进度
        double textProgress = 0.0;
        if (totalTexts > 0) {
            if ("TEXTS".equals(assignmentType)) {

                int completedTexts = 0;

                for (com.english.entity.Text text : texts) {
                    // 获取这篇课文的阅读时间
                    int textReadTime = learningRecordsService.getTextReadTimeForSpecificText(studentId, text.getId(),
                            assignmentId);
                    boolean readingCompleted = textReadTime >= MIN_TEXT_READ_TIME_MINUTES;

                    // 检查这篇课文的习题完成情况
                    boolean exercisesCompleted = checkTextExercisesCompleted(studentId, text.getId(), assignmentId);

                    // 必须同时满足阅读时间和习题完成
                    if (readingCompleted && exercisesCompleted) {
                        completedTexts++;
                    }
                }
                textProgress = (double) completedTexts / totalTexts * 100;

            } else {
                // 单元任务：基于阅读时间和习题完成情况计算进度（与课文任务相同）
                int completedTexts = 0;

                for (com.english.entity.Text text : texts) {
                    // 检查阅读时间
                    int textReadTime = learningRecordsService.getTextReadTimeForSpecificText(studentId, text.getId(),
                            assignmentId);
                    boolean readingCompleted = textReadTime >= MIN_TEXT_READ_TIME_MINUTES;

                    // 检查习题完成情况
                    boolean exercisesCompleted = checkTextExercisesCompleted(studentId, text.getId(), assignmentId);

                    // 必须同时满足阅读时间和习题完成
                    if (readingCompleted && exercisesCompleted) {
                        completedTexts++;
                    }
                }
                textProgress = (double) completedTexts / totalTexts * 100;
            }
        }

        // 根据任务类型计算总体进度
        double overallProgress;

        switch (assignmentType) {
            case "TEXTS":
                // 课文任务：只看课文进度
                overallProgress = textProgress;
                break;

            case "WORDS":
                // 单词任务：只看单词进度
                overallProgress = wordProgress;
                break;

            case "UNIT":
            default:
                // 单元任务：如果有两者都有则加权平均，否则使用现有的逻辑
                if (totalWords > 0 && totalTexts > 0) {
                    // 两者都有，加权平均
                    overallProgress = (wordProgress + textProgress) / 2;
                } else if (totalWords > 0) {
                    // 只有单词
                    overallProgress = wordProgress;
                } else if (totalTexts > 0) {
                    // 只有课文
                    overallProgress = textProgress;
                } else {
                    // 都没有，返回0
                    overallProgress = 0.0;
                }
                break;
        }

        logger.debug("任务进度计算 - 任务类型: {}, 单词进度: {}%, 课文进度: {}%, 综合进度: {}%",
                getAssignmentType(assignment), wordProgress, textProgress, overallProgress);

        // 返回 0-100 的百分比值，兜底确保不超过 100%
        return Math.min(overallProgress, 100.0);
    }

    /**
     * 检查任务是否完成
     */
    private boolean isAssignmentCompleted(Long assignmentId, Long studentId) {
        Assignment assignment = getActiveAssignment(assignmentId);
        if (assignment == null) {
            return false;
        }

        String assignmentType = getAssignmentType(assignment);
        List<Word> words = getAssignmentWords(assignment);
        List<com.english.entity.Text> texts = getAssignmentTexts(assignment);

        switch (assignmentType) {
            case "TEXTS":
                // 课文任务：需要每篇课文都达到足够的阅读时间并且完成习题
                if (texts.isEmpty()) {
                    return true; // 没有课文的任务算完成
                }

                // 检查每篇指定课文的阅读时间是否达到要求且完成习题
                int completedTexts = 0;
                for (com.english.entity.Text text : texts) {
                    int textReadTime = learningRecordsService.getTextReadTimeForSpecificText(studentId, text.getId(),
                            assignmentId);
                    boolean exercisesCompleted = checkTextExercisesCompleted(studentId, text.getId(), assignmentId);

                    // 必须同时满足阅读时间和习题完成
                    if (textReadTime >= MIN_TEXT_READ_TIME_MINUTES && exercisesCompleted) {
                        completedTexts++;
                    }
                }

                return completedTexts >= texts.size();

            case "WORDS":
                // 单词任务：需要掌握所有单词
                if (words.isEmpty())
                    return true;

                int masteredWords = 0;
                for (Word word : words) {
                    boolean wordMastered = isWordMastered(studentId, word.getId(), assignmentId);
                    if (wordMastered) {
                        masteredWords++;
                    }
                }
                return masteredWords >= words.size();

            case "UNIT":
            default:
                // 单元任务：需要所有课文和单词都完成
                // 检查课文
                boolean textsCompleted = true;
                if (!texts.isEmpty()) {
                    for (com.english.entity.Text text : texts) {
                        int textReadTime = learningRecordsService.getTextReadTimeForSpecificText(studentId,
                                text.getId(), assignmentId);
                        boolean exercisesCompleted = checkTextExercisesCompleted(studentId, text.getId(), assignmentId);

                        if (!(textReadTime >= MIN_TEXT_READ_TIME_MINUTES && exercisesCompleted)) {
                            textsCompleted = false;
                            break;
                        }
                    }
                }

                // 检查单词
                boolean wordsCompleted = true;
                if (!words.isEmpty()) {
                    for (Word word : words) {
                        if (!isWordMastered(studentId, word.getId(), assignmentId)) {
                            wordsCompleted = false;
                            break;
                        }
                    }
                }

                return textsCompleted && wordsCompleted;
        }
    }

    /**
     * 检查单词是否真正掌握
     */
    private boolean isWordMastered(Long studentId, Long wordId, Long assignmentId) {
        // 1. 检查是否有任务完成记录
        QueryWrapper<LearningRecord> completeWrapper = new QueryWrapper<>();
        completeWrapper.eq("student_id", studentId)
                .eq("assignment_id", assignmentId)
                .eq("record_type", "WORD_COMPLETE")
                .eq("word_id", wordId)
                .isNull("text_id");

        if (learningRecordMapper.selectCount(completeWrapper) > 0) {
            return true;
        }

        // 2. 检查生词本掌握程度（不是NEW）
        QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
        vocabWrapper.eq("student_id", studentId)
                .eq("word_id", wordId)
                .ne("mastery_level", "NEW");

        return vocabularyBookMapper.selectCount(vocabWrapper) > 0;
    }

    /**
     * 计算在任务中学习的单词数量
     * 基于任务实际关联的词集（而非整个单元），避免进度超过 100%
     */
    private int calculateWordsLearnedInAssignment(Long assignmentId, Long studentId) {
        Assignment assignment = getActiveAssignment(assignmentId);
        if (assignment == null) {
            return 0;
        }

        // 使用 getAssignmentWords 获取任务的有效词集（已过滤软删除）
        List<Word> assignmentWords = getAssignmentWords(assignment);
        if (assignmentWords == null || assignmentWords.isEmpty()) {
            return 0;
        }

        List<Long> wordIds = assignmentWords.stream().map(Word::getId).collect(Collectors.toList());

        // 通过学习记录查询学生已学习的单词
        // 查询该任务中的所有单词学习记录（包括浏览和完成）
        QueryWrapper<LearningRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("student_id", studentId)
                .eq("assignment_id", assignmentId)
                .and(wrapper -> wrapper.eq("record_type", "WORD_COMPLETE").or().eq("record_type", "VIEW"))
                .in("word_id", wordIds)
                .isNull("text_id");

        List<LearningRecord> learningRecords = learningRecordMapper.selectList(recordWrapper);

        // 获取有学习记录的单词ID集合，收敛到任务词集范围
        Set<Long> assignmentWordIdSet = new HashSet<>(wordIds);
        Set<Long> learnedWordIds = (learningRecords != null ? learningRecords : Collections.<LearningRecord>emptyList()).stream()
                .map(LearningRecord::getWordId)
                .filter(assignmentWordIdSet::contains)
                .collect(Collectors.toSet());

        logger.debug("任务单词学习统计 - 学生={}, 任务={}, 总单词={}, 学习记录数={}, 已学习单词={}",
                studentId, assignmentId, wordIds.size(), learningRecords != null ? learningRecords.size() : 0, learnedWordIds.size());

        // 如果没有通过学习记录找到学习的单词，回退到生词本查询
        if (learnedWordIds.isEmpty()) {
            QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
            vocabWrapper.eq("student_id", studentId)
                    .in("word_id", wordIds)
                    .ne("mastery_level", "NEW");

            long vocabCount = vocabularyBookMapper.selectCount(vocabWrapper);
            // 确保不超过任务总词数
            return (int) Math.min(vocabCount, wordIds.size());
        }

        // 确保不超过任务总词数
        return Math.min(learnedWordIds.size(), wordIds.size());
    }

    /**
     * 计算在任务中阅读的课文数量（针对整个单元）
     */
    private int calculateTextsReadInAssignment(Long assignmentId, Long studentId) {
        Assignment assignment = getActiveAssignment(assignmentId);
        if (assignment == null) {
            return 0;
        }

        // 获取任务关联的课文
        List<com.english.entity.Text> texts = getAssignmentTexts(assignment);
        int readCount = 0;

        for (com.english.entity.Text text : texts) {
            // 检查这篇课文是否有阅读记录
            QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
            wrapper.eq("student_id", studentId)
                    .eq("assignment_id", assignmentId)
                    .eq("record_type", "TEXT_VIEW")
                    .eq("text_id", text.getId()); // 关键：按课文ID过滤

            if (learningRecordMapper.selectCount(wrapper) > 0) {
                readCount++;
            }
        }

        return readCount;
    }

    /**
     * 计算在任务中阅读指定课文的数量
     */
    private int calculateTextsReadInAssignmentForSpecificTexts(Long assignmentId, Long studentId,
            List<com.english.entity.Text> texts) {
        if (texts.isEmpty()) {
            return 0;
        }

        // 检查每个课文的学习状态
        int readCount = 0;
        for (com.english.entity.Text text : texts) {
            boolean isRead = checkTextReadStatus(studentId, assignmentId, text.getId());
            if (isRead) {
                readCount++;
            }
        }

        return readCount;
    }

    /**
     * 计算在任务中的学习时间（分钟）
     */
    private int calculateStudyTimeInAssignment(Long assignmentId, Long studentId) {
        // 查询该任务的所有学习记录
        QueryWrapper<LearningRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("student_id", studentId)
                .eq("assignment_id", assignmentId);

        List<LearningRecord> records = learningRecordMapper.selectList(recordWrapper);

        // 先求和所有秒数，再除以60转换为分钟（避免逐条除60导致小值截断为0）
        long totalSeconds = records.stream()
                .mapToLong(record -> record.getDurationSeconds() != null ? record.getDurationSeconds() : 0).sum();
        return (int) Math.min(totalSeconds / 60, Integer.MAX_VALUE);
    }

    /**
     * 计算任务状态（推荐版本）
     */
    private String calculateAssignmentStatus(double progress, LocalDateTime deadline,
            Assignment assignment, Long studentId) {
        LocalDateTime now = LocalDateTime.now();

        if (deadline != null && now.isAfter(deadline)) {
            return "expired";
        }

        // 检查任务是否已完成（使用统一的标准）
        boolean isCompleted = isAssignmentCompleted(assignment.getId(), studentId);
        if (isCompleted) {
            return "completed";
        }

        // 检查是否有任何学习活动（不仅仅是进度）
        // 进度可能为0，但学生可能已经开始学习（例如学习了10秒，但需要600秒才算完成）
        if (progress > 0.0) {
            return "in-progress";
        }

        // 即使进度为0，也检查是否有学习记录
        // 这样可以正确反映学生已经开始学习但还未达到完成标准的情况
        QueryWrapper<LearningRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("student_id", studentId)
                .eq("assignment_id", assignment.getId());

        long recordCount = learningRecordMapper.selectCount(recordWrapper);
        if (recordCount > 0) {
            return "in-progress";
        }

        return "not-started";
    }

    /**
     * 计算单元中的总单词数
     */
    private int calculateTotalWordsInUnit(Long unitId) {
        QueryWrapper<UnitWord> wrapper = new QueryWrapper<>();
        wrapper.eq("unit_id", unitId);
        return Math.toIntExact(unitWordMapper.selectCount(wrapper));
    }

    /**
     * 计算单元中的总课文数
     */
    private int calculateTotalTextsInUnit(Long unitId) {
        QueryWrapper<com.english.entity.Text> wrapper = new QueryWrapper<>();
        wrapper.eq("unit_id", unitId);
        wrapper.eq("deleted", 0);
        return Math.toIntExact(textMapper.selectCount(wrapper));
    }

    /**
     * 检查课文是否已阅读（基于学习记录）
     */
    private boolean checkTextReadStatus(Long studentId, Long assignmentId, Long textId) {
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .eq("assignment_id", assignmentId)
                .eq("record_type", "TEXT_VIEW")
                .eq("text_id", textId); // 现在可以正确使用 text_id 字段

        return learningRecordMapper.selectCount(wrapper) > 0;
    }

    /**
     * 记录课文学习时间（统一的记录方法）
     */
    @Transactional
    public void recordTextLearningTime(Long studentId, Long assignmentId, Long textId, Integer durationSeconds) {
        if (studentId == null || textId == null || durationSeconds == null || durationSeconds <= 0) {
            return;
        }

        // 使用 LearningRecordsService 来记录课文学习
        learningRecordsService.recordTextView(studentId, textId, assignmentId, durationSeconds);
    }

    /**
     * 获取课文阅读时间（分钟）
     */
    private int getTextReadTime(Long studentId, Long assignmentId, Long textId) {
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
                .eq("assignment_id", assignmentId)
                .eq("record_type", "TEXT_VIEW") // 课文学习记录类型
                .eq("text_id", textId);

        List<LearningRecord> records = learningRecordMapper.selectList(wrapper);
        // 先求和所有秒数，再除以60转换为分钟（避免逐条除60导致小值截断为0）
        long totalSeconds = records.stream()
                .mapToLong(record -> record.getDurationSeconds() != null ? record.getDurationSeconds() : 0)
                .sum();
        return (int) (totalSeconds / 60);
    }

    @Override
    public List<Map<String, Object>> getAssignmentProgress(Long assignmentId) {
        Assignment assignment = getActiveAssignment(assignmentId);
        if (assignment == null) {
            return new ArrayList<>();
        }

        // 获取班级中的所有学生
        List<StudentClass> studentClasses = studentClassService.getStudentsByClassId(assignment.getClassId());
        List<Map<String, Object>> progressList = new ArrayList<>();

        for (StudentClass studentClass : studentClasses) {
            Long studentId = studentClass.getStudentId();
            Map<String, Object> studentProgress = new HashMap<>();

            // 基本信息
            studentProgress.put("studentId", studentId);
            studentProgress.put("username",
                    studentClass.getStudent() != null ? studentClass.getStudent().getUsername() : "未知");
            studentProgress.put("realName", studentClass.getStudent() != null
                    ? (studentClass.getStudent().getRealName() != null
                        ? studentClass.getStudent().getRealName()
                        : studentClass.getStudent().getNickname())
                    : "未知");

            // 计算学习进度
            double progress = calculateAssignmentProgress(assignmentId, studentId);
            studentProgress.put("progress", Math.round(progress));

            // 统计信息
            studentProgress.put("wordsLearned", calculateWordsLearnedInAssignment(assignmentId, studentId));
            studentProgress.put("textsRead", calculateTextsReadInAssignment(assignmentId, studentId));
            studentProgress.put("studyTime", calculateStudyTimeInAssignment(assignmentId, studentId));

            // 检查是否完成（复用统一的完成判定方法）
            boolean isCompleted = isAssignmentCompleted(assignmentId, studentId);
            studentProgress.put("completed", isCompleted);

            // 完成时间
            if (isCompleted) {
                // 查询最后一条学习记录作为完成时间
                QueryWrapper<LearningRecord> recordWrapper = new QueryWrapper<>();
                recordWrapper.eq("student_id", studentId)
                        .eq("assignment_id", assignmentId)
                        .orderByDesc("created_time")
                        .last("LIMIT 1");

                LearningRecord lastRecord = learningRecordMapper.selectOne(recordWrapper);
                if (lastRecord != null) {
                    studentProgress.put("completedAt", lastRecord.getCreatedTime());
                }
            }

            // 计算得分（基于进度）
            int score = isCompleted ? (int) Math.round(progress) : 0;
            studentProgress.put("score", score);

            progressList.add(studentProgress);
        }

        return progressList;
    }

    /**
     * 获取任务的完成人数
     */
    private Integer getCompletedCount(Long assignmentId) {
        try {
            List<Map<String, Object>> progressData = getAssignmentProgress(assignmentId);
            return (int) progressData.stream().filter(student -> Boolean.TRUE.equals(student.get("completed"))).count();
        } catch (Exception e) {
            logger.error("获取任务完成人数失败", e);
            return 0;
        }
    }

    /**
     * 获取任务类型
     */
    private String getAssignmentType(Assignment assignment) {
        if (assignment.isTextsTask()) {
            return "TEXTS";
        } else if (assignment.isWordsTask()) {
            return "WORDS";
        } else {
            return "UNIT"; // 默认为单元任务
        }
    }

    /**
     * 获取任务指定的课文列表
     */
    private List<com.english.entity.Text> getAssignmentTexts(Assignment assignment) {
        List<Long> textIds = assignment.getTextIds();

        if (textIds != null && !textIds.isEmpty()) {
            // 课文任务：返回指定的课文（过滤已软删除的）
            QueryWrapper<com.english.entity.Text> wrapper = new QueryWrapper<>();
            wrapper.in("id", textIds).eq("deleted", 0);
            return textMapper.selectList(wrapper);
        } else if ((assignment.isUnitTask() || !assignment.isTextsTask()) && assignment.getUnitId() != null) {
            // 单元任务或其他任务：返回整个单元的课文（过滤已软删除的）
            QueryWrapper<com.english.entity.Text> wrapper = new QueryWrapper<>();
            wrapper.eq("unit_id", assignment.getUnitId()).eq("deleted", 0);
            return textMapper.selectList(wrapper);
        }

        return new ArrayList<>();
    }

    /**
     * 获取任务指定的单词列表
     */
    private List<Word> getAssignmentWords(Assignment assignment) {
        List<Long> wordIds = assignment.getWordIds();

        if (wordIds != null && !wordIds.isEmpty()) {
            // 单词任务：返回指定的单词（过滤已软删除的）
            QueryWrapper<Word> wrapper = new QueryWrapper<>();
            wrapper.in("id", wordIds).eq("deleted", 0);
            List<Word> words = wordMapper.selectList(wrapper);
            // selectList 走 @TableLogic，但 in() 不走，需手动过滤
            return words.stream().filter(w -> w.getDeleted() == null || w.getDeleted() == 0)
                    .collect(Collectors.toList());
        } else if ((assignment.isUnitTask() || !assignment.isWordsTask()) && assignment.getUnitId() != null) {
            // 单元任务或其他任务：返回整个单元的单词
            QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
            unitWordWrapper.eq("unit_id", assignment.getUnitId());
            List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

            if (unitWords == null || unitWords.isEmpty()) {
                return new ArrayList<>();
            }

            List<Long> allWordIds = unitWords.stream()
                    .map(UnitWord::getWordId)
                    .collect(Collectors.toList());

            if (!allWordIds.isEmpty()) {
                QueryWrapper<Word> wrapper = new QueryWrapper<>();
                wrapper.in("id", allWordIds).eq("deleted", 0);
                List<Word> words = wordMapper.selectList(wrapper);
                return words.stream().filter(w -> w.getDeleted() == null || w.getDeleted() == 0)
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }

    /**
     * 检查学生是否完成了指定课文的所有习题
     */
    private boolean checkTextExercisesCompleted(Long studentId, Long textId, Long assignmentId) {
        try {
            // 1. 获取这篇课文的所有习题ID
            QueryWrapper<TextExercise> wrapper = new QueryWrapper<>();
            wrapper.eq("text_id", textId);
            List<TextExercise> textExercises = textExerciseMapper.selectList(wrapper);

            if (textExercises == null || textExercises.isEmpty()) {
                return true; // 没有习题，认为已完成
            }

            List<Long> exerciseIds = textExercises.stream()
                    .map(TextExercise::getId)
                    .collect(Collectors.toList());

            // 2. 查询学生对这些习题的答题记录
            QueryWrapper<StudentExerciseAnswer> answerWrapper = new QueryWrapper<>();
            answerWrapper.eq("student_id", studentId)
                    .eq("assignment_id", assignmentId)
                    .in("exercise_id", exerciseIds)
                    .isNotNull("answer_content")
                    .ne("answer_content", "");

            List<StudentExerciseAnswer> validAnswers = studentExerciseAnswerMapper.selectList(answerWrapper);

            // 3. 提取已答题的习题ID
            Set<Long> answeredExerciseIds = (validAnswers != null ? validAnswers : Collections.<StudentExerciseAnswer>emptyList()).stream()
                    .map(StudentExerciseAnswer::getExerciseId)
                    .collect(Collectors.toSet());

            // 4. 检查是否所有习题都有有效答案
            return exerciseIds.stream().allMatch(answeredExerciseIds::contains);

        } catch (Exception e) {
            logger.error("检查课文习题完成情况失败", e);
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> getAssignmentsByCourseId(Long courseId, Long studentId) {
        // 查询课程下的所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);

        if (units == null || units.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> unitIds = units.stream().map(Unit::getId).collect(Collectors.toList());

        // 查询这些单元的任务（学生只能看到已发布且未删除的任务）
        QueryWrapper<Assignment> assignmentWrapper = new QueryWrapper<>();
        assignmentWrapper.in("unit_id", unitIds);
        assignmentWrapper.eq("status", "PUBLISHED");
        assignmentWrapper.eq("deleted", 0);
        assignmentWrapper.orderByDesc("created_time");
        List<Assignment> assignments = list(assignmentWrapper);

        if (assignments == null) {
            return new ArrayList<>();
        }

        // 获取学生加入的班级ID列表
        List<Long> classIds = studentClassesService.getClassIdsByStudentId(studentId);

        // 过滤出学生所在班级的任务，并添加详细信息
        List<Map<String, Object>> result = new ArrayList<>();
        for (Assignment assignment : assignments) {
            if (classIds != null && classIds.contains(assignment.getClassId())) {
                Map<String, Object> assignmentMap = new HashMap<>();
                assignmentMap.put("id", assignment.getId());
                assignmentMap.put("title", assignment.getTitle());
                assignmentMap.put("description", assignment.getDescription());
                assignmentMap.put("deadline", assignment.getDeadline());
                assignmentMap.put("createdTime", assignment.getCreatedTime());
                assignmentMap.put("classId", assignment.getClassId());
                assignmentMap.put("unitId", assignment.getUnitId());

                // 获取班级和单元名称
                Class clazz = classMapper.selectById(assignment.getClassId());
                assignmentMap.put("className", clazz != null ? clazz.getName() : "未知班级");

                Unit unit = unitMapper.selectById(assignment.getUnitId());
                assignmentMap.put("unitName", unit != null ? unit.getName() : "未知单元");

                // 计算进度
                double progress = calculateAssignmentProgress(assignment.getId(), studentId);
                assignmentMap.put("progress", Math.round(progress));
                assignmentMap.put("status", calculateAssignmentStatus(progress, assignment.getDeadline(),
                        assignment, studentId));

                result.add(assignmentMap);
            }
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getAssignmentsByUnitId(Long unitId, Long studentId) {
        // 查询该单元的任务（学生只能看到已发布且未删除的任务）
        QueryWrapper<Assignment> assignmentWrapper = new QueryWrapper<>();
        assignmentWrapper.eq("unit_id", unitId);
        assignmentWrapper.eq("status", "PUBLISHED");
        assignmentWrapper.eq("deleted", 0);
        assignmentWrapper.orderByDesc("created_time");
        List<Assignment> assignments = list(assignmentWrapper);

        if (assignments == null) {
            return new ArrayList<>();
        }

        // 获取学生加入的班级ID列表
        List<Long> classIds = studentClassesService.getClassIdsByStudentId(studentId);

        // 过滤出学生所在班级的任务，并添加详细信息
        List<Map<String, Object>> result = new ArrayList<>();
        for (Assignment assignment : assignments) {
            if (classIds != null && classIds.contains(assignment.getClassId())) {
                Map<String, Object> assignmentMap = new HashMap<>();
                assignmentMap.put("id", assignment.getId());
                assignmentMap.put("title", assignment.getTitle());
                assignmentMap.put("description", assignment.getDescription());
                assignmentMap.put("deadline", assignment.getDeadline());
                assignmentMap.put("createdTime", assignment.getCreatedTime());
                assignmentMap.put("classId", assignment.getClassId());
                assignmentMap.put("unitId", assignment.getUnitId());

                // 获取班级和单元名称
                Class clazz = classMapper.selectById(assignment.getClassId());
                assignmentMap.put("className", clazz != null ? clazz.getName() : "未知班级");

                Unit unit = unitMapper.selectById(assignment.getUnitId());
                assignmentMap.put("unitName", unit != null ? unit.getName() : "未知单元");

                // 计算进度
                double progress = calculateAssignmentProgress(assignment.getId(), studentId);
                assignmentMap.put("progress", Math.round(progress));
                assignmentMap.put("status", calculateAssignmentStatus(progress, assignment.getDeadline(),
                        assignment, studentId));

                result.add(assignmentMap);
            }
        }

        return result;
    }

    @Override
    public Map<String, Object> searchAllPublishedAssignments(String keyword, Long studentId, String status) {
        // 搜索所有已发布的任务（不限班级），按标题关键词模糊匹配
        QueryWrapper<Assignment> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "PUBLISHED");
        wrapper.eq("deleted", 0);

        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like("title", keyword.trim());
        }

        wrapper.orderByDesc("created_time");

        List<Assignment> assignments = list(wrapper);

        // 获取学生加入的班级ID列表（用于标记是否为已推荐任务）
        List<Long> classIds = studentClassesService.getClassIdsByStudentId(studentId);

        // 为每个任务添加详细信息
        List<Map<String, Object>> enrichedAssignments = new ArrayList<>();
        for (Assignment assignment : assignments) {
            Map<String, Object> assignmentMap = new HashMap<>();

            // 基本信息
            assignmentMap.put("id", assignment.getId());
            assignmentMap.put("title", assignment.getTitle());
            assignmentMap.put("description", assignment.getDescription());
            assignmentMap.put("deadline", assignment.getDeadline());
            assignmentMap.put("createdTime", assignment.getCreatedTime());
            assignmentMap.put("classId", assignment.getClassId());
            assignmentMap.put("unitId", assignment.getUnitId());

            // 获取班级名称
            Class clazz = classMapper.selectById(assignment.getClassId());
            assignmentMap.put("className", clazz != null ? clazz.getName() : "未知班级");

            // 获取单元名称
            Unit unit = unitMapper.selectById(assignment.getUnitId());
            assignmentMap.put("unitName", unit != null ? unit.getName() : "未知单元");

            // 判断是否为已推荐任务（学生所在班级的任务）
            boolean isAssigned = classIds != null && classIds.contains(assignment.getClassId());
            assignmentMap.put("isAssigned", isAssigned);

            // 计算学习进度（仅对已推荐的任务计算进度，其他任务进度为0）
            if (isAssigned) {
                double progress = calculateAssignmentProgress(assignment.getId(), studentId);
                assignmentMap.put("progress", Math.round(progress));

                // 计算任务状态
                String assignmentStatus = calculateAssignmentStatus(progress, assignment.getDeadline(),
                        assignment, studentId);
                if (status != null && !status.isEmpty() && !status.equals(assignmentStatus)) {
                    continue; // 如果指定了状态筛选，跳过不匹配的任务
                }
                assignmentMap.put("status", assignmentStatus);

                // 统计信息
                List<Word> assignmentWords = getAssignmentWords(assignment);
                List<com.english.entity.Text> assignmentTexts = getAssignmentTexts(assignment);

                assignmentMap.put("totalWords", assignmentWords.size());
                assignmentMap.put("learnedWords", calculateWordsLearnedInAssignment(assignment.getId(), studentId));
                assignmentMap.put("totalTexts", assignmentTexts.size());
                assignmentMap.put("readTexts", calculateTextsReadInAssignment(assignment.getId(), studentId));
                assignmentMap.put("studyTime", calculateStudyTimeInAssignment(assignment.getId(), studentId));
                assignmentMap.put("assignmentType", getAssignmentType(assignment));
            } else {
                // 未推荐的任务：进度为0，状态为 not-started
                assignmentMap.put("progress", 0);
                assignmentMap.put("status", "not-started");
                assignmentMap.put("totalWords", 0);
                assignmentMap.put("learnedWords", 0);
                assignmentMap.put("totalTexts", 0);
                assignmentMap.put("readTexts", 0);
                assignmentMap.put("studyTime", 0);
                assignmentMap.put("assignmentType", getAssignmentType(assignment));

                // 如果指定了状态筛选，未推荐任务只显示 not-started
                if (status != null && !status.isEmpty() && !status.equals("not-started")) {
                    continue;
                }
            }

            enrichedAssignments.add(assignmentMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("assignments", enrichedAssignments);
        result.put("total", enrichedAssignments.size());

        return result;
    }
}
