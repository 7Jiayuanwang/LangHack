package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.*;
import com.english.entity.Class;
import com.english.mapper.*;
import com.english.service.StudentClassesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生班级服务实现类
 */
@Service
public class StudentClassesServiceImpl implements StudentClassesService {

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

        for (StudentClass studentClass : studentClasses) {
            // 查询班级信息
            Class classInfo = classMapper.selectById(studentClass.getClassId());
            if (classInfo == null) continue;

            // 查询课程信息
            Course course = courseMapper.selectById(classInfo.getCourseId());

            // 查询教师信息
            User teacher = userMapper.selectById(classInfo.getTeacherId());

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
            classMap.put("learnedWords", calculateLearnedWords(studentId, classInfo.getId()));
            classMap.put("totalUnits", calculateTotalUnits(classInfo.getCourseId()));
            classMap.put("studyTime", calculateStudyTime(studentId, classInfo.getId()));

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

        for (StudentClass studentClass : studentClasses) {
            // 查询班级信息
            Class classInfo = classMapper.selectById(studentClass.getClassId());
            if (classInfo == null) continue;

            // 查询课程信息
            Course course = courseMapper.selectById(classInfo.getCourseId());
            if (course == null) continue;

            Long courseId = course.getId();
            if (!courseMap.containsKey(courseId)) {
                Map<String, Object> courseData = new HashMap<>();
                courseData.put("courseId", courseId);
                courseData.put("courseName", course.getName());
                courseData.put("courseDescription", course.getDescription());
                courseData.put("classes", new ArrayList<>());
                courseData.put("totalUnits", calculateTotalUnits(courseId));
                courseData.put("learnedUnits", calculateLearnedUnits(studentId, courseId));
                courseData.put("totalWords", calculateTotalWords(courseId));
                courseData.put("learnedWords", calculateLearnedWordsInCourse(studentId, courseId));
                courseData.put("studyTime", calculateStudyTimeInCourse(studentId, courseId));

                courseMap.put(courseId, courseData);
            }

            // 添加班级信息到课程中
            Map<String, Object> courseData = courseMap.get(courseId);
            List<Map<String, Object>> classes = (List<Map<String, Object>>) courseData.get("classes");

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
        QueryWrapper<StudentClass> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);
        wrapper.select("class_id");

        return studentClassMapper.selectList(wrapper).stream()
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

        // 获取课程下所有单元的单词
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", classInfo.getCourseId());
        List<Unit> units = unitMapper.selectList(unitWrapper);

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
        QueryWrapper<Unit> wrapper = new QueryWrapper<>();
        wrapper.eq("course_id", courseId);
        return Math.toIntExact(unitMapper.selectCount(wrapper));
    }

    /**
     * 计算学生在班级中的学习时间（分钟）
     */
    private int calculateStudyTime(Long studentId, Long classId) {
        // 获取班级的课程ID
        QueryWrapper<Class> classWrapper = new QueryWrapper<>();
        classWrapper.eq("id", classId);
        Class classInfo = classMapper.selectOne(classWrapper);
        if (classInfo == null) {
            return 0;
        }

        // 获取课程下所有单元的单词
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", classInfo.getCourseId());
        List<Unit> units = unitMapper.selectList(unitWrapper);

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
     * 计算学生已学习的单元数量
     */
    private int calculateLearnedUnits(Long studentId, Long courseId) {
        // 获取课程下所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);

        if (units.isEmpty()) {
            return 0;
        }

        int learnedUnits = 0;
        for (Unit unit : units) {
            // 获取单元下的所有单词
            QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
            unitWordWrapper.eq("unit_id", unit.getId());
            List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

            if (unitWords.isEmpty()) {
                continue;
            }

            List<Long> wordIds = unitWords.stream().map(UnitWord::getWordId).collect(Collectors.toList());

            // 检查学生对这个单元的掌握程度
            QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
            vocabWrapper.eq("student_id", studentId)
                       .in("word_id", wordIds)
                       .ne("mastery_level", "NEW");

            int learnedWordsInUnit = Math.toIntExact(vocabularyBookMapper.selectCount(vocabWrapper));
            int totalWordsInUnit = wordIds.size();

            // 如果学习了单元中超过50%的单词，认为已学习该单元
            if (totalWordsInUnit > 0 && learnedWordsInUnit >= totalWordsInUnit * 0.5) {
                learnedUnits++;
            }
        }

        return learnedUnits;
    }

    /**
     * 计算课程的总单词数量
     */
    private int calculateTotalWords(Long courseId) {
        // 获取课程下所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);

        if (units.isEmpty()) {
            return 0;
        }

        List<Long> unitIds = units.stream().map(Unit::getId).collect(Collectors.toList());

        // 查询所有单元的单词
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.in("unit_id", unitIds);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

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
        // 获取课程下所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);

        if (units.isEmpty()) {
            return 0;
        }

        List<Long> unitIds = units.stream().map(Unit::getId).collect(Collectors.toList());

        // 查询所有单元的单词
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.in("unit_id", unitIds);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

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
     */
    private int calculateStudyTimeInCourse(Long studentId, Long courseId) {
        // 获取课程下所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", courseId);
        List<Unit> units = unitMapper.selectList(unitWrapper);

        if (units.isEmpty()) {
            return 0;
        }

        List<Long> unitIds = units.stream().map(Unit::getId).collect(Collectors.toList());

        // 查询所有单元的单词
        QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
        unitWordWrapper.in("unit_id", unitIds);
        List<UnitWord> unitWords = unitWordMapper.selectList(unitWordWrapper);

        List<Long> wordIds = unitWords.stream()
                .map(UnitWord::getWordId)
                .collect(Collectors.toList());

        if (wordIds.isEmpty()) {
            return 0;
        }

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
}