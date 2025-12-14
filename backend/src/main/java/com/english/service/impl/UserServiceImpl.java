package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.dto.LoginRequest;
import com.english.entity.User;
import com.english.entity.VocabularyBook;
import com.english.entity.LearningRecord;
import com.english.entity.AnswerRecord;
import com.english.entity.StudentClass;
import com.english.entity.Class;
import com.english.mapper.UserMapper;
import com.english.mapper.VocabularyBookMapper;
import com.english.mapper.LearningRecordMapper;
import com.english.mapper.AnswerRecordMapper;
import com.english.mapper.StudentClassMapper;
import com.english.mapper.ClassMapper;
import com.english.service.UserService;
import com.english.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private VocabularyBookMapper vocabularyBookMapper;

    @Autowired
    private LearningRecordMapper learningRecordMapper;

    @Autowired
    private AnswerRecordMapper answerRecordMapper;

    @Autowired
    private StudentClassMapper studentClassMapper;

    @Autowired
    private ClassMapper classMapper;

    @PostConstruct
    public void init() {
        initDefaultAdmin();
    }

    @Override
    public Map<String, Object> login(LoginRequest loginRequest) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", loginRequest.getUsername());
        queryWrapper.eq("deleted", 0);
        User user = getOne(queryWrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 生成JWT Token和Refresh Token
        String accessToken = jwtUtil.generateToken(user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername(), user.getRole());

        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("user", user);
        result.put("expiresIn", 86400000L); // 24小时

        return result;
    }

    @Override
    public User getUserById(Long id) {
        return getById(id);
    }

    @Override
    public User getUserByUsername(String username) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        queryWrapper.eq("deleted", 0);
        return getOne(queryWrapper);
    }

    @Override
    public Map<String, Object> getUsersByPage(Map<String, Object> params) {
        int page = (int) params.get("page");
        int size = (int) params.get("size");
        String username = (String) params.get("username");
        String email = (String) params.get("email");
        String role = (String) params.get("role");

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (username != null && !username.trim().isEmpty()) {
            queryWrapper.like("username", username);
        }
        if (email != null && !email.trim().isEmpty()) {
            queryWrapper.like("email", email);
        }
        if (role != null && !role.trim().isEmpty()) {
            queryWrapper.eq("role", role);
        }
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");

        IPage<User> pageResult = page(new Page<>(page, size), queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    public void createUser(User user) {
        // 检查用户名是否已存在
        User existingUser = getUserByUsername(user.getUsername());
        if (existingUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查邮箱是否已存在
        QueryWrapper<User> emailWrapper = new QueryWrapper<>();
        emailWrapper.eq("email", user.getEmail());
        emailWrapper.eq("deleted", 0);
        User existingEmail = getOne(emailWrapper);
        if (existingEmail != null) {
            throw new RuntimeException("邮箱已存在");
        }

        // 密码加密
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreatedTime(LocalDateTime.now());
        user.setUpdatedTime(LocalDateTime.now());
        user.setDeleted(0);
        user.setStatus("ACTIVE");

        save(user);
    }

    @Override
    public void updateUser(User user) {
        User existingUser = getUserById(user.getId());
        if (existingUser == null) {
            throw new RuntimeException("用户不存在");
        }

        // 如果修改了密码，需要加密
        if (user.getPassword() != null && !user.getPassword().isEmpty() &&
            !user.getPassword().equals(existingUser.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(existingUser.getPassword());
        }

        user.setUpdatedTime(LocalDateTime.now());
        updateById(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = getUserById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        String role = user.getRole();

        if ("STUDENT".equals(role)) {
            // 学生删除：清理相关数据

            // 删除词汇本记录
            QueryWrapper<VocabularyBook> vocabWrapper = new QueryWrapper<>();
            vocabWrapper.eq("student_id", id);
            vocabularyBookMapper.delete(vocabWrapper);

            // 删除学习记录
            QueryWrapper<LearningRecord> learningWrapper = new QueryWrapper<>();
            learningWrapper.eq("student_id", id);
            learningRecordMapper.delete(learningWrapper);

            // 删除答题记录
            QueryWrapper<AnswerRecord> answerWrapper = new QueryWrapper<>();
            answerWrapper.eq("student_id", id);
            answerRecordMapper.delete(answerWrapper);

            // 删除学生-班级关联
            QueryWrapper<StudentClass> studentClassWrapper = new QueryWrapper<>();
            studentClassWrapper.eq("student_id", id);
            studentClassMapper.delete(studentClassWrapper);

        } else if ("TEACHER".equals(role)) {
            // 教师删除：检查是否有关联班级
            QueryWrapper<Class> classWrapper = new QueryWrapper<>();
            classWrapper.eq("teacher_id", id);
            Long classCount = classMapper.selectCount(classWrapper);

            if (classCount > 0) {
                throw new RuntimeException("该教师还有 " + classCount + " 个班级，无法删除。请先转移或删除这些班级。");
            }
        }

        // 软删除用户
        User deleteUser = new User();
        deleteUser.setId(id);
        deleteUser.setDeleted(1);
        deleteUser.setUpdatedTime(LocalDateTime.now());
        updateById(deleteUser);
    }

    @Override
    public void initDefaultAdmin() {
        try {
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("username", "admin");
            wrapper.eq("deleted", 0);
            User existingAdmin = getOne(wrapper);

            if (existingAdmin == null) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setNickname("系统管理员");
                admin.setEmail("admin@english.com");
                admin.setRole("ADMIN");
                admin.setStatus("ACTIVE");
                admin.setCreatedTime(LocalDateTime.now());
                admin.setUpdatedTime(LocalDateTime.now());
                admin.setDeleted(0);

                save(admin);
                System.out.println("✅ 默认管理员账户创建成功: admin/admin123");
            } else {
                System.out.println("✅ 默认管理员账户已存在: admin");
            }
        } catch (Exception e) {
            System.err.println("❌ 初始化默认管理员失败: " + e.getMessage());
            // 不抛出异常,避免影响应用启动
        }
    }

    @Override
    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public List<User> getTeachers() {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("role", "TEACHER");
        wrapper.eq("status", "ACTIVE");
        wrapper.eq("deleted", 0);
        wrapper.orderByDesc("created_time");
        return list(wrapper);
    }

    @Override
    public List<User> getStudents() {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("role", "STUDENT");
        wrapper.eq("status", "ACTIVE");
        wrapper.eq("deleted", 0);
        wrapper.orderByDesc("created_time");
        return list(wrapper);
    }

    @Override
    public Map<String, Object> getUserStats() {
        Map<String, Object> stats = new HashMap<>();

        QueryWrapper<User> adminWrapper = new QueryWrapper<>();
        adminWrapper.eq("role", "ADMIN");
        adminWrapper.eq("deleted", 0);
        Long adminCount = count(adminWrapper);

        QueryWrapper<User> teacherWrapper = new QueryWrapper<>();
        teacherWrapper.eq("role", "TEACHER");
        teacherWrapper.eq("deleted", 0);
        Long teacherCount = count(teacherWrapper);

        QueryWrapper<User> studentWrapper = new QueryWrapper<>();
        studentWrapper.eq("role", "STUDENT");
        studentWrapper.eq("deleted", 0);
        Long studentCount = count(studentWrapper);

        QueryWrapper<User> activeWrapper = new QueryWrapper<>();
        activeWrapper.eq("status", "ACTIVE");
        activeWrapper.eq("deleted", 0);
        Long activeCount = count(activeWrapper);

        stats.put("totalAdmins", adminCount);
        stats.put("totalTeachers", teacherCount);
        stats.put("totalStudents", studentCount);
        stats.put("totalActive", activeCount);
        stats.put("totalUsers", adminCount + teacherCount + studentCount);

        return stats;
    }

    @Override
    public void updateUserStatus(Long id, String status) {
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        user.setUpdatedTime(LocalDateTime.now());
        updateById(user);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        User user = new User();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedTime(LocalDateTime.now());
        updateById(user);
    }
}