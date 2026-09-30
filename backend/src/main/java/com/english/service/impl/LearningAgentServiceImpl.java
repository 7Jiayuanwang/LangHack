package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.learner.LearnerProfileDto;
import com.english.dto.learner.LearningAgentContextDto;
import com.english.dto.learner.LearningAgentMemoryRequest;
import com.english.dto.learner.LearningAgentOnboardingRequest;
import com.english.entity.LearnerAgentProfile;
import com.english.entity.LearnerGoal;
import com.english.entity.LearnerInterest;
import com.english.entity.LearnerMemory;
import com.english.entity.StudentSchedule;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.LearnerAgentProfileMapper;
import com.english.mapper.LearnerGoalMapper;
import com.english.mapper.LearnerInterestMapper;
import com.english.mapper.LearnerMemoryMapper;
import com.english.service.LearnerProfileService;
import com.english.service.LearningAgentService;
import com.english.service.SchoolDataSyncService;
import com.english.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningAgentServiceImpl implements LearningAgentService {

    private static final Set<String> GOAL_TYPES = Set.of(
            "CET4", "CET6", "POSTGRADUATE", "ACADEMIC_READING", "COURSE", "GENERAL", "OTHER");
    private static final Set<String> LEVELS = Set.of(
            "BEGINNER", "ELEMENTARY", "INTERMEDIATE", "UPPER_INTERMEDIATE", "ADVANCED");
    private static final Set<String> FORMATS = Set.of(
            "READING", "VOCABULARY", "QUIZ", "WRITING", "CONVERSATION", "VIDEO");
    private static final Set<String> ENCOURAGEMENT_STYLES = Set.of("GENTLE", "DIRECT", "CHALLENGE");
    private static final Set<String> MEMORY_TYPES = Set.of("PROFILE", "PREFERENCE", "HABIT", "GOAL", "DIFFICULTY");

    private static final Map<String, String> DEFAULT_GOAL_TITLES;

    static {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put("CET4", "通过大学英语四级");
        titles.put("CET6", "通过大学英语六级");
        titles.put("POSTGRADUATE", "准备研究生英语考试");
        titles.put("ACADEMIC_READING", "提升英文论文阅读能力");
        titles.put("COURSE", "完成大学英语课程学习");
        titles.put("GENERAL", "用英语获取信息和拓展兴趣");
        titles.put("OTHER", "自定义英语学习目标");
        DEFAULT_GOAL_TITLES = Collections.unmodifiableMap(titles);
    }

    private final LearnerAgentProfileMapper profileMapper;
    private final LearnerGoalMapper goalMapper;
    private final LearnerInterestMapper interestMapper;
    private final LearnerMemoryMapper memoryMapper;
    private final LearnerProfileService learnerProfileService;
    private final SchoolDataSyncService schoolDataSyncService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    public LearningAgentContextDto getContext(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("用户不存在");
        }

        LearnerAgentProfile settings = getSettings(userId);
        List<LearnerGoal> goals = goalMapper.selectList(new QueryWrapper<LearnerGoal>()
                .eq("user_id", userId)
                .orderByDesc("priority")
                .orderByAsc("target_date")
                .orderByAsc("id"));
        List<LearnerInterest> interests = interestMapper.selectList(new QueryWrapper<LearnerInterest>()
                .eq("user_id", userId)
                .orderByDesc("weight")
                .orderByAsc("id"));
        List<LearnerMemory> memories = memoryMapper.selectList(new QueryWrapper<LearnerMemory>()
                .eq("user_id", userId)
                .eq("status", "ACTIVE")
                .and(wrapper -> wrapper.isNull("expires_time").or().gt("expires_time", LocalDateTime.now()))
                .orderByDesc("updated_time")
                .last("LIMIT 50"));

        LearningAgentContextDto context = new LearningAgentContextDto();
        if (settings != null) {
            context.setOnboardingCompleted(Boolean.TRUE.equals(settings.getOnboardingCompleted()));
            context.setCurrentLevel(settings.getCurrentLevel());
            context.setWeeklyMinutes(settings.getWeeklyMinutes());
            context.setPreferredFormats(parseFormats(settings.getPreferredFormatsJson()));
            context.setEncouragementStyle(settings.getEncouragementStyle());
            context.setMemoryConsent(Boolean.TRUE.equals(settings.getMemoryConsent()));
        }
        context.setGoals(goals.stream().map(this::toGoalView).collect(Collectors.toList()));
        context.setInterests(interests.stream().map(LearnerInterest::getName).collect(Collectors.toList()));
        context.setMemories(memories.stream().map(this::toMemoryView).collect(Collectors.toList()));

        LearnerProfileDto learnerProfile = null;
        try {
            learnerProfile = learnerProfileService.getUserProfile(userId);
        } catch (Exception e) {
            log.debug("学习伙伴暂时无法读取行为画像: userId={}, reason={}", userId, e.getMessage());
        }
        context.setLearnerProfile(learnerProfile);
        context.setSchedule(loadScheduleSummary(userId));
        context.setGreeting(buildGreeting(context));
        context.setSuggestedQuestions(buildSuggestedQuestions(context));
        return context;
    }

    @Override
    @Transactional
    public LearningAgentContextDto saveOnboarding(Long userId, LearningAgentOnboardingRequest request) {
        validateOnboarding(request);
        LocalDateTime now = LocalDateTime.now();

        LearnerAgentProfile settings = getSettings(userId);
        if (settings == null) {
            settings = new LearnerAgentProfile();
            settings.setUserId(userId);
            settings.setCreatedTime(now);
        }
        settings.setCurrentLevel(request.getCurrentLevel().trim().toUpperCase());
        settings.setWeeklyMinutes(request.getWeeklyMinutes());
        settings.setPreferredFormatsJson(writeFormats(request.getPreferredFormats()));
        settings.setEncouragementStyle(normalizeOrDefault(
                request.getEncouragementStyle(), ENCOURAGEMENT_STYLES, "GENTLE", "鼓励方式"));
        settings.setMemoryConsent(Boolean.TRUE.equals(request.getMemoryConsent()));
        settings.setOnboardingCompleted(true);
        settings.setUpdatedTime(now);
        if (settings.getId() == null) {
            profileMapper.insert(settings);
        } else {
            profileMapper.updateById(settings);
        }

        goalMapper.delete(new QueryWrapper<LearnerGoal>().eq("user_id", userId).eq("source", "USER"));
        for (LearningAgentOnboardingRequest.GoalInput input : request.getGoals()) {
            LearnerGoal goal = new LearnerGoal();
            String type = input.getGoalType().trim().toUpperCase();
            goal.setUserId(userId);
            goal.setGoalType(type);
            goal.setTitle(cleanGoalTitle(type, input.getTitle()));
            goal.setPriority(input.getPriority() == null ? 3 : input.getPriority());
            goal.setTargetDate(input.getTargetDate());
            goal.setStatus("ACTIVE");
            goal.setSource("USER");
            goal.setCreatedTime(now);
            goal.setUpdatedTime(now);
            goalMapper.insert(goal);
        }

        interestMapper.delete(new QueryWrapper<LearnerInterest>().eq("user_id", userId).eq("source", "USER"));
        for (String name : normalizeInterests(request.getInterests())) {
            LearnerInterest interest = new LearnerInterest();
            interest.setUserId(userId);
            interest.setName(name);
            interest.setSource("USER");
            interest.setWeight(BigDecimal.ONE);
            interest.setCreatedTime(now);
            interest.setUpdatedTime(now);
            interestMapper.insert(interest);
        }

        replaceOnboardingMemories(userId, settings, request.getPreferredFormats(), now);
        return getContext(userId);
    }

    @Override
    @Transactional
    public LearningAgentContextDto addMemory(Long userId, LearningAgentMemoryRequest request) {
        if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new BusinessException("记忆内容不能为空");
        }
        String content = request.getContent().trim();
        if (content.length() > 500) {
            throw new BusinessException("记忆内容不能超过500字");
        }
        String type = normalizeOrDefault(request.getMemoryType(), MEMORY_TYPES, "PREFERENCE", "记忆类型");
        LocalDateTime now = LocalDateTime.now();

        LearnerMemory memory = new LearnerMemory();
        memory.setUserId(userId);
        memory.setMemoryKey("user:" + UUID.randomUUID());
        memory.setMemoryType(type);
        memory.setContent(content);
        memory.setConfidence(BigDecimal.ONE);
        memory.setSource("USER_CONFIRMED");
        memory.setIsSensitive(Boolean.TRUE.equals(request.getSensitive()));
        memory.setStatus("ACTIVE");
        memory.setCreatedTime(now);
        memory.setUpdatedTime(now);
        memoryMapper.insert(memory);
        return getContext(userId);
    }

    @Override
    @Transactional
    public LearningAgentContextDto deleteMemory(Long userId, Long memoryId) {
        if (memoryId == null || memoryId <= 0) {
            throw new BusinessException("记忆不存在");
        }
        LearnerMemory memory = memoryMapper.selectOne(new QueryWrapper<LearnerMemory>()
                .eq("id", memoryId)
                .eq("user_id", userId)
                .last("LIMIT 1"));
        if (memory == null) {
            throw new BusinessException("记忆不存在或无权删除");
        }
        memoryMapper.deleteById(memoryId);
        return getContext(userId);
    }

    private LearnerAgentProfile getSettings(Long userId) {
        return profileMapper.selectOne(new QueryWrapper<LearnerAgentProfile>()
                .eq("user_id", userId)
                .last("LIMIT 1"));
    }

    private void validateOnboarding(LearningAgentOnboardingRequest request) {
        if (request == null) {
            throw new BusinessException("学习档案不能为空");
        }
        if (request.getGoals() == null || request.getGoals().isEmpty()) {
            throw new BusinessException("请至少选择一个学习目标");
        }
        if (request.getGoals().size() > 5) {
            throw new BusinessException("学习目标最多选择5个");
        }
        for (LearningAgentOnboardingRequest.GoalInput goal : request.getGoals()) {
            if (goal == null || goal.getGoalType() == null
                    || !GOAL_TYPES.contains(goal.getGoalType().trim().toUpperCase())) {
                throw new BusinessException("学习目标类型无效");
            }
            if (goal.getPriority() != null && (goal.getPriority() < 1 || goal.getPriority() > 5)) {
                throw new BusinessException("目标优先级必须在1到5之间");
            }
            cleanGoalTitle(goal.getGoalType().trim().toUpperCase(), goal.getTitle());
        }
        if (request.getCurrentLevel() == null
                || !LEVELS.contains(request.getCurrentLevel().trim().toUpperCase())) {
            throw new BusinessException("请选择当前英语水平");
        }
        if (request.getWeeklyMinutes() == null || request.getWeeklyMinutes() < 30 || request.getWeeklyMinutes() > 3000) {
            throw new BusinessException("每周学习时间应在30到3000分钟之间");
        }
        if (normalizeInterests(request.getInterests()).size() > 12) {
            throw new BusinessException("兴趣最多填写12项");
        }
        if (request.getPreferredFormats() == null || request.getPreferredFormats().isEmpty()) {
            throw new BusinessException("请至少选择一种学习形式");
        }
        for (String format : request.getPreferredFormats()) {
            if (format == null || !FORMATS.contains(format.trim().toUpperCase())) {
                throw new BusinessException("学习形式无效");
            }
        }
        normalizeOrDefault(request.getEncouragementStyle(), ENCOURAGEMENT_STYLES, "GENTLE", "鼓励方式");
    }

    private String cleanGoalTitle(String type, String title) {
        String cleaned = title == null ? "" : title.trim();
        if (cleaned.isEmpty()) {
            cleaned = DEFAULT_GOAL_TITLES.get(type);
        }
        if (cleaned == null || cleaned.isEmpty()) {
            throw new BusinessException("学习目标名称不能为空");
        }
        if (cleaned.length() > 100) {
            throw new BusinessException("学习目标名称不能超过100字");
        }
        return cleaned;
    }

    private List<String> normalizeInterests(List<String> interests) {
        if (interests == null) return Collections.emptyList();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String interest : interests) {
            if (interest == null || interest.trim().isEmpty()) continue;
            String cleaned = interest.trim();
            if (cleaned.length() > 40) {
                throw new BusinessException("单项兴趣不能超过40字");
            }
            result.add(cleaned);
        }
        return new ArrayList<>(result);
    }

    private String normalizeOrDefault(String value, Set<String> allowed, String defaultValue, String fieldName) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new BusinessException(fieldName + "无效");
        }
        return normalized;
    }

    private String writeFormats(List<String> formats) {
        List<String> normalized = formats.stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .map(value -> value.trim().toUpperCase())
                .distinct()
                .collect(Collectors.toList());
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new BusinessException("学习形式保存失败");
        }
    }

    private List<String> parseFormats(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            log.warn("学习伙伴偏好格式解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void replaceOnboardingMemories(Long userId, LearnerAgentProfile settings,
                                            List<String> formats, LocalDateTime now) {
        memoryMapper.delete(new QueryWrapper<LearnerMemory>()
                .eq("user_id", userId)
                .eq("source", "ONBOARDING"));
        addOnboardingMemory(userId, "onboarding:level", "PROFILE",
                "当前自评英语水平：" + settings.getCurrentLevel(), now);
        addOnboardingMemory(userId, "onboarding:weekly_minutes", "HABIT",
                "每周计划投入英语学习 " + settings.getWeeklyMinutes() + " 分钟", now);
        addOnboardingMemory(userId, "onboarding:formats", "PREFERENCE",
                "偏好的学习形式：" + String.join("、", formats), now);
    }

    private void addOnboardingMemory(Long userId, String key, String type, String content, LocalDateTime now) {
        LearnerMemory memory = new LearnerMemory();
        memory.setUserId(userId);
        memory.setMemoryKey(key);
        memory.setMemoryType(type);
        memory.setContent(content);
        memory.setConfidence(BigDecimal.ONE);
        memory.setSource("ONBOARDING");
        memory.setIsSensitive(false);
        memory.setStatus("ACTIVE");
        memory.setCreatedTime(now);
        memory.setUpdatedTime(now);
        memoryMapper.insert(memory);
    }

    private LearningAgentContextDto.GoalView toGoalView(LearnerGoal goal) {
        LearningAgentContextDto.GoalView view = new LearningAgentContextDto.GoalView();
        view.setId(goal.getId());
        view.setGoalType(goal.getGoalType());
        view.setTitle(goal.getTitle());
        view.setPriority(goal.getPriority());
        view.setTargetDate(goal.getTargetDate());
        view.setStatus(goal.getStatus());
        return view;
    }

    private LearningAgentContextDto.MemoryView toMemoryView(LearnerMemory memory) {
        LearningAgentContextDto.MemoryView view = new LearningAgentContextDto.MemoryView();
        view.setId(memory.getId());
        view.setMemoryType(memory.getMemoryType());
        view.setContent(memory.getContent());
        view.setSource(memory.getSource());
        view.setSensitive(Boolean.TRUE.equals(memory.getIsSensitive()));
        view.setUpdatedTime(memory.getUpdatedTime());
        return view;
    }

    private LearningAgentContextDto.ScheduleSummary loadScheduleSummary(Long userId) {
        LearningAgentContextDto.ScheduleSummary summary = new LearningAgentContextDto.ScheduleSummary();
        try {
            User user = userService.getUserById(userId);
            if (user == null || user.getUsername() == null) return summary;
            List<StudentSchedule> schedules = schoolDataSyncService.getStudentSchedule(user.getUsername(), null, null);
            if (schedules == null) return summary;
            List<String> allCourseNames = schedules.stream()
                    .map(StudentSchedule::getCourseName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
            int today = java.time.LocalDate.now().getDayOfWeek().getValue();
            List<String> todayCourses = schedules.stream()
                    .filter(item -> item.getDayOfWeek() != null && item.getDayOfWeek() == today)
                    .map(StudentSchedule::getCourseName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
            summary.setTotalCourses(allCourseNames.size());
            summary.setTodayCourseCount(todayCourses.size());
            summary.setTodayCourseNames(todayCourses);
        } catch (Exception e) {
            log.debug("学习伙伴暂时无法读取课表: userId={}, reason={}", userId, e.getMessage());
        }
        return summary;
    }

    private String buildGreeting(LearningAgentContextDto context) {
        if (!context.isOnboardingCompleted()) {
            return "你好，我是你的英语学习伙伴。先用两分钟告诉我你的目标、水平和兴趣吧。";
        }
        StringBuilder greeting = new StringBuilder("欢迎回来！");
        if (context.getLearnerProfile() != null && context.getLearnerProfile().getHabit() != null
                && context.getLearnerProfile().getHabit().getConsecutiveDays() > 1) {
            greeting.append("你已经连续学习")
                    .append(context.getLearnerProfile().getHabit().getConsecutiveDays())
                    .append("天，保持得很好。");
        } else if (!context.getGoals().isEmpty()) {
            greeting.append("我们继续朝着“").append(context.getGoals().get(0).getTitle()).append("”前进。");
        }
        if (context.getSchedule() != null && context.getSchedule().getTodayCourseCount() > 0) {
            greeting.append("今天有").append(context.getSchedule().getTodayCourseCount())
                    .append("门课，我会帮你把学习安排得更轻一些。");
        }
        return greeting.toString();
    }

    private List<String> buildSuggestedQuestions(LearningAgentContextDto context) {
        List<String> suggestions = new ArrayList<>(Arrays.asList(
                "根据我的目标，今天最值得学什么？",
                "帮我把英语学习安排进这周课表",
                "分析一下我最近的学习状态"));
        if (!context.getGoals().isEmpty() && "ACADEMIC_READING".equals(context.getGoals().get(0).getGoalType())) {
            suggestions.set(0, "给我安排一次英文论文阅读训练");
        }
        return suggestions;
    }
}
