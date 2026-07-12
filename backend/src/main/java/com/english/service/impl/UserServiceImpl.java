package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.datasource.UseMaster;
import com.english.dto.LoginRequest;
import com.english.entity.User;
import com.english.mapper.UserMapper;
import com.english.mapper.SchoolStudentMapper;
import com.english.mapper.AdministrativeClassMapper;
import com.english.entity.SchoolStudent;
import com.english.service.UserService;
import com.english.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.english.exception.BusinessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SchoolStudentMapper schoolStudentMapper;

    @Autowired
    private AdministrativeClassMapper administrativeClassMapper;

    /** JwtAuthenticationFilter 中 user:active 缓存的前缀，需保持一致 */
    private static final String USER_ACTIVE_CACHE_PREFIX = "user:active:";

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

        // 生成双 token：access token（2 小时）+ refresh token（30 天）。
        // access token 用于访问受保护资源，refresh token 仅用于 /api/auth/refresh 换取新 access token。
        String accessToken = jwtUtil.generateAccessToken(user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        // 清除密码，避免返回给前端
        user.setPassword(null);

        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken); // 兼容字段 = accessToken
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("user", user);
        result.put("expiresIn", JwtUtil.ACCESS_TOKEN_EXPIRATION_MILLIS);
        result.put("refreshExpiresIn", JwtUtil.REFRESH_TOKEN_EXPIRATION_MILLIS);

        return result;
    }

    @Override
    public User getUserById(Long id) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("deleted", 0);
        return getOne(wrapper);
    }

    @Override
    public List<User> getUsersByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        // 去重后批量查询，listByIds 由 MyBatis-Plus ServiceImpl 提供
        Set<Long> distinct = new HashSet<>(ids);
        List<User> users = listByIds(distinct);
        return users != null ? users : new ArrayList<>();
    }

    @Override
    @UseMaster
    public User getUserByUsername(String username) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        // 兼容 deleted=0 和 deleted IS NULL（防止直接插入的用户 deleted 字段为 NULL）
        queryWrapper.and(w -> w.eq("deleted", 0).or().isNull("deleted"));
        return getOne(queryWrapper);
    }

    @Override
    @UseMaster
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

        // 批量反查 school_student 补充学院/行政班信息
        List<User> records = pageResult.getRecords();
        List<Map<String, Object>> enrichedRecords = enrichWithSchoolInfo(records);

        Map<String, Object> result = new HashMap<>();
        result.put("records", enrichedRecords);
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    private List<Map<String, Object>> enrichWithSchoolInfo(List<User> users) {
        if (users == null || users.isEmpty()) return new ArrayList<>();

        Set<Long> ssIds = users.stream()
                .filter(u -> u.getSchoolStudentId() != null)
                .map(User::getSchoolStudentId)
                .collect(Collectors.toSet());
        Set<String> usernames = users.stream()
                .map(User::getUsername)
                .filter(username -> username != null && !username.isBlank())
                .collect(Collectors.toSet());

        Map<Long, SchoolStudent> ssMap = new HashMap<>();
        if (!ssIds.isEmpty()) {
            QueryWrapper<SchoolStudent> ssWrapper = new QueryWrapper<>();
            ssWrapper.in("id", ssIds);
            schoolStudentMapper.selectList(ssWrapper).forEach(ss -> ssMap.put(ss.getId(), ss));
        }

        Map<String, SchoolStudent> ssByStudentId = new HashMap<>();
        if (!usernames.isEmpty()) {
            QueryWrapper<SchoolStudent> byStudentIdWrapper = new QueryWrapper<>();
            byStudentIdWrapper.in("student_id", usernames);
            schoolStudentMapper.selectList(byStudentIdWrapper).forEach(ss -> {
                if (ss.getStudentId() != null && !ss.getStudentId().isBlank()) {
                    ssByStudentId.putIfAbsent(ss.getStudentId(), ss);
                }
            });
        }

        List<Map<String, Object>> result = new ArrayList<>(users.size());
        for (User u : users) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", u.getId());
            row.put("username", u.getUsername());
            row.put("realName", u.getRealName());
            row.put("nickname", u.getNickname());
            row.put("email", u.getEmail());
            row.put("role", u.getRole());
            row.put("createdTime", u.getCreatedTime());
            row.put("schoolStudentId", u.getSchoolStudentId());

            SchoolStudent schoolStudent = null;
            if (u.getSchoolStudentId() != null) {
                schoolStudent = ssMap.get(u.getSchoolStudentId());
            }
            if (schoolStudent == null && u.getUsername() != null && !u.getUsername().isBlank()) {
                schoolStudent = ssByStudentId.get(u.getUsername());
            }
            if (schoolStudent != null) {
                row.put("collegeName", schoolStudent.getCollegeName());
                row.put("className", schoolStudent.getClassName());
                if (u.getSchoolStudentId() == null && schoolStudent.getId() != null) {
                    row.put("schoolStudentId", schoolStudent.getId());
                }
            }
            result.add(row);
        }
        return result;
    }

    @Override
    @Transactional
    public void createUser(User user) {
        // 检查用户名是否已存在（活跃用户）
        User existingUser = getUserByUsername(user.getUsername());
        if (existingUser != null) {
            throw new BusinessException("用户名已存在");
        }

        // 检查是否有同名的软删除用户 → 恢复并更新信息
        User deletedUser = baseMapper.findDeletedByUsername(user.getUsername());
        if (deletedUser != null) {
            log.info("发现同名软删除用户 id={}，恢复并更新", deletedUser.getId());
            // 先恢复（undeleteById 绕过 @TableLogic，设 deleted=0）
            baseMapper.undeleteById(deletedUser.getId());
            // 刷新 deletedUser 的 deleted 状态，使 updateById 的 WHERE deleted=0 能匹配
            deletedUser.setDeleted(0);
            // 用新信息覆盖旧数据
            deletedUser.setPassword(passwordEncoder.encode(user.getPassword()));
            deletedUser.setEmail(user.getEmail());
            deletedUser.setNickname(user.getNickname());
            deletedUser.setRealName(user.getRealName());
            deletedUser.setRole(user.getRole());
            deletedUser.setStatus(user.getStatus() != null ? user.getStatus() : "ACTIVE");
            deletedUser.setAvatar(user.getAvatar());
            deletedUser.setSsoBound(user.getSsoBound());
            deletedUser.setSsoProvider(user.getSsoProvider());
            deletedUser.setSsoSubject(user.getSsoSubject());
            deletedUser.setUpdatedTime(LocalDateTime.now());
            updateById(deletedUser);
            // 将 ID 回写到入参 user，供调用方获取新建用户 ID
            user.setId(deletedUser.getId());
            return;
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

        // 角色或状态变更时，清除 user:active 缓存，使旧 JWT 立即失效
        // 缓存 key 格式: user:active:{username}:{role}
        boolean roleChanged = user.getRole() != null && !user.getRole().equals(existingUser.getRole());
        boolean statusChanged = user.getStatus() != null && !user.getStatus().equals(existingUser.getStatus());
        if (roleChanged || statusChanged) {
            String reason = roleChanged ? String.format("角色变更: %s → %s", existingUser.getRole(), user.getRole())
                    : String.format("状态变更: %s → %s", existingUser.getStatus(), user.getStatus());
            log.info("用户 {} {}，清除 user:active 缓存", existingUser.getUsername(), reason);
            try {
                // 清除旧角色缓存（用户当前使用的 JWT 包含旧角色）
                stringRedisTemplate.delete(USER_ACTIVE_CACHE_PREFIX + existingUser.getUsername() + ":" + existingUser.getRole());
                // 清除新角色缓存（角色变更后新 JWT 可能已经缓存）
                if (roleChanged) {
                    stringRedisTemplate.delete(USER_ACTIVE_CACHE_PREFIX + existingUser.getUsername() + ":" + user.getRole());
                }
            } catch (Exception e) {
                log.warn("清除 user:active 缓存失败（Redis 不可用），变更将在缓存过期后生效（最多5分钟）", e);
            }
        }
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

        // 校园名册与在册行政班统计（用户管理页 header 展示）
        try {
            stats.put("rosterActiveClassCount",   administrativeClassMapper.countActiveClasses());
            stats.put("rosterActiveCollegeCount", administrativeClassMapper.countActiveDistinctColleges());
            stats.put("coveredClassCount",        schoolStudentMapper.countDistinctClassName());
            stats.put("coveredCollegeCount",      schoolStudentMapper.countDistinctCollegeName());
        } catch (Exception e) {
            // 名册表尚未同步时给 0 兜底，不影响原有 stats
            stats.putIfAbsent("rosterActiveClassCount",   0L);
            stats.putIfAbsent("rosterActiveCollegeCount", 0L);
            stats.putIfAbsent("coveredClassCount",        0L);
            stats.putIfAbsent("coveredCollegeCount",      0L);
        }
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
