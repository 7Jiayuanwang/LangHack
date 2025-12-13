package com.english.service.impl;



import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.StudentClass;
import com.english.entity.User;
import com.english.entity.Course;
import com.english.entity.Unit;
import com.english.entity.UnitWord;
import com.english.entity.VocabularyBook;
import com.english.entity.LearningRecord;
import com.english.mapper.StudentClassMapper;
import com.english.mapper.ClassMapper;
import com.english.mapper.CourseMapper;
import com.english.mapper.UserMapper;
import com.english.mapper.UnitMapper;
import com.english.mapper.UnitWordMapper;
import com.english.mapper.VocabularyBookMapper;
import com.english.mapper.LearningRecordMapper;
import com.english.service.ClassService;
import com.english.service.StudentClassService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class StudentClassServiceImpl extends ServiceImpl<StudentClassMapper, StudentClass> implements StudentClassService {

    private static final Logger logger = LoggerFactory.getLogger(StudentClassServiceImpl.class);

    @Autowired
    private ClassService classService;

    @Autowired
    private UserService userService;

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
    public Map<String, Object> getStudentClassesByPage(Map<String, Object> params) {
        int page = (int) params.get("page");
        int size = (int) params.get("size");
        String keyword = (String) params.get("keyword");
        Long classId = params.get("classId") != null ? Long.valueOf(params.get("classId").toString()) : null;
        Long studentId = params.get("studentId") != null ? Long.valueOf(params.get("studentId").toString()) : null;

        Page<StudentClass> pageParam = new Page<>(page, size);
        IPage<StudentClass> pageResult = baseMapper.selectStudentClassesWithDetails(pageParam, classId, studentId, keyword);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    public StudentClass getStudentClassById(Long id) {
        return getById(id);
    }

    @Override
    @Transactional
    public void joinClass(Long studentId, Long classId) {
        // 验证学生是否存在
        User student = userService.getUserById(studentId);
        if (student == null || !"STUDENT".equals(student.getRole())) {
            throw new RuntimeException("学生不存在或角色不正确");
        }

        // 验证班级是否存在
        com.english.entity.Class classInfo = classService.getClassById(classId);
        if (classInfo == null) {
            throw new RuntimeException("班级不存在");
        }

        // 检查学生是否已在班级中
        if (isStudentInClass(studentId, classId)) {
            throw new RuntimeException("学生已在此班级中");
        }

        StudentClass studentClass = new StudentClass();
        studentClass.setStudentId(studentId);
        studentClass.setClassId(classId);
        studentClass.setJoinTime(LocalDateTime.now());

        save(studentClass);
    }

    @Override
    @Transactional
    public void leaveClass(Long studentId, Long classId) {
        // 验证学生是否在班级中
        if (!isStudentInClass(studentId, classId)) {
            throw new RuntimeException("学生不在此班级中");
        }

        QueryWrapper<StudentClass> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_id", studentId);
        queryWrapper.eq("class_id", classId);
        remove(queryWrapper);
    }

    @Override
    public List<StudentClass> getStudentsByClassId(Long classId) {
        return baseMapper.selectStudentsByClassId(classId);
    }

    @Override
    public List<StudentClass> getClassesByStudentId(Long studentId) {
        return baseMapper.selectClassesByStudentId(studentId);
    }

    @Override
    public boolean isStudentInClass(Long studentId, Long classId) {
        Integer count = baseMapper.checkStudentInClass(studentId, classId);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void batchAddStudentsToClass(Long classId, List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return;
        }

        // 验证班级是否存在
        com.english.entity.Class classInfo = classService.getClassById(classId);
        if (classInfo == null) {
            throw new RuntimeException("班级不存在");
        }

        for (Long studentId : studentIds) {
            try {
                joinClass(studentId, classId);
            } catch (Exception e) {
                // 记录失败的学生，但不影响其他学生加入
                logger.warn("学生 {} 加入班级失败: {}", studentId, e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void batchRemoveStudentsFromClass(Long classId, List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return;
        }

        for (Long studentId : studentIds) {
            try {
                leaveClass(studentId, classId);
            } catch (Exception e) {
                // 记录失败的学生，但不影响其他学生移除
                logger.warn("学生 {} 移除班级失败: {}", studentId, e.getMessage());
            }
        }
    }

    @Override
    public Long getStudentCountByClassId(Long classId) {
        return baseMapper.selectStudentCountByClassId(classId);
    }

    @Override
    public Long getClassCountByStudentId(Long studentId) {
        return baseMapper.selectClassCountByStudentId(studentId);
    }

    @Override
    @Transactional
    public void addStudentToClass(Long teacherId, Long classId, Long studentId) {
        // 验证教师是否有权限管理此班级
        if (!classService.validateTeacherPermission(teacherId, classId)) {
            throw new RuntimeException("教师没有权限管理此班级");
        }

        joinClass(studentId, classId);
    }

    @Override
    @Transactional
    public void removeStudentFromClass(Long teacherId, Long classId, Long studentId) {
        // 验证教师是否有权限管理此班级
        if (!classService.validateTeacherPermission(teacherId, classId)) {
            throw new RuntimeException("教师没有权限管理此班级");
        }

        leaveClass(studentId, classId);
    }

    @Override
    public int countByStudentId(Long studentId) {
        QueryWrapper<StudentClass> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);
        return Math.toIntExact(baseMapper.selectCount(wrapper));
    }

    @Override
    public List<Map<String, Object>> getStudentClassesWithDetails(Long studentId) {
        List<Map<String, Object>> result = new ArrayList<>();

        // 查询学生加入的班级
        QueryWrapper<StudentClass> studentClassWrapper = new QueryWrapper<>();
        studentClassWrapper.eq("student_id", studentId);
        List<StudentClass> studentClasses = baseMapper.selectList(studentClassWrapper);

        for (StudentClass studentClass : studentClasses) {
            // 查询班级信息
            com.english.entity.Class classInfo = classMapper.selectById(studentClass.getClassId());
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
        List<StudentClass> studentClasses = baseMapper.selectList(studentClassWrapper);

        for (StudentClass studentClass : studentClasses) {
            // 查询班级信息
            com.english.entity.Class classInfo = classMapper.selectById(studentClass.getClassId());
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

        return baseMapper.selectList(wrapper).stream()
                .map(StudentClass::getClassId)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getStudentClassInfo(Long studentId, Long classId) {
        QueryWrapper<StudentClass> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId)
              .eq("class_id", classId);

        StudentClass studentClass = baseMapper.selectOne(wrapper);
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
        QueryWrapper<com.english.entity.Class> classWrapper = new QueryWrapper<>();
        classWrapper.eq("id", classId);
        com.english.entity.Class classInfo = classMapper.selectOne(classWrapper);
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
        QueryWrapper<com.english.entity.Class> classWrapper = new QueryWrapper<>();
        classWrapper.eq("id", classId);
        com.english.entity.Class classInfo = classMapper.selectOne(classWrapper);
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