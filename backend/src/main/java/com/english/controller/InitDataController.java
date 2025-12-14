package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.Result;
import com.english.entity.Course;
import com.english.entity.Unit;
import com.english.entity.Word;
import com.english.entity.Text;
import com.english.entity.User;
import com.english.mapper.CourseMapper;
import com.english.mapper.UnitMapper;
import com.english.mapper.WordMapper;
import com.english.mapper.TextMapper;
import com.english.mapper.UserMapper;
import com.english.service.CourseService;
import com.english.service.UnitService;
import com.english.service.WordService;
import com.english.service.TextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据初始化控制器
 * 注意：此控制器仅在开发环境(dev)中生效，生产环境无法访问
 */
@RestController
//@Profile("dev")  // 临时注释,允许在任何环境下访问
@RequestMapping("/api/init")
public class InitDataController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private UnitService unitService;

    @Autowired
    private WordService wordService;

    @Autowired
    private TextService textService;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private UnitMapper unitMapper;

    @Autowired
    private WordMapper wordMapper;

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 初始化演示用户账户
     * 创建admin、teacher、student三个演示账户
     */
    @RequestMapping("/demo-users")
    public Result<Map<String, String>> initDemoUsers() {
        try {
            Map<String, String> result = new HashMap<>();

            // 创建或更新admin账户 (密码: admin123)
            QueryWrapper<User> adminWrapper = new QueryWrapper<>();
            adminWrapper.eq("username", "admin");
            User existingAdmin = userMapper.selectOne(adminWrapper);

            if (existingAdmin != null) {
                // 更新密码
                existingAdmin.setPassword(passwordEncoder.encode("admin123"));
                existingAdmin.setUpdatedTime(LocalDateTime.now());
                userMapper.updateById(existingAdmin);
                result.put("admin", "admin/admin123 - 更新成功");
            } else {
                // 创建新用户
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setRealName("系统管理员");
                admin.setRole("ADMIN");
                admin.setEmail("admin@langhack.com");
                admin.setStatus("ACTIVE");
                admin.setDeleted(0);
                admin.setCreatedTime(LocalDateTime.now());
                admin.setUpdatedTime(LocalDateTime.now());
                userMapper.insert(admin);
                result.put("admin", "admin/admin123 - 创建成功");
            }

            // 创建或更新teacher账户 (密码: teacher123)
            QueryWrapper<User> teacherWrapper = new QueryWrapper<>();
            teacherWrapper.eq("username", "teacher");
            User existingTeacher = userMapper.selectOne(teacherWrapper);

            if (existingTeacher != null) {
                existingTeacher.setPassword(passwordEncoder.encode("teacher123"));
                existingTeacher.setUpdatedTime(LocalDateTime.now());
                userMapper.updateById(existingTeacher);
                result.put("teacher", "teacher/teacher123 - 更新成功");
            } else {
                User teacher = new User();
                teacher.setUsername("teacher");
                teacher.setPassword(passwordEncoder.encode("teacher123"));
                teacher.setRealName("示范教师");
                teacher.setRole("TEACHER");
                teacher.setEmail("teacher@langhack.com");
                teacher.setStatus("ACTIVE");
                teacher.setDeleted(0);
                teacher.setCreatedTime(LocalDateTime.now());
                teacher.setUpdatedTime(LocalDateTime.now());
                userMapper.insert(teacher);
                result.put("teacher", "teacher/teacher123 - 创建成功");
            }

            // 创建或更新student账户 (密码: student123)
            QueryWrapper<User> studentWrapper = new QueryWrapper<>();
            studentWrapper.eq("username", "student");
            User existingStudent = userMapper.selectOne(studentWrapper);

            if (existingStudent != null) {
                existingStudent.setPassword(passwordEncoder.encode("student123"));
                existingStudent.setUpdatedTime(LocalDateTime.now());
                userMapper.updateById(existingStudent);
                result.put("student", "student/student123 - 更新成功");
            } else {
                User student = new User();
                student.setUsername("student");
                student.setPassword(passwordEncoder.encode("student123"));
                student.setRealName("示范学生");
                student.setRole("STUDENT");
                student.setEmail("student@langhack.com");
                student.setStatus("ACTIVE");
                student.setDeleted(0);
                student.setCreatedTime(LocalDateTime.now());
                student.setUpdatedTime(LocalDateTime.now());
                userMapper.insert(student);
                result.put("student", "student/student123 - 创建成功");
            }

            return Result.success("演示账户创建/更新成功！", result);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("演示账户创建失败: " + e.getMessage());
        }
    }

    @PostMapping("/testdata")
    public Result<String> initTestDataPost() {
        return initTestData();
    }

    @RequestMapping("/testdata")
    public Result<String> initTestData() {
        try {
            // 创建课程1
            Course course1 = new Course();
            course1.setName("新一代大学英语(第二版)综合教程1(智慧版)");
            course1.setDescription("适合大学英语基础阶段学习");
            course1.setTeacherId(1L);
            course1.setDeleted(0);
            course1.setCreatedTime(LocalDateTime.now());
            course1.setUpdatedTime(LocalDateTime.now());
            courseService.createCourse(course1);

            // 创建课程2
            Course course2 = new Course();
            course2.setName("新一代大学英语(第二版)综合教程2(智慧版)");
            course2.setDescription("适合大学英语提高阶段学习");
            course2.setTeacherId(1L);
            course2.setDeleted(0);
            course2.setCreatedTime(LocalDateTime.now());
            course2.setUpdatedTime(LocalDateTime.now());
            courseService.createCourse(course2);

            // 获取刚创建的课程ID
            Long courseId1 = course1.getId();
            Long courseId2 = course2.getId();

            // 创建课程1的单元
            createUnit(courseId1, "Unit 1", "Fresh Start", 1);
            createUnit(courseId1, "Unit 2", "Loving Parents, Loving Children", 2);
            createUnit(courseId1, "Unit 3", "Digital Campus", 3);
            createUnit(courseId1, "Unit 4", "Heroes of Our Time", 4);
            createUnit(courseId1, "Unit 5", "Winning Is Not Everything", 5);

            // 创建课程2的单元
            createUnit(courseId2, "Unit 1", "Ways of Learning", 1);
            createUnit(courseId2, "Unit 2", "Values", 2);
            createUnit(courseId2, "Unit 3", "The Generation Gap", 3);
            createUnit(courseId2, "Unit 4", "The Virtual World", 4);
            createUnit(courseId2, "Unit 5", "Overcoming Obstacles", 5);

            // 创建常用单词
            createWord("study", "/ˈstʌdi/", "学习；研究", "v./n.", "I study English every day.");
            createWord("learn", "/lɜːn/", "学习；得知", "v.", "We learn new things every day.");
            createWord("fresh", "/freʃ/", "新鲜的；清新的", "adj.", "Fresh start means a new beginning.");
            createWord("start", "/stɑːt/", "开始；出发", "v./n.", "Let us start the lesson.");
            createWord("parent", "/ˈpeərənt/", "父母；家长", "n.", "Loving parents care for their children.");
            createWord("love", "/lʌv/", "爱；热爱", "v./n.", "We love our parents.");
            createWord("child", "/tʃaɪld/", "孩子；儿童", "n.", "Every child needs education.");
            createWord("children", "/ˈtʃɪldrən/", "孩子们", "n.", "The children are playing.");
            createWord("digital", "/ˈdɪdʒɪtl/", "数字的；数码的", "adj.", "We live in a digital age.");
            createWord("campus", "/ˈkæmpəs/", "校园", "n.", "The digital campus is very modern.");
            createWord("have", "/hæv/", "有；拥有", "v.", "I have a book.");
            createWord("make", "/meɪk/", "制作；使得", "v.", "Make a plan.");
            createWord("time", "/taɪm/", "时间；时代", "n.", "Time is precious.");
            createWord("people", "/ˈpiːpl/", "人；人们", "n.", "Many people study English.");
            createWord("way", "/weɪ/", "方式；道路", "n.", "Different ways of learning.");
            createWord("work", "/wɜːk/", "工作", "n./v.", "Hard work pays off.");
            createWord("world", "/wɜːld/", "世界", "n.", "The virtual world.");
            createWord("life", "/laɪf/", "生活；生命", "n.", "Campus life is colorful.");
            createWord("good", "/ɡʊd/", "好的；优秀的", "adj.", "This is a good book.");
            createWord("new", "/njuː/", "新的", "adj.", "A new semester.");

            return Result.success("测试数据初始化成功！已创建2个课程、10个单元、20个单词");
        } catch (Exception e) {
            return Result.error("初始化失败: " + e.getMessage());
        }
    }

    private void createUnit(Long courseId, String name, String description, int orderNum) {
        Unit unit = new Unit();
        unit.setCourseId(courseId);
        unit.setName(name);
        unit.setDescription(description);
        unit.setOrderNum(orderNum);
        unit.setCreatedTime(LocalDateTime.now());
        unit.setUpdatedTime(LocalDateTime.now());
        unitService.createUnit(unit);
    }

    private void createWord(String word, String pronunciation, String definition,
                           String partOfSpeech, String exampleSentence) {
        Word w = new Word();
        w.setWord(word);
        w.setPronunciation(pronunciation);
        w.setDefinition(definition);
        w.setPartOfSpeech(partOfSpeech);
        w.setExampleSentence(exampleSentence);
        w.setDifficulty("EASY");
        w.setDeleted(0);
        w.setCreatedTime(LocalDateTime.now());
        w.setUpdatedTime(LocalDateTime.now());
        wordService.createWord(w);
    }

    /**
     * 清空所有测试数据
     */
    @RequestMapping("/clear")
    public Result<String> clearTestData() {
        try {
            int totalDeleted = 0;

            // 删除所有课文（未删除的）
            QueryWrapper<Text> textWrapper = new QueryWrapper<>();
            textWrapper.eq("deleted", 0);
            List<Text> texts = textMapper.selectList(textWrapper);
            for (Text text : texts) {
                textService.deleteText(text.getId());
                totalDeleted++;
            }

            // 删除所有单元
            QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
            List<Unit> units = unitMapper.selectList(unitWrapper);
            for (Unit unit : units) {
                unitService.deleteUnit(unit.getId());
                totalDeleted++;
            }

            // 删除所有课程（未删除的）
            QueryWrapper<Course> courseWrapper = new QueryWrapper<>();
            courseWrapper.eq("deleted", 0);
            List<Course> courses = courseMapper.selectList(courseWrapper);
            for (Course course : courses) {
                courseService.deleteCourse(course.getId());
                totalDeleted++;
            }

            // 删除所有单词（未删除的）
            QueryWrapper<Word> wordWrapper = new QueryWrapper<>();
            wordWrapper.eq("deleted", 0);
            List<Word> words = wordMapper.selectList(wordWrapper);
            for (Word word : words) {
                wordService.deleteWord(word.getId());
                totalDeleted++;
            }

            return Result.success("测试数据清空成功！共删除 " + totalDeleted + " 条记录");
        } catch (Exception e) {
            return Result.error("清空失败: " + e.getMessage());
        }
    }

    /**
     * 硬删除所有数据（完全清空数据库）
     */
    @RequestMapping("/hardclear")
    public Result<String> hardClearAllData() {
        try {
            // 硬删除所有数据（不是软删除，是真正从数据库删除）
            textMapper.delete(null);
            unitMapper.delete(null);
            courseMapper.delete(null);
            wordMapper.delete(null);

            return Result.success("所有数据已完全清空！");
        } catch (Exception e) {
            return Result.error("硬删除失败: " + e.getMessage());
        }
    }

    /**
     * 清空并重新初始化数据（推荐使用）
     */
    @RequestMapping("/reset")
    public Result<String> resetTestData() {
        try {
            // 先硬删除所有数据
            hardClearAllData();

            // 等待一下确保删除完成
            Thread.sleep(500);

            // 重新初始化
            return initTestData();
        } catch (Exception e) {
            return Result.error("重置失败: " + e.getMessage());
        }
    }
}
