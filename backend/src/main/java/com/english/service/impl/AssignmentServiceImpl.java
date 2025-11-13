package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.*;
import com.english.entity.Class;
import com.english.mapper.*;
import com.english.service.AssignmentService;
import com.english.service.CourseService;
import com.english.service.StudentClassService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AssignmentServiceImpl extends ServiceImpl<AssignmentMapper, Assignment> implements AssignmentService {

    @Autowired
    private AssignmentMapper assignmentMapper;

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

    @Override
    public Map<String, Object> getAssignmentsByPage(Map<String, Object> params) {
        int page = (int) params.get("page");
        int size = (int) params.get("size");
        Long teacherId = params.get("teacherId") != null ? Long.valueOf(params.get("teacherId").toString()) : null;
        Long classId = params.get("classId") != null ? Long.valueOf(params.get("classId").toString()) : null;
        String status = (String) params.get("status");
        String keyword = (String) params.get("keyword");

        Page<Assignment> pageParam = new Page<>(page, size);
        IPage<Assignment> pageResult = assignmentMapper.selectAssignmentsWithDetails(
                pageParam, teacherId, classId, status, keyword);

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
            throw new RuntimeException("单元ID不能为空");
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
        // 物理删除
        removeById(id);
    }

    
    @Override
    public Map<String, Object> getTeacherStats(Long teacherId) {
        Map<String, Object> stats = assignmentMapper.selectTeacherStats(teacherId);

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
            Long classId = (Long) classMap.get("id");
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
        if (classId == null) return 0;
        try {
            List<StudentClass> studentClasses = studentClassService.getStudentsByClassId(classId);
            return studentClasses.size();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public List<Assignment> getRecentAssignments(Long teacherId, Integer limit) {
        return assignmentMapper.selectRecentAssignments(teacherId, limit);
    }

    @Override
    public boolean validateTeacherPermission(Long teacherId, Long assignmentId) {
        Assignment assignment = getById(assignmentId);
        return assignment != null && teacherId.equals(assignment.getCreatedBy());
    }

    // ========== 学生端方法实现 ==========

    @Override
    public Map<String, Object> getStudentAssignmentsWithStatus(List<Long> classIds, Long studentId, String status) {
        QueryWrapper<Assignment> wrapper = new QueryWrapper<>();
        wrapper.in("class_id", classIds)
              .orderByDesc("created_time");

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
            String assignmentStatus = calculateAssignmentStatus(progress, assignment.getDeadline());
            if (status != null && !status.isEmpty() && !status.equals(assignmentStatus)) {
                continue; // 如果指定了状态筛选，跳过不匹配的任务
            }
            assignmentMap.put("status", assignmentStatus);

            // 统计信息
            assignmentMap.put("totalWords", calculateTotalWordsInUnit(assignment.getUnitId()));
            assignmentMap.put("learnedWords", calculateWordsLearnedInAssignment(assignment.getId(), studentId));
            assignmentMap.put("totalTexts", calculateTotalTextsInUnit(assignment.getUnitId()));
            assignmentMap.put("readTexts", calculateTextsReadInAssignment(assignment.getId(), studentId));
            assignmentMap.put("studyTime", calculateStudyTimeInAssignment(assignment.getId(), studentId));

            enrichedAssignments.add(assignmentMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("assignments", enrichedAssignments);
        result.put("total", enrichedAssignments.size());

        return result;
    }

    @Override
    public Map<String, Object> getAssignmentDetailWithProgress(Long assignmentId, Long studentId) {
        Assignment assignment = getById(assignmentId);
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
        detail.put("progress", calculateAssignmentProgress(assignmentId, studentId));
        detail.put("completed", isAssignmentCompleted(assignmentId, studentId));

        return detail;
    }

    @Override
    public Map<String, Object> startAssignment(Long assignmentId, Long studentId) {
        Assignment assignment = getById(assignmentId);
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
        Assignment assignment = getById(assignmentId);
        if (assignment == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> content = new HashMap<>();
        content.put("unitId", assignment.getUnitId());

        // 查询单元的课文列表
        QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
        textWrapper.eq("unit_id", assignment.getUnitId());
        List<com.english.entity.Text> texts = textMapper.selectList(textWrapper);

        // 获取课文信息并添加学生学习状态
        List<Map<String, Object>> textList = new ArrayList<>();
        for (com.english.entity.Text text : texts) {
            Map<String, Object> textMap = new HashMap<>();
            textMap.put("id", text.getId());
            textMap.put("title", text.getTitle());
            textMap.put("content", text.getContent());
            textMap.put("difficulty", text.getDifficulty());
            textMap.put("audioUrl", text.getAudioUrl());

            // 检查学生的学习状态
            boolean isRead = checkTextReadStatus(studentId, assignmentId, text.getId());
            int readTime = getTextReadTime(studentId, assignmentId, text.getId());

            textMap.put("completed", isRead);
            textMap.put("studyTime", readTime);

            textList.add(textMap);
        }
        content.put("texts", textList);

        // 查询单元的单词列表
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.eq("unit_id", assignment.getUnitId());
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

        // 获取单词详细信息并添加学生学习状态
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

                boolean completed = vocabBook != null && !"NEW".equals(vocabBook.getMasteryLevel());
                int reviewCount = vocabBook != null ? vocabBook.getReviewCount() : 0;

                wordMap.put("completed", completed);
                wordMap.put("reviewCount", reviewCount);
                wordMap.put("masteryLevel", vocabBook != null ? vocabBook.getMasteryLevel() : "NEW");

                wordList.add(wordMap);
            }
        }
        content.put("words", wordList);

        return content;
    }

    @Override
    public void completeAssignment(Long assignmentId, Long studentId, Map<String, Object> completionData) {
        // 记录任务完成的学习记录
        LearningRecord record = new LearningRecord();
        record.setStudentId(studentId);
        record.setAssignmentId(assignmentId);
        record.setWordId(0L); // 任务完成记录，不关联具体单词
        record.setRecordType("COMPLETE");
        record.setDurationSeconds(completionData.get("studyTime") != null ?
            ((Number) completionData.get("studyTime")).intValue() * 60 : 0); // 分钟转秒
        record.setCorrectCount(completionData.get("completedItems") != null ?
            ((Number) completionData.get("completedItems")).intValue() : 0);
        record.setTotalCount(completionData.get("totalItems") != null ?
            ((Number) completionData.get("totalItems")).intValue() : 1);
        record.setCreatedTime(LocalDateTime.now());

        learningRecordMapper.insert(record);
    }

    @Override
    public Map<String, Object> getAssignmentProgress(Long assignmentId, Long studentId) {
        Map<String, Object> progress = new HashMap<>();
        progress.put("assignmentId", assignmentId);
        progress.put("studentId", studentId);

        // 实现具体的进度计算逻辑
        progress.put("completionRate", calculateAssignmentProgress(assignmentId, studentId));
        progress.put("wordsLearned", calculateWordsLearnedInAssignment(assignmentId, studentId));
        progress.put("textsRead", calculateTextsReadInAssignment(assignmentId, studentId));
        progress.put("studyTime", calculateStudyTimeInAssignment(assignmentId, studentId));

        return progress;
    }

    @Override
    public boolean isAssignmentInClasses(Long assignmentId, List<Long> classIds) {
        Assignment assignment = getById(assignmentId);
        return assignment != null && classIds.contains(assignment.getClassId());
    }

    /**
     * 计算任务进度
     */
    private double calculateAssignmentProgress(Long assignmentId, Long studentId) {
        Assignment assignment = getById(assignmentId);
        if (assignment == null) {
            return 0.0;
        }

        // 获取任务关联单元的单词
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.eq("unit_id", assignment.getUnitId());
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

        if (unitWords.isEmpty()) {
            return 0.0;
        }

        List<Long> wordIds = unitWords.stream().map(UnitWord::getWordId).collect(Collectors.toList());

        // 查询学生已学习的单词
        QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
        vocabWrapper.eq("student_id", studentId)
                   .in("word_id", wordIds)
                   .ne("mastery_level", "NEW");

        int learnedWords = Math.toIntExact(vocabularyBookMapper.selectCount(vocabWrapper));
        int totalWords = wordIds.size();

        return totalWords > 0 ? (double) learnedWords / totalWords * 100 : 0.0;
    }

    /**
     * 检查任务是否完成
     */
    private boolean isAssignmentCompleted(Long assignmentId, Long studentId) {
        return calculateAssignmentProgress(assignmentId, studentId) >= 80.0; // 80%以上算完成
    }

    /**
     * 计算在任务中学习的单词数量
     */
    private int calculateWordsLearnedInAssignment(Long assignmentId, Long studentId) {
        Assignment assignment = getById(assignmentId);
        if (assignment == null) {
            return 0;
        }

        // 获取任务关联单元的单词
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.eq("unit_id", assignment.getUnitId());
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
     * 计算在任务中阅读的课文数量
     */
    private int calculateTextsReadInAssignment(Long assignmentId, Long studentId) {
        Assignment assignment = getById(assignmentId);
        if (assignment == null) {
            return 0;
        }

        // 获取任务关联单元的课文
        QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
        textWrapper.eq("unit_id", assignment.getUnitId());
        List<com.english.entity.Text> texts = textMapper.selectList(textWrapper);

        if (texts.isEmpty()) {
            return 0;
        }

        List<Long> textIds = texts.stream().map(com.english.entity.Text::getId).collect(Collectors.toList());

        // 查询学生的课文学习记录
        QueryWrapper<LearningRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("student_id", studentId)
                   .in("assignment_id", assignmentId)
                   .eq("record_type", "VIEW");

        return Math.toIntExact(learningRecordMapper.selectCount(recordWrapper));
    }

    /**
     * 计算在任务中的学习时间（分钟）
     */
    private int calculateStudyTimeInAssignment(Long assignmentId, Long studentId) {
        // 获取任务关联单元的单词
        Assignment assignment = getById(assignmentId);
        if (assignment == null) {
            return 0;
        }

        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.eq("unit_id", assignment.getUnitId());
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

        if (unitWords.isEmpty()) {
            return 0;
        }

        List<Long> wordIds = unitWords.stream().map(UnitWord::getWordId).collect(Collectors.toList());

        // 查询学习记录
        QueryWrapper<LearningRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("student_id", studentId)
                   .in("word_id", wordIds)
                   .or()
                   .eq("assignment_id", assignmentId);
        List<LearningRecord> records = learningRecordMapper.selectList(recordWrapper);

        // 计算总学习时间（转换为分钟）
        return records.stream().mapToInt(record ->
            record.getDurationSeconds() != null ? record.getDurationSeconds() / 60 : 0
        ).sum();
    }

    /**
     * 计算任务状态
     */
    private String calculateAssignmentStatus(double progress, LocalDateTime deadline) {
        LocalDateTime now = LocalDateTime.now();

        if (deadline != null && now.isAfter(deadline)) {
            return "expired";
        }

        if (progress >= 100.0) {
            return "completed";
        } else if (progress > 0.0) {
            return "in-progress";
        } else {
            return "not-started";
        }
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
        return Math.toIntExact(textMapper.selectCount(wrapper));
    }

    /**
     * 检查课文是否已阅读（基于学习记录）
     */
    private boolean checkTextReadStatus(Long studentId, Long assignmentId, Long textId) {
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
              .eq("assignment_id", assignmentId)
              .eq("record_type", "VIEW")
              .isNotNull("word_id"); // 表示这是一个课文学习记录

        return learningRecordMapper.selectCount(wrapper) > 0;
    }

    /**
     * 获取课文阅读时间（分钟）
     */
    private int getTextReadTime(Long studentId, Long assignmentId, Long textId) {
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
              .eq("assignment_id", assignmentId)
              .eq("record_type", "VIEW")
              .isNotNull("word_id");

        List<LearningRecord> records = learningRecordMapper.selectList(wrapper);
        return records.stream()
                .mapToInt(record -> record.getDurationSeconds() != null ? record.getDurationSeconds() / 60 : 0)
                .sum();
    }
}