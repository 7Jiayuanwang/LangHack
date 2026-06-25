package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.*;
import com.english.enums.EnrollmentStatus;
import com.english.enums.TaskType;
import com.english.mapper.*;
import com.english.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 活动统计与排名服务实现（简化版）
 */
@Slf4j
@Service
public class CampaignStatsServiceImpl implements CampaignStatsService {

    @Autowired
    private CampaignTaskCompletionMapper completionMapper;

    @Autowired
    private CampaignParticipationMapper participationMapper;

    @Autowired
    private CampaignTaskMapper taskMapper;

    @Autowired
    private LearningRecordsService learningRecordsService;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SchoolStudentMapper schoolStudentMapper;

    @Autowired
    private com.english.mapper.TextMapper textMapper;

    @Override
    public void collectTaskData(Long campaignId, Long taskId) {
        CampaignTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getAutoCollect()) {
            return;
        }

        CampaignActivity campaign = campaignService.getCampaignById(campaignId);
        if (campaign == null) {
            return;
        }

        // P0-06 修复: 分页获取参与者，防止 OOM
        final int PAGE_SIZE = 500;
        int pageNum = 1;
        Page<CampaignParticipation> page;

        do {
            page = new Page<>(pageNum, PAGE_SIZE);
            QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
            wrapper.eq("campaign_id", campaignId);
            wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
            page = participationMapper.selectPage(page, wrapper);

            List<CampaignParticipation> participants = page.getRecords();
            for (CampaignParticipation participant : participants) {
                try {
                    BigDecimal value = collectUserTaskData(campaign, task, participant.getUserId());
                    updateTaskCompletion(campaignId, taskId, participant.getUserId(), value, task.getTargetValue());
                } catch (Exception e) {
                    log.error("采集用户{}任务{}数据失败", participant.getUserId(), taskId, e);
                }
            }

            pageNum++;
        } while (page.hasNext());
    }

    private BigDecimal collectUserTaskData(CampaignActivity campaign, CampaignTask task, Long userId) {
        // 根据任务类型采集数据
        switch (task.getTaskType()) {
            case READING_TIME:
                // 获取学习时长（分钟）
                long minutes = learningRecordsService.getStudyMinutesInRange(
                    userId, campaign.getStartTime(), campaign.getEndTime()
                );
                return BigDecimal.valueOf(minutes);

            case TEXT_UPLOAD:
                // 统计活动期间审核通过的课文数量
                QueryWrapper<com.english.entity.Text> textWrapper = new QueryWrapper<>();
                textWrapper.eq("uploaded_by", userId);
                textWrapper.eq("status", "APPROVED"); // 只统计审核通过的
                textWrapper.eq("deleted", 0);
                textWrapper.ge("created_time", campaign.getStartTime()); // 活动开始后上传
                textWrapper.le("created_time", campaign.getEndTime());   // 活动结束前上传
                long approvedCount = textMapper.selectCount(textWrapper);
                return BigDecimal.valueOf(approvedCount);

            case WORD_LEARNING:
            case FEEDBACK:
            case AI_QA:
                // TODO: 实现其他类型的数据采集
                return BigDecimal.ZERO;

            default:
                return BigDecimal.ZERO;
        }
    }

    private void updateTaskCompletion(Long campaignId, Long taskId, Long userId, BigDecimal value, Integer targetValue) {
        CampaignTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.error("任务{}不存在", taskId);
            return;
        }

        QueryWrapper<CampaignTaskCompletion> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("task_id", taskId);
        wrapper.eq("user_id", userId);

        CampaignTaskCompletion completion = completionMapper.selectOne(wrapper);
        if (completion == null) {
            completion = new CampaignTaskCompletion();
            completion.setCampaignId(campaignId);
            completion.setTaskId(taskId);
            completion.setUserId(userId);
            completion.setCompletionCount(0);
            completion.setEarnedScore(0);
            completion.setTodayCompletionCount(0);
        }

        // 检查是否达标
        boolean reachedTarget = value.compareTo(BigDecimal.valueOf(targetValue)) >= 0;

        if (!reachedTarget) {
            // 未达标，只更新当前值
            completion.setCurrentValue(value);
            completion.setIsCompleted(false);
        } else {
            // 达标，处理积分逻辑
            if (task.getIsRepeatable() != null && task.getIsRepeatable()) {
                // 可重复任务：检查是否可以继续积分
                java.time.LocalDate today = java.time.LocalDate.now();
                java.time.LocalDate lastDate = completion.getLastCompletionDate();

                // 如果日期不同，重置今日计数
                if (lastDate == null || !lastDate.equals(today)) {
                    completion.setTodayCompletionCount(0);
                    completion.setLastCompletionDate(today);
                }

                // 检查单日限制
                if (task.getDailyLimit() != null && completion.getTodayCompletionCount() >= task.getDailyLimit()) {
                    log.info("用户{}任务{}今日已达单日上限{}", userId, taskId, task.getDailyLimit());
                    return;
                }

                // 检查总次数限制
                if (task.getMaxCompletions() != null && completion.getCompletionCount() >= task.getMaxCompletions()) {
                    log.info("用户{}任务{}已达总完成上限{}", userId, taskId, task.getMaxCompletions());
                    return;
                }

                // 可以积分：增加完成次数和积分
                completion.setCompletionCount(completion.getCompletionCount() + 1);
                completion.setTodayCompletionCount(completion.getTodayCompletionCount() + 1);
                completion.setEarnedScore(completion.getEarnedScore() + task.getPoints());
                completion.setCurrentValue(value);
                completion.setIsCompleted(true); // 标记为已完成（可重复）
                completion.setLastCompletionDate(today);

            } else {
                // 一次性任务：只标记完成一次
                if (!completion.getIsCompleted()) {
                    completion.setCompletionCount(1);
                    completion.setEarnedScore(task.getPoints());
                    completion.setIsCompleted(true);
                }
                completion.setCurrentValue(value);
            }
        }

        if (completion.getId() == null) {
            completionMapper.insert(completion);
        } else {
            completionMapper.updateById(completion);
        }

        // 更新参与记录的总得分
        updateParticipationScore(campaignId, userId);
    }

    private void updateParticipationScore(Long campaignId, Long userId) {
        // 统计用户在所有任务上的总得分
        QueryWrapper<CampaignTaskCompletion> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        List<CampaignTaskCompletion> completions = completionMapper.selectList(wrapper);

        int totalScore = 0;
        int completedTaskCount = 0;

        for (CampaignTaskCompletion completion : completions) {
            if (completion.getEarnedScore() != null) {
                totalScore += completion.getEarnedScore();
            }
            // 对于可重复任务，只要完成过一次就算"完成"该任务
            // 对于一次性任务，isCompleted=true 才算完成
            if (completion.getIsCompleted() != null && completion.getIsCompleted()) {
                completedTaskCount++;
            }
        }

        // 更新参与记录
        QueryWrapper<CampaignParticipation> participationWrapper = new QueryWrapper<>();
        participationWrapper.eq("campaign_id", campaignId);
        participationWrapper.eq("user_id", userId);
        CampaignParticipation participation = participationMapper.selectOne(participationWrapper);

        if (participation != null) {
            participation.setCompletedTasks(completedTaskCount);
            participation.setTotalScore(totalScore);

            // 更新完成状态
            String newCompletionStatus = calculateCompletionStatus(campaignId, completedTaskCount, totalScore);
            participation.setCompletionStatus(newCompletionStatus);

            participationMapper.updateById(participation);
        }
    }

    /**
     * 计算用户的完成状态
     *
     * @param campaignId 活动ID
     * @param completedTaskCount 已完成任务数
     * @param totalScore 总得分
     * @return 完成状态：NOT_STARTED（未开始）、IN_PROGRESS（进行中）、COMPLETED（已完成）
     */
    private String calculateCompletionStatus(Long campaignId, int completedTaskCount, int totalScore) {
        // 获取活动完成规则
        CampaignActivity campaign = campaignService.getCampaignById(campaignId);
        if (campaign == null) {
            return "NOT_STARTED";
        }

        // 如果没有任何得分和完成任务，状态为未开始
        if (totalScore == 0 && completedTaskCount == 0) {
            return "NOT_STARTED";
        }

        // 根据完成规则判断是否完成
        boolean isCompleted = false;

        switch (campaign.getCompletionRule()) {
            case ANY:
                // 完成任意任务即算完成
                isCompleted = (completedTaskCount > 0);
                break;

            case ALL:
                // 完成所有必做任务才算完成
                QueryWrapper<CampaignTask> allTaskWrapper = new QueryWrapper<>();
                allTaskWrapper.eq("campaign_id", campaignId);
                allTaskWrapper.eq("is_required", true);
                allTaskWrapper.eq("deleted", 0);
                long requiredTaskCount = taskMapper.selectCount(allTaskWrapper);
                isCompleted = (completedTaskCount >= requiredTaskCount);
                break;

            case THRESHOLD:
                // 完成指定数量的任务才算完成
                Integer threshold = campaign.getCompletionThreshold();
                if (threshold != null) {
                    isCompleted = (completedTaskCount >= threshold);
                }
                break;

            default:
                isCompleted = false;
        }

        return isCompleted ? "COMPLETED" : "IN_PROGRESS";
    }

    @Override
    @CacheEvict(value = {"campaign_ranking", "campaign_overview"}, allEntries = true, cacheManager = "campaignCacheManager")
    public void collectAllCampaignData(Long campaignId) {
        List<CampaignTask> tasks = taskMapper.selectList(
            new QueryWrapper<CampaignTask>().eq("campaign_id", campaignId)
        );
        for (CampaignTask task : tasks) {
            collectTaskData(campaignId, task.getId());
        }
    }

    @Override
    @Cacheable(value = "campaign_ranking", key = "'individual_' + #campaignId + '_' + #current + '_' + #size", cacheManager = "campaignCacheManager")
    public Page<CampaignParticipation> getRankingByIndividual(Long campaignId, int current, int size, Map<String, Object> params) {
        Page<CampaignParticipation> page = new Page<>(current, size);
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        wrapper.orderByDesc("total_score", "completed_tasks");
        Page<CampaignParticipation> result = participationMapper.selectPage(page, wrapper);

        // 批量填充用户信息（姓名、班级、学院）
        enrichRankingWithUserInfo(result.getRecords());

        return result;
    }

    /**
     * 批量填充排名数据的用户信息（姓名、班级、学院）
     * P0-04 修复: 使用批量查询替代 N+1 查询
     */
    private void enrichRankingWithUserInfo(List<CampaignParticipation> participations) {
        if (participations == null || participations.isEmpty()) {
            return;
        }

        // 批量查询用户信息
        List<Long> userIds = participations.stream()
            .map(CampaignParticipation::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        // P0-04 修复: 一次性批量查询所有用户
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> usersById = users.stream()
            .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        // P0-04 修复: 批量查询所有学校学生信息
        List<Long> schoolStudentIds = users.stream()
            .map(User::getSchoolStudentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        List<SchoolStudent> schoolStudents = schoolStudentIds.isEmpty()
            ? java.util.Collections.emptyList()
            : schoolStudentMapper.selectBatchIds(schoolStudentIds);

        Map<Long, SchoolStudent> studentsBySchoolId = schoolStudents.stream()
            .collect(java.util.stream.Collectors.toMap(SchoolStudent::getId, s -> s));

        Map<String, SchoolStudent> studentsByStudentId = schoolStudents.stream()
            .filter(s -> s.getStudentId() != null && !s.getStudentId().isBlank())
            .collect(java.util.stream.Collectors.toMap(SchoolStudent::getStudentId, s -> s, (s1, s2) -> s1));

        // 填充每条记录的用户信息
        for (CampaignParticipation participation : participations) {
            User user = usersById.get(participation.getUserId());
            if (user == null) {
                continue;
            }

            // 获取学校数据（优先用 school_student_id，其次用学号）
            SchoolStudent schoolStudent = null;
            if (user.getSchoolStudentId() != null) {
                schoolStudent = studentsBySchoolId.get(user.getSchoolStudentId());
            }
            if (schoolStudent == null && user.getUsername() != null && !user.getUsername().isBlank()) {
                schoolStudent = studentsByStudentId.get(user.getUsername());
            }

            // 设置用户姓名（优先真实姓名，其次昵称，最后用户名）
            String userName = user.getRealName() != null && !user.getRealName().isBlank()
                ? user.getRealName()
                : user.getNickname() != null && !user.getNickname().isBlank()
                    ? user.getNickname()
                    : user.getUsername();

            participation.setUserName(userName);
            participation.setClassName(schoolStudent != null ? schoolStudent.getClassName() : null);
            participation.setCollegeName(schoolStudent != null ? schoolStudent.getCollegeName() : null);
        }
    }

    @Override
    public List<Map<String, Object>> getRankingByClass(Long campaignId) {
        // 获取所有已报名参与者
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        List<CampaignParticipation> participations = participationMapper.selectList(wrapper);

        if (participations.isEmpty()) {
            return new ArrayList<>();
        }

        // P0-05 修复: 批量查询用户和学校数据，避免 N+1 查询
        List<Long> userIds = participations.stream()
            .map(CampaignParticipation::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> usersById = users.stream()
            .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        List<Long> schoolStudentIds = users.stream()
            .map(User::getSchoolStudentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        List<SchoolStudent> schoolStudents = schoolStudentIds.isEmpty()
            ? java.util.Collections.emptyList()
            : schoolStudentMapper.selectBatchIds(schoolStudentIds);

        Map<Long, SchoolStudent> studentsBySchoolId = schoolStudents.stream()
            .collect(java.util.stream.Collectors.toMap(SchoolStudent::getId, s -> s));

        Map<String, SchoolStudent> studentsByStudentId = schoolStudents.stream()
            .filter(s -> s.getStudentId() != null && !s.getStudentId().isBlank())
            .collect(java.util.stream.Collectors.toMap(SchoolStudent::getStudentId, s -> s, (s1, s2) -> s1));

        // 按班级分组统计
        Map<String, ClassStats> statsByClass = new HashMap<>();
        for (CampaignParticipation participation : participations) {
            User user = usersById.get(participation.getUserId());
            if (user == null) {
                continue;
            }

            // 获取学校数据（优先用 school_student_id，其次用学号）
            SchoolStudent schoolStudent = null;
            if (user.getSchoolStudentId() != null) {
                schoolStudent = studentsBySchoolId.get(user.getSchoolStudentId());
            }
            if (schoolStudent == null && user.getUsername() != null && !user.getUsername().isBlank()) {
                schoolStudent = studentsByStudentId.get(user.getUsername());
            }

            if (schoolStudent == null || schoolStudent.getClassName() == null) {
                continue;
            }

            final String className = schoolStudent.getClassName();
            final String collegeName = schoolStudent.getCollegeName();
            ClassStats stats = statsByClass.computeIfAbsent(className, k -> new ClassStats(
                className,
                collegeName
            ));

            stats.totalScore += (participation.getTotalScore() != null ? participation.getTotalScore() : 0);
            stats.participantCount++;
            if (participation.getCompletionStatus() != null && "COMPLETED".equals(participation.getCompletionStatus())) {
                stats.completedCount++;
            }
        }

        // 转换为列表并排序
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassStats stats : statsByClass.values()) {
            Map<String, Object> item = new HashMap<>();
            item.put("className", stats.className);
            item.put("collegeName", stats.collegeName);
            item.put("totalScore", stats.totalScore);
            item.put("participantCount", stats.participantCount);
            item.put("avgScore", stats.participantCount > 0
                ? Math.round((double) stats.totalScore / stats.participantCount * 10) / 10.0
                : 0.0);
            item.put("completionRate", stats.participantCount > 0
                ? String.format("%.1f%%", (double) stats.completedCount / stats.participantCount * 100)
                : "0.0%");
            result.add(item);
        }

        // 按总分降序排序
        result.sort((a, b) -> {
            Integer scoreA = (Integer) a.get("totalScore");
            Integer scoreB = (Integer) b.get("totalScore");
            return scoreB.compareTo(scoreA);
        });

        return result;
    }

    @Override
    public List<Map<String, Object>> getRankingByCollege(Long campaignId) {
        // 获取所有已报名参与者
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        List<CampaignParticipation> participations = participationMapper.selectList(wrapper);

        if (participations.isEmpty()) {
            return new ArrayList<>();
        }

        // P0-05 修复: 批量查询用户和学校数据，避免 N+1 查询
        List<Long> userIds = participations.stream()
            .map(CampaignParticipation::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> usersById = users.stream()
            .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        List<Long> schoolStudentIds = users.stream()
            .map(User::getSchoolStudentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        List<SchoolStudent> schoolStudents = schoolStudentIds.isEmpty()
            ? java.util.Collections.emptyList()
            : schoolStudentMapper.selectBatchIds(schoolStudentIds);

        Map<Long, SchoolStudent> studentsBySchoolId = schoolStudents.stream()
            .collect(java.util.stream.Collectors.toMap(SchoolStudent::getId, s -> s));

        Map<String, SchoolStudent> studentsByStudentId = schoolStudents.stream()
            .filter(s -> s.getStudentId() != null && !s.getStudentId().isBlank())
            .collect(java.util.stream.Collectors.toMap(SchoolStudent::getStudentId, s -> s, (s1, s2) -> s1));

        // 按学院分组统计
        Map<String, CollegeStats> statsByCollege = new HashMap<>();
        for (CampaignParticipation participation : participations) {
            User user = usersById.get(participation.getUserId());
            if (user == null) {
                continue;
            }

            // 获取学校数据（优先用 school_student_id，其次用学号）
            SchoolStudent schoolStudent = null;
            if (user.getSchoolStudentId() != null) {
                schoolStudent = studentsBySchoolId.get(user.getSchoolStudentId());
            }
            if (schoolStudent == null && user.getUsername() != null && !user.getUsername().isBlank()) {
                schoolStudent = studentsByStudentId.get(user.getUsername());
            }

            if (schoolStudent == null || schoolStudent.getCollegeName() == null) {
                continue;
            }

            final String collegeName = schoolStudent.getCollegeName();
            CollegeStats stats = statsByCollege.computeIfAbsent(collegeName, k -> new CollegeStats(
                collegeName
            ));

            stats.totalScore += (participation.getTotalScore() != null ? participation.getTotalScore() : 0);
            stats.participantCount++;
            if (participation.getCompletionStatus() != null && "COMPLETED".equals(participation.getCompletionStatus())) {
                stats.completedCount++;
            }
        }

        // 转换为列表并排序
        List<Map<String, Object>> result = new ArrayList<>();
        for (CollegeStats stats : statsByCollege.values()) {
            Map<String, Object> item = new HashMap<>();
            item.put("collegeName", stats.collegeName);
            item.put("totalScore", stats.totalScore);
            item.put("participantCount", stats.participantCount);
            item.put("avgScore", stats.participantCount > 0
                ? Math.round((double) stats.totalScore / stats.participantCount * 10) / 10.0
                : 0.0);
            item.put("completionRate", stats.participantCount > 0
                ? String.format("%.1f%%", (double) stats.completedCount / stats.participantCount * 100)
                : "0.0%");
            result.add(item);
        }

        // 按总分降序排序
        result.sort((a, b) -> {
            Integer scoreA = (Integer) a.get("totalScore");
            Integer scoreB = (Integer) b.get("totalScore");
            return scoreB.compareTo(scoreA);
        });

        return result;
    }

    // 内部统计类
    private static class ClassStats {
        String className;
        String collegeName;
        int totalScore = 0;
        int participantCount = 0;
        int completedCount = 0;

        ClassStats(String className, String collegeName) {
            this.className = className;
            this.collegeName = collegeName;
        }
    }

    private static class CollegeStats {
        String collegeName;
        int totalScore = 0;
        int participantCount = 0;
        int completedCount = 0;

        CollegeStats(String collegeName) {
            this.collegeName = collegeName;
        }
    }

    @Override
    public Map<String, Object> getMyRanking(Long campaignId, Long userId) {
        Map<String, Object> result = new HashMap<>();

        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        CampaignParticipation myParticipation = participationMapper.selectOne(wrapper);

        if (myParticipation == null || myParticipation.getEnrollmentStatus() != EnrollmentStatus.ENROLLED) {
            result.put("enrolled", false);
            result.put("myRank", null);
            result.put("totalParticipants", 0);
            result.put("aroundMe", new ArrayList<>());
            result.put("enrollmentStatus", myParticipation != null ? myParticipation.getEnrollmentStatus() : null);
            return result;
        }

        QueryWrapper<CampaignParticipation> rankWrapper = new QueryWrapper<>();
        rankWrapper.eq("campaign_id", campaignId);
        rankWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        rankWrapper.gt("total_score", myParticipation.getTotalScore());
        long myRank = participationMapper.selectCount(rankWrapper) + 1;

        QueryWrapper<CampaignParticipation> totalWrapper = new QueryWrapper<>();
        totalWrapper.eq("campaign_id", campaignId);
        totalWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        long totalParticipants = participationMapper.selectCount(totalWrapper);

        QueryWrapper<CampaignParticipation> aroundWrapper = new QueryWrapper<>();
        aroundWrapper.eq("campaign_id", campaignId);
        aroundWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        aroundWrapper.orderByDesc("total_score", "completed_tasks", "id");
        List<CampaignParticipation> allRanked = participationMapper.selectList(aroundWrapper);

        Map<Long, Long> ranksByUserId = new HashMap<>();
        long currentRank = 0;
        Integer previousScore = null;
        for (int i = 0; i < allRanked.size(); i++) {
            CampaignParticipation participation = allRanked.get(i);
            Integer totalScore = participation.getTotalScore();
            if (previousScore == null || !Objects.equals(previousScore, totalScore)) {
                currentRank = i + 1L;
                previousScore = totalScore;
            }
            ranksByUserId.put(participation.getUserId(), currentRank);
        }

        Map<Long, User> usersById = new HashMap<>();
        Map<Long, SchoolStudent> studentsBySchoolId = new HashMap<>();
        Map<String, SchoolStudent> studentsByStudentId = new HashMap<>();
        for (CampaignParticipation participation : allRanked) {
            Long participantUserId = participation.getUserId();
            if (participantUserId == null || usersById.containsKey(participantUserId)) {
                continue;
            }
            User user = userService.getUserById(participantUserId);
            if (user == null) {
                continue;
            }
            usersById.put(participantUserId, user);
            if (user.getSchoolStudentId() != null) {
                SchoolStudent schoolStudent = schoolStudentMapper.selectById(user.getSchoolStudentId());
                if (schoolStudent != null) {
                    studentsBySchoolId.put(user.getSchoolStudentId(), schoolStudent);
                }
            }
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                SchoolStudent schoolStudent = schoolStudentMapper.selectByStudentId(user.getUsername());
                if (schoolStudent != null) {
                    studentsByStudentId.put(user.getUsername(), schoolStudent);
                }
            }
        }

        List<Map<String, Object>> aroundMe = new ArrayList<>();
        int myIndex = -1;
        for (int i = 0; i < allRanked.size(); i++) {
            if (allRanked.get(i).getUserId().equals(userId)) {
                myIndex = i;
                break;
            }
        }
        if (myIndex >= 0) {
            int from = Math.max(0, myIndex - 2);
            int to = Math.min(allRanked.size() - 1, myIndex + 2);
            for (int i = from; i <= to; i++) {
                CampaignParticipation p = allRanked.get(i);
                User user = usersById.get(p.getUserId());
                SchoolStudent schoolStudent = null;
                if (user != null && user.getSchoolStudentId() != null) {
                    schoolStudent = studentsBySchoolId.get(user.getSchoolStudentId());
                }
                if (schoolStudent == null && user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
                    schoolStudent = studentsByStudentId.get(user.getUsername());
                }

                String userName = user != null && user.getRealName() != null && !user.getRealName().isBlank()
                    ? user.getRealName()
                    : user != null && user.getNickname() != null && !user.getNickname().isBlank()
                        ? user.getNickname()
                        : user != null ? user.getUsername() : null;

                Map<String, Object> item = new HashMap<>();
                item.put("rank", ranksByUserId.getOrDefault(p.getUserId(), (long) (i + 1)));
                item.put("userId", p.getUserId());
                item.put("userName", userName);
                item.put("className", schoolStudent != null ? schoolStudent.getClassName() : null);
                item.put("totalScore", p.getTotalScore());
                item.put("isMe", p.getUserId().equals(userId));
                aroundMe.add(item);
            }
        }

        result.put("enrolled", true);
        result.put("myRank", myRank);
        result.put("totalParticipants", totalParticipants);
        result.put("totalScore", myParticipation.getTotalScore());
        result.put("completedTasks", myParticipation.getCompletedTasks());
        result.put("enrollmentStatus", myParticipation.getEnrollmentStatus());
        result.put("aroundMe", aroundMe);

        return result;
    }

    @Override
    public List<CampaignParticipation> getRankingAroundMe(Long campaignId, Long userId, int range) {
        // TODO: 实现周围排名
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> getParticipationStats(Long campaignId) {
        Map<String, Object> stats = new HashMap<>();

        // 查询总参与人数（所有状态）
        QueryWrapper<CampaignParticipation> totalWrapper = new QueryWrapper<>();
        totalWrapper.eq("campaign_id", campaignId);
        long totalParticipants = participationMapper.selectCount(totalWrapper);

        // 查询已报名人数（ENROLLED状态）
        QueryWrapper<CampaignParticipation> enrolledWrapper = new QueryWrapper<>();
        enrolledWrapper.eq("campaign_id", campaignId);
        enrolledWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        long enrolledCount = participationMapper.selectCount(enrolledWrapper);

        // 查询已完成人数（COMPLETED状态）
        QueryWrapper<CampaignParticipation> completedWrapper = new QueryWrapper<>();
        completedWrapper.eq("campaign_id", campaignId);
        completedWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        completedWrapper.eq("completion_status", "COMPLETED");
        long completedParticipants = participationMapper.selectCount(completedWrapper);

        // 查询进行中人数（IN_PROGRESS状态）
        QueryWrapper<CampaignParticipation> inProgressWrapper = new QueryWrapper<>();
        inProgressWrapper.eq("campaign_id", campaignId);
        inProgressWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        inProgressWrapper.eq("completion_status", "IN_PROGRESS");
        long inProgressParticipants = participationMapper.selectCount(inProgressWrapper);

        // 计算完成率
        String completionRate = enrolledCount > 0
            ? String.format("%.1f%%", (double) completedParticipants / enrolledCount * 100)
            : "0%";

        stats.put("totalParticipants", totalParticipants);
        stats.put("enrolledCount", enrolledCount);
        stats.put("completedParticipants", completedParticipants);
        stats.put("inProgressParticipants", inProgressParticipants);
        stats.put("completionRate", completionRate);

        return stats;
    }

    @Override
    @Cacheable(value = "campaign_overview", key = "#campaignId", cacheManager = "campaignCacheManager")
    public Map<String, Object> getCampaignOverview(Long campaignId) {
        Map<String, Object> overview = new HashMap<>();
        overview.putAll(getParticipationStats(campaignId));

        // 任务统计
        long taskCount = taskMapper.selectCount(
            new QueryWrapper<CampaignTask>().eq("campaign_id", campaignId)
        );
        overview.put("taskCount", taskCount);

        return overview;
    }

    @Override
    public Map<String, Object> getMyProgress(Long campaignId, Long userId) {
        Map<String, Object> result = new HashMap<>();

        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        CampaignParticipation myParticipation = participationMapper.selectOne(wrapper);

        if (myParticipation == null || myParticipation.getEnrollmentStatus() != EnrollmentStatus.ENROLLED) {
            result.put("enrolled", false);
            result.put("enrollmentStatus", myParticipation != null ? myParticipation.getEnrollmentStatus() : null);
            return result;
        }

        QueryWrapper<CampaignParticipation> rankWrapper = new QueryWrapper<>();
        rankWrapper.eq("campaign_id", campaignId);
        rankWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        rankWrapper.gt("total_score", myParticipation.getTotalScore());
        long myRank = participationMapper.selectCount(rankWrapper) + 1;

        result.put("enrolled", true);
        result.put("totalScore", myParticipation.getTotalScore());
        result.put("completedTasks", myParticipation.getCompletedTasks());
        result.put("completionStatus", myParticipation.getCompletionStatus());
        result.put("myRank", myRank);
        result.put("enrollmentStatus", myParticipation.getEnrollmentStatus());

        return result;
    }

    @Override
    public List<Map<String, Object>> getMyTaskProgress(Long campaignId, Long userId) {
        List<Map<String, Object>> progress = new ArrayList<>();

        // 获取所有任务
        List<CampaignTask> tasks = taskMapper.selectList(
            new QueryWrapper<CampaignTask>().eq("campaign_id", campaignId)
        );

        for (CampaignTask task : tasks) {
            QueryWrapper<CampaignTaskCompletion> wrapper = new QueryWrapper<>();
            wrapper.eq("campaign_id", campaignId);
            wrapper.eq("task_id", task.getId());
            wrapper.eq("user_id", userId);
            CampaignTaskCompletion completion = completionMapper.selectOne(wrapper);

            Map<String, Object> taskProgress = new HashMap<>();
            taskProgress.put("taskId", task.getId());
            taskProgress.put("taskName", task.getTaskName());
            taskProgress.put("taskType", task.getTaskType());
            taskProgress.put("taskUnit", task.getTaskUnit());
            taskProgress.put("targetValue", task.getTargetValue());
            taskProgress.put("points", task.getPoints());
            taskProgress.put("isRequired", task.getIsRequired());
            taskProgress.put("currentValue", completion != null ? completion.getCurrentValue() : BigDecimal.ZERO);
            taskProgress.put("isCompleted", completion != null && completion.getIsCompleted());
            taskProgress.put("completionCount", completion != null ? completion.getCompletionCount() : 0);

            progress.add(taskProgress);
        }

        return progress;
    }
}
