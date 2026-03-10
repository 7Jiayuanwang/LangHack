package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.*;
import com.english.entity.Class;
import com.english.mapper.*;
import com.english.entity.TextLearningRecord;
import com.english.service.StudentClassesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * 学生班级服务实现类
 */
@Service
public class StudentClassesServiceImpl implements StudentClassesService {

    private static final Logger logger = LoggerFactory.getLogger(StudentClassesServiceImpl.class);

    @Autowired
    private StudentClassMapper studentClassMapper;

    @Autowired
    private ClassMapper classMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UnitMapper unitMapper;

    @Autowired
    private UnitWordMapper unitWordMapper;

    @Autowired
    private VocabularyBookMapper vocabularyBookMapper;

    @Autowired
    private LearningRecordMapper learningRecordMapper;
    
    @Autowired
    private TextMapper textMapper;

    @Autowired
    private TextLearningRecordMapper textLearningRecordMapper;

    @Override
    public int countByStudentId(Long studentId) {
        QueryWrapper<StudentClass> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);
        return Math.toIntExact(studentClassMapper.selectCount(wrapper));
    }

    @Override
    public List<Map<String, Object>> getStudentClassesWithDetails(Long studentId) {
        List<Map<String, Object>> result = new ArrayList<>();

        // 查询学生加入的班级
        QueryWrapper<StudentClass> studentClassWrapper = new QueryWrapper<>();
        studentClassWrapper.eq("student_id", studentId);
        List<StudentClass> studentClasses = studentClassMapper.selectList(studentClassWrapper);
        if (studentClasses == null || studentClasses.isEmpty()) {
            return Collections.emptyList();
        }

        for (StudentClass studentClass : studentClasses) {
            // 查询班级信息
            Class classInfo = classMapper.selectById(studentClass.getClassId());
            if (classInfo == null) continue;

            // 查询课程信息 - 过滤已删除课程
            Course course = null;
            if (classInfo.getCourseId() != null) {
                QueryWrapper<Course> courseWrapper = new QueryWrapper<>();
                courseWrapper.eq("id", classInfo.getCourseId()).eq("deleted", 0);
                course = courseMapper.selectOne(courseWrapper);
            }

            // 查询教师信息 - 过滤已删除用户
            User teacher = null;
            if (classInfo.getTeacherId() != null) {
                QueryWrapper<User> teacherWrapper = new QueryWrapper<>();
                teacherWrapper.eq("id", classInfo.getTeacherId()).eq("deleted", 0);
                teacher = userMapper.selectOne(teacherWrapper);
            }

            Map<String, Object> classMap = new HashMap<>();
            classMap.put("id", studentClass.getId());
            classMap.put("classId", classInfo.getId());
            classMap.put("className", classInfo.getName());
            classMap.put("classDescription", classInfo.getDescription());
            classMap.put("courseId", course != null ? course.getId() : null);
            classMap.put("courseName", course != null ? course.getName() : "");
            classMap.put("courseDescription", course != null ? course.getDescription() : "");
            classMap.put("teacherId", teacher != null ? teacher.getId() : null);
            classMap.put("teacherName", teacher != null ? teacher.getRealName() : "未知教师");
            classMap.put("joinTime", studentClass.getJoinTime());

            // 计算学习进度统计
            Long courseId = classInfo.getCourseId();
            int learnedWords = calculateLearnedWords(studentId, classInfo.getId());
            int totalUnits = calculateTotalUnits(courseId);
            classMap.put("learnedWords", learnedWords);
            classMap.put("totalUnits", totalUnits);
            classMap.put("studyTime", calculateStudyTime(studentId, classInfo.getId()));
            
            // 计算综合进度（基于单词和单元的综合）
            double courseProgress = 0.0;
            if (totalUnits > 0) {
                int learnedUnits = calculateLearnedUnits(studentId, courseId);
                int totalWords = calculateTotalWords(courseId);
                int learnedWordsInCourse = calculateLearnedWordsInCourse(studentId, courseId);

                // 单元进度权重60%（百分比 0-100）
                double unitProgress = (double) learnedUnits / totalUnits * 100;
                // 单词进度权重40%（百分比 0-100）
                double wordProgress = totalWords > 0 ? (double) learnedWordsInCourse / totalWords * 100 : 0.0;
                // 综合进度 = 60% * unitProgress + 40% * wordProgress = 0-100
                courseProgress = unitProgress * 0.6 + wordProgress * 0.4;
            }
            // courseProgress 范围已是 0-100，Math.round() 保留到整数，再除100得到百分数形式
            // 结果范围 [0.0, 1.0]，表示 0%-100%
            classMap.put("progress", Math.round(courseProgress) / 100.0);

            result.add(classMap);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getStudentCoursesWithProgress(Long studentId) {
        Map<Long, Map<String, Object>> courseMap = new HashMap<>();

        // 查询学生加入的班级
        QueryWrapper<StudentClass> studentClassWrapper = new QueryWrapper<>();
        studentClassWrapper.eq("student_id", studentId);
        List<StudentClass> studentClasses = studentClassMapper.selectList(studentClassWrapper);
        if (studentClasses == null || studentClasses.isEmpty()) {
            return Collections.emptyList();
        }

        for (StudentClass studentClass : studentClasses) {
            // 查询班级信息
            Class classInfo = classMapper.selectById(studentClass.getClassId());
            if (classInfo == null) continue;

            // 查询课程信息 - 过滤已删除课程
            Course course = null;
            if (classInfo.getCourseId() != null) {
                QueryWrapper<Course> courseWrapper = new QueryWrapper<>();
                courseWrapper.eq("id", classInfo.getCourseId()).eq("deleted", 0);
                course = courseMapper.selectOne(courseWrapper);
            }
            if (course == null) continue;

            Long courseId = course.getId();
            if (!courseMap.containsKey(courseId)) {
                Map<String, Object> courseData = new HashMap<>();
                courseData.put("courseId", courseId);
                courseData.put("courseName", course.getName());
                courseData.put("courseDescription", course.getDescription());
                courseData.put("classes", new ArrayList<>());
                int totalUnits = calculateTotalUnits(courseId);
                int learnedUnits = calculateLearnedUnits(studentId, courseId);
                int totalWords = calculateTotalWords(courseId);
                int learnedWords = calculateLearnedWordsInCourse(studentId, courseId);

                courseData.put("totalUnits", totalUnits);
                courseData.put("learnedUnits", learnedUnits);
                courseData.put("totalWords", totalWords);
                courseData.put("learnedWords", learnedWords);
                courseData.put("studyTime", calculateStudyTimeInCourse(studentId, courseId));

                // 计算综合进度（基于单词和单元的综合）
                double courseProgress = 0.0;
                if (totalUnits > 0) {
                    // 单元进度权重60%
                    double unitProgress = (double) learnedUnits / totalUnits * 100;
                    // 单词进度权重40%
                    double wordProgress = totalWords > 0 ? (double) learnedWords / totalWords * 100 : 0.0;
                    courseProgress = unitProgress * 0.6 + wordProgress * 0.4;
                }
                // 统一与 getStudentClassesWithDetails() 的格式，返回 [0.0, 1.0]
                courseData.put("progress", Math.round(courseProgress) / 100.0);

                courseMap.put(courseId, courseData);
            }

            // 添加班级信息到课程中
            Map<String, Object> courseData = courseMap.get(courseId);
            Object classesObj = courseData.get("classes");
            if (!(classesObj instanceof List)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> classes = (List<Map<String, Object>>) classesObj;

            Map<String, Object> classData = new HashMap<>();
            classData.put("classId", classInfo.getId());
            classData.put("className", classInfo.getName());
            classData.put("joinTime", studentClass.getJoinTime());

            classes.add(classData);
        }

        return new ArrayList<>(courseMap.values());
    }

    @Override
    public List<Long> getClassIdsByStudentId(Long studentId) {
        if (studentId == null) {
            return Collections.emptyList();
        }

        QueryWrapper<StudentClass> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);
        wrapper.select("class_id");

        List<StudentClass> studentClasses = studentClassMapper.selectList(wrapper);
        if (studentClasses == null || studentClasses.isEmpty()) {
            return Collections.emptyList();
        }

        return studentClasses.stream()
                .map(StudentClass::getClassId)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isStudentInClass(Long studentId, Long classId) {
        QueryWrapper<StudentClass> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
              .eq("class_id", classId);
        return studentClassMapper.selectCount(wrapper) > 0;
    }

    @Override
    public Map<String, Object> getStudentClassInfo(Long studentId, Long classId) {
        QueryWrapper<StudentClass> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
              .eq("class_id", classId);

        StudentClass studentClass = studentClassMapper.selectOne(wrapper);
        if (studentClass == null) {
            return null;
        }

        Map<String, Object> info = new HashMap<>();
        info.put("id", studentClass.getId());
        info.put("studentId", studentClass.getStudentId());
        info.put("classId", studentClass.getClassId());
        info.put("joinTime", studentClass.getJoinTime());

        return info;
    }

    /**
     * 计算学生在班级中学习的单词数量
     */
    private int calculateLearnedWords(Long studentId, Long classId) {
        // 获取班级的课程ID
        QueryWrapper<Class> classWrapper = new QueryWrapper<>();
        classWrapper.eq("id", classId);
        Class classInfo = classMapper.selectOne(classWrapper);
        if (classInfo == null) {
            return 0;
        }

        // 确保 courseId 不为 null，避免查出 course_id IS NULL 的记录
        Long courseId = classInfo.getCourseId();
        if (courseId == null) {
            return 0;
        }

        // 获取课程下所有单元的单词
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);
        if (units == null) units = Collections.emptyList();

        if (units.isEmpty()) {
            return 0;
        }

        List<Long> unitIds = units.stream().map(Unit::getId).collect(Collectors.toList());

        // 查询单元单词关联
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.in("unit_id", unitIds);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

        List<Long> wordIds = unitWords.stream().map(UnitWord::getWordId).collect(Collectors.toList());
        if (wordIds.isEmpty()) {
            return 0;
        }

        // 查询学生已学习的单词（从生词本和学习记录）
        QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
        vocabWrapper.eq("student_id", studentId)
                   .in("word_id", wordIds)
                   .ne("mastery_level", "NEW");
        return Math.toIntExact(vocabularyBookMapper.selectCount(vocabWrapper));
    }

    /**
     * 计算课程的总单元数量
     */
    private int calculateTotalUnits(Long courseId) {
        if (courseId == null) return 0;
        QueryWrapper<Unit> wrapper = new QueryWrapper<>();
        wrapper.eq("course_id", courseId);
        return Math.toIntExact(unitMapper.selectCount(wrapper));
    }

    /**
     * 计算学生在班级中的学习时间（分钟）
     * 包含单词学习记录和课文学习记录
     */
    private int calculateStudyTime(Long studentId, Long classId) {
        // 获取班级的课程ID
        QueryWrapper<Class> classWrapper = new QueryWrapper<>();
        classWrapper.eq("id", classId);
        Class classInfo = classMapper.selectOne(classWrapper);
        if (classInfo == null) {
            return 0;
        }

        // 确保 courseId 不为 null，避免查出 course_id IS NULL 的记录
        Long studyCourseId = classInfo.getCourseId();
        if (studyCourseId == null) {
            return 0;
        }

        // 获取课程下所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", studyCourseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);
        if (units == null) units = Collections.emptyList();

        if (units.isEmpty()) {
            return 0;
        }

        List<Long> unitIds = units.stream().map(Unit::getId).collect(Collectors.toList());

        long totalSeconds = 0L;

        // 1. 统计单词学习记录
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.in("unit_id", unitIds);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);
        if (unitWords == null) unitWords = Collections.emptyList();

        List<Long> wordIds = unitWords.stream().map(UnitWord::getWordId).collect(Collectors.toList());

        if (!wordIds.isEmpty()) {
            QueryWrapper<LearningRecord> wordRecordWrapper = new QueryWrapper<>();
            wordRecordWrapper.eq("student_id", studentId)
                       .in("word_id", wordIds);
            List<LearningRecord> wordRecords = learningRecordMapper.selectList(wordRecordWrapper);
            if (wordRecords != null) {
                totalSeconds += wordRecords.stream()
                    .mapToLong(r -> r.getDurationSeconds() != null ? r.getDurationSeconds() : 0)
                    .sum();
            }
        }

        // 2. 统计课文学习记录（TEXT_VIEW 类型，通过 text_id 关联）
        QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
        textWrapper.in("unit_id", unitIds)
                   .eq("deleted", 0);
        List<com.english.entity.Text> texts = textMapper.selectList(textWrapper);
        if (texts == null) texts = Collections.emptyList();

        List<Long> textIds = texts.stream().map(com.english.entity.Text::getId).collect(Collectors.toList());

        if (!textIds.isEmpty()) {
            QueryWrapper<LearningRecord> textRecordWrapper = new QueryWrapper<>();
            textRecordWrapper.eq("student_id", studentId)
                       .in("text_id", textIds)
                       .eq("record_type", "TEXT_VIEW");
            List<LearningRecord> textRecords = learningRecordMapper.selectList(textRecordWrapper);
            if (textRecords != null) {
                totalSeconds += textRecords.stream()
                    .mapToLong(r -> r.getDurationSeconds() != null ? r.getDurationSeconds() : 0)
                    .sum();
            }
        }

        // 先求和再除60，避免逐条整除导致截断误差
        return (int) (totalSeconds / 60);
    }

    /**
     * 计算学生已学习的单元数量（基于综合进度）
     */
    private int calculateLearnedUnits(Long studentId, Long courseId) {
        if (courseId == null) return 0;
        // 获取课程下所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);
        if (units == null) units = Collections.emptyList();

        if (units.isEmpty()) {
            return 0;
        }

        int learnedUnits = 0;
        for (Unit unit : units) {
            // 获取单元下的所有单词
            QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
            unitWordWrapper.eq("unit_id", unit.getId());
            List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);
            if (unitWords == null) unitWords = Collections.emptyList();

            // 获取单元下的所有课文
            QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
            textWrapper.eq("unit_id", unit.getId())
                       .eq("deleted", 0);
            List<com.english.entity.Text> texts = textMapper.selectList(textWrapper);
            if (texts == null) texts = Collections.emptyList();

            // 如果单元既没有单词也没有课文，跳过
            if (unitWords.isEmpty() && texts.isEmpty()) {
                continue;
            }

            // 计算单词进度
            double wordProgress = 0.0;
            if (!unitWords.isEmpty()) {
                List<Long> wordIds = unitWords.stream().map(UnitWord::getWordId).collect(Collectors.toList());
                QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
                vocabWrapper.eq("student_id", studentId)
                           .in("word_id", wordIds)
                           .ne("mastery_level", "NEW");
                int learnedWordsInUnit = Math.toIntExact(vocabularyBookMapper.selectCount(vocabWrapper));
                wordProgress = (double) learnedWordsInUnit / wordIds.size() * 100;
            }

            // 计算课文进度
            double textProgress = 0.0;
            if (!texts.isEmpty()) {
                int readTexts = 0;
                for (com.english.entity.Text text : texts) {
                    // 通过 text_learning_records 表按实际 textId 查询该学生的阅读记录
                    QueryWrapper<TextLearningRecord> textRecordWrapper = new QueryWrapper<>();
                    textRecordWrapper.eq("student_id", studentId)
                                   .eq("text_id", text.getId());
                    List<TextLearningRecord> textRecords = textLearningRecordMapper.selectList(textRecordWrapper);
                    if (textRecords == null) textRecords = Collections.emptyList();

                    int totalReadTime = textRecords.stream()
                        .mapToInt(r -> r.getDurationSeconds() != null ? r.getDurationSeconds() : 0)
                        .sum();
                    if (totalReadTime >= 600) { // 10分钟 = 600秒
                        readTexts++;
                    }
                }
                textProgress = (double) readTexts / texts.size() * 100;
            }

            // 综合进度：单词和课文各占50%，如果只有一种则直接使用
            double unitProgress = 0.0;
            if (!unitWords.isEmpty() && !texts.isEmpty()) {
                unitProgress = (wordProgress * 0.5 + textProgress * 0.5);
            } else if (!unitWords.isEmpty()) {
                unitProgress = wordProgress;
            } else {
                unitProgress = textProgress;
            }

            // 如果单元进度超过50%，认为已学习该单元
            if (unitProgress >= 50.0) {
                learnedUnits++;
            }
        }

        return learnedUnits;
    }

    /**
     * 计算课程的总单词数量
     */
    private int calculateTotalWords(Long courseId) {
        if (courseId == null) return 0;
        // 获取课程下所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);
        if (units == null) units = Collections.emptyList();

        if (units.isEmpty()) {
            return 0;
        }

        List<Long> unitIds = units.stream().map(Unit::getId).collect(Collectors.toList());

        // 查询所有单元的单词
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.in("unit_id", unitIds);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);
        if (unitWords == null) unitWords = Collections.emptyList();

        // 使用Set去重
        return unitWords.stream()
                .map(UnitWord::getWordId)
                .collect(Collectors.toSet())
                .size();
    }

    /**
     * 计算学生在课程中学习的单词数量
     */
    private int calculateLearnedWordsInCourse(Long studentId, Long courseId) {
        if (courseId == null) return 0;
        // 获取课程下所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);
        if (units == null) units = Collections.emptyList();

        if (units.isEmpty()) {
            return 0;
        }

        List<Long> unitIds = units.stream().map(Unit::getId).collect(Collectors.toList());

        // 查询所有单元的单词
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.in("unit_id", unitIds);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);
        if (unitWords == null) unitWords = Collections.emptyList();

        List<Long> wordIds = unitWords.stream()
                .map(UnitWord::getWordId)
                .collect(Collectors.toList());

        if (wordIds.isEmpty()) {
            return 0;
        }

        // 查询学生已学习的单词
        QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
        vocabWrapper.eq("student_id", studentId)
                   .in("word_id", wordIds)
                   .ne("mastery_level", "NEW");

        return Math.toIntExact(vocabularyBookMapper.selectCount(vocabWrapper));
    }

    /**
     * 计算学生在课程中的学习时间（分钟）
     * 包含单词学习记录和课文学习记录
     */
    private int calculateStudyTimeInCourse(Long studentId, Long courseId) {
        if (courseId == null) return 0;
        // 获取课程下所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);
        if (units == null) units = Collections.emptyList();

        if (units.isEmpty()) {
            return 0;
        }

        List<Long> unitIds = units.stream().map(Unit::getId).collect(Collectors.toList());

        long totalSeconds = 0L;

        // 1. 统计单词学习记录
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.in("unit_id", unitIds);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);
        if (unitWords == null) unitWords = Collections.emptyList();

        List<Long> wordIds = unitWords.stream()
                .map(UnitWord::getWordId)
                .collect(Collectors.toList());

        if (!wordIds.isEmpty()) {
            QueryWrapper<LearningRecord> wordRecordWrapper = new QueryWrapper<>();
            wordRecordWrapper.eq("student_id", studentId)
                       .in("word_id", wordIds);
            List<LearningRecord> wordRecords = learningRecordMapper.selectList(wordRecordWrapper);
            if (wordRecords != null) {
                totalSeconds += wordRecords.stream()
                    .mapToLong(r -> r.getDurationSeconds() != null ? r.getDurationSeconds() : 0)
                    .sum();
            }
        }

        // 2. 统计课文学习记录（TEXT_VIEW 类型，通过 text_id 关联）
        QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
        textWrapper.in("unit_id", unitIds)
                   .eq("deleted", 0);
        List<com.english.entity.Text> texts = textMapper.selectList(textWrapper);
        if (texts == null) texts = Collections.emptyList();

        List<Long> textIds = texts.stream().map(com.english.entity.Text::getId).collect(Collectors.toList());

        if (!textIds.isEmpty()) {
            QueryWrapper<LearningRecord> textRecordWrapper = new QueryWrapper<>();
            textRecordWrapper.eq("student_id", studentId)
                       .in("text_id", textIds)
                       .eq("record_type", "TEXT_VIEW");
            List<LearningRecord> textRecords = learningRecordMapper.selectList(textRecordWrapper);
            if (textRecords != null) {
                totalSeconds += textRecords.stream()
                    .mapToLong(r -> r.getDurationSeconds() != null ? r.getDurationSeconds() : 0)
                    .sum();
            }
        }

        // 先求和再除60，避免逐条整除导致截断误差
        return (int) (totalSeconds / 60);
    }
}