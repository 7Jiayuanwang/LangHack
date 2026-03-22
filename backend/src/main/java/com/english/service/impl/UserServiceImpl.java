package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.dto.LoginRequest;
import com.english.entity.User;
import com.english.mapper.UserMapper;
import com.english.service.UserService;
import com.english.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.english.exception.BusinessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
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

    @PostConstruct
    public void init() {
        initDefaultAdmin();
    }

    @Override
    public Map<String, Object> login(LoginRequest loginRequest) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", loginRequest.getUsername());
        User user = getOne(queryWrapper);

        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("账户已被禁用，请联系管理员");
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        // 生成JWT Token
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

        // 清除密码，避免返回给前端
        user.setPassword(null);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        result.put("expiresIn", jwtUtil.getExpiration()); // 与 JWT 配置保持一致

        return result;
    }

    @Override
    public User getUserById(Long id) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("deleted", 0);
        return getOne(wrapper);
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
        int page = params.get("page") != null ? ((Number) params.get("page")).intValue() : 1;
        int size = params.get("size") != null ? ((Number) params.get("size")).intValue() : 10;
        String keyword = (String) params.get("keyword");
        String role = (String) params.get("role");

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();

        // 支持关键词搜索：用户名、真实姓名、邮箱
        if (keyword != null && !keyword.isBlank()) {
            queryWrapper.and(wrapper -> wrapper
                .like("username", keyword)
                .or()
                .like("real_name", keyword)
                .or()
                .like("email", keyword)
            );
        }

        if (role != null && !role.isBlank()) {
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
    @Transactional
    public void createUser(User user) {
        // 检查用户名是否已存在
        User existingUser = getUserByUsername(user.getUsername());
        if (existingUser != null) {
            throw new BusinessException("用户名已存在");
        }

        // 检查邮箱是否已存在（邮箱为空时跳过）
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            QueryWrapper<User> emailWrapper = new QueryWrapper<>();
            emailWrapper.eq("email", user.getEmail().trim());
            emailWrapper.eq("deleted", 0);
            User existingEmail = getOne(emailWrapper);
            if (existingEmail != null) {
                throw new BusinessException("邮箱已存在");
            }
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
    @Transactional
    @CacheEvict(value = "stats", allEntries = true)
    public void updateUser(User user) {
        User existingUser = getUserById(user.getId());
        if (existingUser == null) {
            throw new BusinessException("用户不存在");
        }

        // 如果传入了新密码（明文），则加密后更新；否则保留原密码
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(existingUser.getPassword());
        }

        user.setUpdatedTime(LocalDateTime.now());
        updateById(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = "stats", allEntries = true)
    public void deleteUser(Long id) {
        // removeById 配合 @TableLogic 会生成 UPDATE SET deleted=1 WHERE id=? AND deleted=0
        // 不能使用 setDeleted(1)+updateById()，因为 @TableLogic 会从 SET 子句中排除 deleted 字段
        removeById(id);
    }

    @Override
    @Transactional
    public void initDefaultAdmin() {
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
        } else {
            // admin already exists
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
        wrapper.in("role", java.util.Arrays.asList("STUDENT", "USER"));
        wrapper.eq("status", "ACTIVE");
        wrapper.eq("deleted", 0);
        wrapper.orderByDesc("created_time");
        return list(wrapper);
    }

    @Override
    public List<User> searchUsers(String keyword, String role) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();

        // 搜索条件：用户名、真实姓名、昵称
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like("username", keyword)
                .or()
                .like("real_name", keyword)
                .or()
                .like("nickname", keyword)
            );
        }

        // 角色筛选
        if (role != null && !role.isBlank()) {
            wrapper.eq("role", role);
        }

        // 只搜索活跃用户
        wrapper.eq("status", "ACTIVE");
        wrapper.eq("deleted", 0);

        // 限制结果数量，避免返回过多数据
        wrapper.last("LIMIT 50");

        // 按用户名排序
        wrapper.orderByAsc("username");

        return list(wrapper);
    }

    @Override
    public Map<String, Object> getUserStats() {
        Map<String, Object> raw = baseMapper.selectUserStats();
        if (raw == null) {
            raw = new HashMap<>();
        }
        long adminCount   = raw.getOrDefault("adminCount",   0L) instanceof Number n ? n.longValue() : 0L;
        long teacherCount = raw.getOrDefault("teacherCount", 0L) instanceof Number n ? n.longValue() : 0L;
        long studentCount = raw.getOrDefault("studentCount", 0L) instanceof Number n ? n.longValue() : 0L;
        long activeCount  = raw.getOrDefault("activeCount",  0L) instanceof Number n ? n.longValue() : 0L;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAdmins",   adminCount);
        stats.put("totalTeachers", teacherCount);
        stats.put("totalStudents", studentCount);
        stats.put("totalActive",   activeCount);
        stats.put("totalUsers",    adminCount + teacherCount + studentCount);
        return stats;
    }

    @Override
    @Transactional
    @CacheEvict(value = "stats", allEntries = true)
    public void updateUserStatus(Long id, String status) {
        User existingUser = getUserById(id);
        if (existingUser == null) {
            throw new BusinessException("用户不存在");
        }
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        user.setUpdatedTime(LocalDateTime.now());
        updateById(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = "stats", allEntries = true)
    public void resetPassword(Long id, String newPassword) {
        User existingUser = getUserById(id);
        if (existingUser == null) {
            throw new BusinessException("用户不存在");
        }
        User user = new User();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedTime(LocalDateTime.now());
        updateById(user);
    }
}