package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.*;
import com.english.enums.CampaignStatus;
import com.english.enums.EnrollmentStatus;
import com.english.enums.TaskType;
import com.english.mapper.*;
import com.english.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

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

    // 使用 @Lazy 注解打破循环依赖
    @Autowired
    @org.springframework.context.annotation.Lazy
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

    @Autowired
    private com.english.mapper.VocabularyBookMapper vocabularyBookMapper;

    @Autowired
    private com.english.mapper.AiFeedbackMapper aiFeedbackMapper;

    @Autowired
    private com.english.mapper.AiCallLogMapper aiCallLogMapper;

    @Autowired
    private CampaignActivityMapper campaignActivityMapper;

    @Override
    @Async("campaignCollectionExecutor")
    public void collectUserTasksByTypeAsync(Long userId, TaskType taskType) {
        try {
            // 1. Query user's enrolled campaigns
            List<CampaignParticipation> participations = participationMapper.selectList(
                new QueryWrapper<CampaignParticipation>()
                    .eq("user_id", userId)
                    .eq("enrollment_status", EnrollmentStatus.ENROLLED)
            );

            if (participations.isEmpty()) {
                return; // Not enrolled in any campaign
            }

            // 2. Filter campaigns by ACTIVE status
            List<Long> campaignIds = participations.stream()
                .map(CampaignParticipation::getCampaignId)
                .distinct()
                .collect(Collectors.toList());

            List<CampaignActivity> activeCampaigns = campaignActivityMapper.selectBatchIds(campaignIds)
                .stream()
                .filter(c -> c.getStatus() == CampaignStatus.ACTIVE && c.getDeleted() == 0)
                .collect(Collectors.toList());

            if (activeCampaigns.isEmpty()) {
                return; // No active campaigns
            }

            // 3. For each active campaign, collect matching tasks
            for (CampaignActivity campaign : activeCampaigns) {
                List<CampaignTask> tasks = taskMapper.selectList(
                    new QueryWrapper<CampaignTask>()
                        .eq("campaign_id", campaign.getId())
                        .eq("task_type", taskType)
                        .eq("auto_collect", true)
                        .orderByAsc("target_value")  // 按目标值升序，先完成低标准任务
                );

                if (tasks.isEmpty()) continue;

                // 采集该类型在活动时间窗口内的总进度值（只采集一次，各任务共享）
                BigDecimal totalValue = BigDecimal.ZERO;
                try {
                    totalValue = collectUserTaskData(campaign, tasks.get(0), userId);
                } catch (Exception e) {
                    log.error("采集数据失败: userId={}, campaignId={}, taskType={}", userId, campaign.getId(), taskType, e);
                    continue;
                }

                // 分层分配：totalValue 从低标准任务依次向高标准任务分配，不重复计算
                distributeValueToTieredTasks(campaign.getId(), tasks, userId, totalValue);
            }

            log.debug("事件驱动采集完成: userId={}, taskType={}, 活跃活动数={}", userId, taskType, activeCampaigns.size());

        } catch (Exception e) {
            log.error("事件驱动采集任务失败: userId={}, taskType={}", userId, taskType, e);
        }
    }

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
                // 获取阅读时长（分钟）— 仅统计 TEXT_VIEW 记录，避免把单词学习/复习时长计入阅读任务
                long minutes = learningRecordsService.getReadingMinutesInRange(
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
                // 统计活动期间学习/复习的单词数量
                // 计入：活动期间新增的单词（added_time 落在窗口内）
                //      或活动期间复习过的单词（last_reviewed 落在窗口内）
                QueryWrapper<com.english.entity.VocabularyBook> vocabWrapper = new QueryWrapper<>();
                vocabWrapper.eq("student_id", userId);
                vocabWrapper.and(w -> w
                        .between("added_time", campaign.getStartTime(), campaign.getEndTime())
                        .or()
                        .between("last_reviewed", campaign.getStartTime(), campaign.getEndTime())
                );
                long wordCount = vocabularyBookMapper.selectCount(vocabWrapper);
                return BigDecimal.valueOf(wordCount);

            case FEEDBACK:
                // 统计活动期间提交的 AI 反馈数量
                QueryWrapper<com.english.entity.AiFeedback> feedbackWrapper = new QueryWrapper<>();
                feedbackWrapper.eq("user_id", userId);
                feedbackWrapper.between("created_time", campaign.getStartTime(), campaign.getEndTime());
                long feedbackCount = aiFeedbackMapper.selectCount(feedbackWrapper);
                return BigDecimal.valueOf(feedbackCount);

            case AI_QA:
                // 统计活动期间 AI 问答成功调用次数
                // ai_call_log.triggered_by 存储的是用户名（username），不是 userId
                User qaUser = userMapper.selectById(userId);
                if (qaUser == null || qaUser.getUsername() == null) {
                    return BigDecimal.ZERO;
                }
                QueryWrapper<com.english.entity.AiCallLog> callLogWrapper = new QueryWrapper<>();
                // 同时匹配 "QA"（AiQaController 主路径）和 "HIAGENT"（HiAgentServiceImpl 回退路径），
                // 避免漏算 aiProviderRuntimeService 不可用时的回退调用；
                // 限定 method_name='chatQA' 排除 HiAgent 的翻译/ASR 等非问答调用。
                callLogWrapper.in("service_name", "QA", "HIAGENT");
                callLogWrapper.eq("method_name", "chatQA");
                callLogWrapper.eq("triggered_by", qaUser.getUsername());
                callLogWrapper.eq("success", true);
                callLogWrapper.between("created_time", campaign.getStartTime(), campaign.getEndTime());
                long qaCount = aiCallLogMapper.selectCount(callLogWrapper);
                return BigDecimal.valueOf(qaCount);

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
            completion.setLastCountedValue(BigDecimal.ZERO);
        }
        // 兜底：历史数据可能没有 lastCountedValue
        if (completion.getLastCountedValue() == null) {
            completion.setLastCountedValue(BigDecimal.ZERO);
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
                // 可重复任务：基于增量计分，避免同一进度被重复加分
                // 新增完成次数 = (currentValue - lastCountedValue) / targetValue（向下取整）
                BigDecimal targetDecimal = BigDecimal.valueOf(targetValue);
                BigDecimal progressDelta = value.subtract(completion.getLastCountedValue());
                int newCompletions = progressDelta.divide(targetDecimal, 0, java.math.RoundingMode.DOWN).intValue();

                if (newCompletions <= 0) {
                    // 没有新增进度，只更新当前值，不加分
                    completion.setCurrentValue(value);
                    completion.setIsCompleted(true);
                } else {
                    java.time.LocalDate today = java.time.LocalDate.now();
                    java.time.LocalDate lastDate = completion.getLastCompletionDate();

                    // 如果日期不同，重置今日计数
                    if (lastDate == null || !lastDate.equals(today)) {
                        completion.setTodayCompletionCount(0);
                        completion.setLastCompletionDate(today);
                    }

                    // 按单日限制裁剪本次可加次数
                    int allowedByDaily = newCompletions;
                    if (task.getDailyLimit() != null) {
                        int remainingToday = task.getDailyLimit() - completion.getTodayCompletionCount();
                        allowedByDaily = Math.max(0, remainingToday);
                    }

                    // 按总次数限制裁剪本次可加次数
                    int allowedByMax = newCompletions;
                    if (task.getMaxCompletions() != null) {
                        int remainingTotal = task.getMaxCompletions() - completion.getCompletionCount();
                        allowedByMax = Math.max(0, remainingTotal);
                    }

                    int actualNew = Math.min(newCompletions, Math.min(allowedByDaily, allowedByMax));

                    if (actualNew <= 0) {
                        log.info("用户{}任务{}已达上限（单日/总数），跳过本次加分", userId, taskId);
                        // 仍更新当前值，但不前移 lastCountedValue，避免上限解除后漏算
                        completion.setCurrentValue(value);
                        completion.setIsCompleted(true);
                    } else {
                        // 加分：增加完成次数和积分
                        completion.setCompletionCount(completion.getCompletionCount() + actualNew);
                        completion.setTodayCompletionCount(completion.getTodayCompletionCount() + actualNew);
                        completion.setEarnedScore(completion.getEarnedScore() + actualNew * task.getPoints());
                        // 前移 lastCountedValue 到本次计分覆盖的进度点
                        BigDecimal countedValue = completion.getLastCountedValue()
                                .add(targetDecimal.multiply(BigDecimal.valueOf(actualNew)));
                        completion.setLastCountedValue(countedValue);
                        completion.setCurrentValue(value);
                        completion.setIsCompleted(true);
                        completion.setLastCompletionDate(today);
                    }
                }

            } else {
                // 一次性任务：只标记完成一次
                if (!completion.getIsCompleted()) {
                    completion.setCompletionCount(1);
                    completion.setEarnedScore(task.getPoints());
                    completion.setIsCompleted(true);
                    // 一次性任务也前移 lastCountedValue，保持语义一致
                    completion.setLastCountedValue(value);
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
     * 完成判定优先级：
     * 1. 必做任务（isRequired=true）：所有必做任务完成才算 COMPLETED
     * 2. 无必做任务时，按 completionRule（ANY/ALL/THRESHOLD）判断
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

        // 查询活动所有未删除任务
        QueryWrapper<CampaignTask> taskWrapper = new QueryWrapper<>();
        taskWrapper.eq("campaign_id", campaignId);
        taskWrapper.eq("deleted", 0);
        List<CampaignTask> allTasks = taskMapper.selectList(taskWrapper);

        // 优先基于必做任务判断：所有必做任务完成才算完成
        List<CampaignTask> requiredTasks = allTasks.stream()
            .filter(t -> Boolean.TRUE.equals(t.getIsRequired()))
            .collect(Collectors.toList());

        if (!requiredTasks.isEmpty()) {
            // 获取用户已完成的任务ID集合
            QueryWrapper<CampaignTaskCompletion> compWrapper = new QueryWrapper<>();
            compWrapper.eq("campaign_id", campaignId);
            // 注意：此方法在 updateParticipationScore 内被调用，无法拿到 userId
            // 改为通过 completedTaskCount 与必做任务数比较
            // 必做任务全部完成 = completedTaskCount >= requiredTasks.size()
            // （completedTaskCount 包含必做 + 可选，所以只要 >= 必做数即可）
            boolean isCompleted = completedTaskCount >= requiredTasks.size();
            return isCompleted ? "COMPLETED" : "IN_PROGRESS";
        }

        // 无必做任务时，按 completionRule 判断
        boolean isCompleted = false;

        switch (campaign.getCompletionRule()) {
            case ANY:
                // 完成任意任务即算完成
                isCompleted = (completedTaskCount > 0);
                break;

            case ALL:
                // 完成所有任务才算完成
                long totalTaskCount = allTasks.size();
                isCompleted = (totalTaskCount > 0 && completedTaskCount >= totalTaskCount);
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
        CampaignActivity campaign = campaignService.getCampaignById(campaignId);
        if (campaign == null) return;

        List<CampaignTask> allTasks = taskMapper.selectList(
            new QueryWrapper<CampaignTask>().eq("campaign_id", campaignId).eq("auto_collect", true)
        );
        if (allTasks.isEmpty()) return;

        // 按类型分组，同类型按 targetValue 升序，支持多任务分层完成
        Map<TaskType, List<CampaignTask>> tasksByType = allTasks.stream()
            .collect(java.util.stream.Collectors.groupingBy(CampaignTask::getTaskType));

        final int PAGE_SIZE = 500;
        int pageNum = 1;
        Page<CampaignParticipation> page;

        do {
            page = new Page<>(pageNum, PAGE_SIZE);
            QueryWrapper<CampaignParticipation> pWrapper = new QueryWrapper<>();
            pWrapper.eq("campaign_id", campaignId);
            pWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
            page = participationMapper.selectPage(page, pWrapper);

            for (CampaignParticipation participant : page.getRecords()) {
                Long userId = participant.getUserId();
                for (Map.Entry<TaskType, List<CampaignTask>> entry : tasksByType.entrySet()) {
                    List<CampaignTask> typeTasks = entry.getValue().stream()
                        .sorted(java.util.Comparator.comparing(CampaignTask::getTargetValue))
                        .collect(Collectors.toList());
                    try {
                        BigDecimal totalValue = collectUserTaskData(campaign, typeTasks.get(0), userId);
                        distributeValueToTieredTasks(campaignId, typeTasks, userId, totalValue);
                    } catch (Exception e) {
                        log.error("全量采集失败: userId={}, campaignId={}, taskType={}",
                            userId, campaignId, entry.getKey(), e);
                    }
                }
            }
            pageNum++;
        } while (page.hasNext());
    }

    /**
     * 将总进度值按目标值升序分层分配给同类型的多个任务。
     *
     * 规则（以阅读时长为例，有两个任务 30分钟/10分 和 60分钟/20分）：
     * - totalValue=45 → 30分钟任务完成(+10分)，60分钟任务进行中(0分)
     * - totalValue=70 → 30分钟任务完成(+10分)，60分钟任务完成(+20分)
     * - updateTaskCompletion 内部用 lastCountedValue 防止同一进度被重复计分
     */
    private void distributeValueToTieredTasks(Long campaignId, List<CampaignTask> tasks,
                                               Long userId, BigDecimal totalValue) {
        for (CampaignTask task : tasks) {
            try {
                updateTaskCompletion(campaignId, task.getId(), userId, totalValue, task.getTargetValue());
            } catch (Exception e) {
                log.error("分层任务更新失败: campaignId={}, taskId={}, userId={}", campaignId, task.getId(), userId, e);
            }
        }
    }

    @Override
    @Cacheable(value = "campaign_ranking", key = "'individual_' + #campaignId + '_' + #current + '_' + #size + '_' + (#params?.get('className') ?: '') + '_' + (#params?.get('collegeName') ?: '')", cacheManager = "campaignCacheManager")
    public Page<CampaignParticipation> getRankingByIndividual(Long campaignId, int current, int size, Map<String, Object> params) {
        Page<CampaignParticipation> page = new Page<>(current, size);

        String className = params.containsKey("className") ? (String) params.get("className") : null;
        String collegeName = params.containsKey("collegeName") ? (String) params.get("collegeName") : null;

        // 如果有筛选条件，使用自定义 SQL；否则使用标准查询
        if (className != null || collegeName != null) {
            List<CampaignParticipation> records = participationMapper.selectRankingWithFilters(
                campaignId, className, collegeName, page
            );
            page.setRecords(records);

            // 需要手动计算 total（因为 MyBatis Plus 分页插件不会自动处理自定义 SQL 的 count）
            // 这里简化处理：如果有筛选，total 就是返回记录数（实际应该再执行一次 count 查询）
            page.setTotal(records.size());
        } else {
            QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
            wrapper.eq("campaign_id", campaignId);
            wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
            wrapper.orderByDesc("total_score", "completed_tasks");
            page = participationMapper.selectPage(page, wrapper);
        }

        // 批量填充用户信息（姓名、班级、学院）
        enrichRankingWithUserInfo(page.getRecords());

        return page;
    }

    /**
     * 批量填充排名数据的用户信息（姓名、班级、学院）
     * P0-04 修复: 使用批量查询替代 N+1 查询
     */
    private void enrichRankingWithUserInfo(List<CampaignParticipation> participations) {
        if (participations == null || participations.isEmpty()) {
            return;
        }

        List<User> users = loadActiveUsersByIds(participations.stream()
            .map(CampaignParticipation::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .toList());
        Map<Long, User> usersById = users.stream()
            .collect(Collectors.toMap(User::getId, u -> u));

        SchoolStudentLookup lookup = buildSchoolStudentLookup(users);

        for (CampaignParticipation participation : participations) {
            User user = usersById.get(participation.getUserId());
            if (user == null) {
                continue;
            }

            SchoolStudent schoolStudent = resolveSchoolStudent(user, lookup);
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

    private record SchoolStudentLookup(
        Map<Long, SchoolStudent> bySchoolId,
        Map<String, SchoolStudent> byStudentId
    ) {}

    private List<User> loadActiveUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        return userMapper.selectBatchIds(userIds).stream()
            .filter(Objects::nonNull)
            .filter(u -> u.getDeleted() == null || u.getDeleted() == 0)
            .toList();
    }

    private SchoolStudentLookup buildSchoolStudentLookup(List<User> users) {
        if (users == null || users.isEmpty()) {
            return new SchoolStudentLookup(Collections.emptyMap(), Collections.emptyMap());
        }

        List<Long> schoolStudentIds = users.stream()
            .map(User::getSchoolStudentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Map<Long, SchoolStudent> studentsBySchoolId = schoolStudentIds.isEmpty()
            ? Collections.emptyMap()
            : schoolStudentMapper.selectBatchIds(schoolStudentIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SchoolStudent::getId, s -> s));

        List<String> usernames = users.stream()
            .map(User::getUsername)
            .filter(username -> username != null && !username.isBlank())
            .distinct()
            .toList();

        Map<String, SchoolStudent> studentsByStudentId = new HashMap<>();
        if (!usernames.isEmpty()) {
            QueryWrapper<SchoolStudent> byStudentIdWrapper = new QueryWrapper<>();
            byStudentIdWrapper.in("student_id", usernames);
            schoolStudentMapper.selectList(byStudentIdWrapper).forEach(ss -> {
                if (ss.getStudentId() != null && !ss.getStudentId().isBlank()) {
                    studentsByStudentId.putIfAbsent(ss.getStudentId(), ss);
                }
            });
        }

        return new SchoolStudentLookup(studentsBySchoolId, studentsByStudentId);
    }

    private SchoolStudent resolveSchoolStudent(User user, SchoolStudentLookup lookup) {
        if (user == null || lookup == null) {
            return null;
        }

        SchoolStudent schoolStudent = null;
        if (user.getSchoolStudentId() != null) {
            schoolStudent = lookup.bySchoolId().get(user.getSchoolStudentId());
        }
        if (schoolStudent == null && user.getUsername() != null && !user.getUsername().isBlank()) {
            schoolStudent = lookup.byStudentId().get(user.getUsername());
        }
        return schoolStudent;
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

        List<User> users = loadActiveUsersByIds(userIds);
        Map<Long, User> usersById = users.stream()
            .collect(Collectors.toMap(User::getId, u -> u));

        SchoolStudentLookup lookup = buildSchoolStudentLookup(users);

        // 按班级分组统计
        Map<String, ClassStats> statsByClass = new HashMap<>();
        for (CampaignParticipation participation : participations) {
            User user = usersById.get(participation.getUserId());
            if (user == null) {
                continue;
            }

            SchoolStudent schoolStudent = resolveSchoolStudent(user, lookup);
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

        List<User> users = loadActiveUsersByIds(userIds);
        Map<Long, User> usersById = users.stream()
            .collect(Collectors.toMap(User::getId, u -> u));

        SchoolStudentLookup lookup = buildSchoolStudentLookup(users);

        // 按学院分组统计
        Map<String, CollegeStats> statsByCollege = new HashMap<>();
        for (CampaignParticipation participation : participations) {
            User user = usersById.get(participation.getUserId());
            if (user == null) {
                continue;
            }

            SchoolStudent schoolStudent = resolveSchoolStudent(user, lookup);
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

        // P2 修复: null totalScore 排名错误
        // 原 rankWrapper.gt("total_score", myTotalScore) 当 myTotalScore=null 时 SQL 恒 false → rank=1（错误）
        // 修复: null 视为 0，并使用 COALESCE 语义 — 得分比我高的人数 = 得分 > myScore 的人数
        Integer myTotalScore = myParticipation.getTotalScore();
        int myScoreForRank = myTotalScore != null ? myTotalScore : 0;

        QueryWrapper<CampaignParticipation> rankWrapper = new QueryWrapper<>();
        rankWrapper.eq("campaign_id", campaignId);
        rankWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        // 使用 apply 写原生 SQL，COALESCE 处理 null，避免 null 比较恒 false
        rankWrapper.apply("(COALESCE(total_score, 0) > {0})", myScoreForRank);
        long myRank = participationMapper.selectCount(rankWrapper) + 1;

        QueryWrapper<CampaignParticipation> totalWrapper = new QueryWrapper<>();
        totalWrapper.eq("campaign_id", campaignId);
        totalWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        long totalParticipants = participationMapper.selectCount(totalWrapper);

        QueryWrapper<CampaignParticipation> aroundWrapper = new QueryWrapper<>();
        aroundWrapper.eq("campaign_id", campaignId);
        aroundWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        // P2 修复: 排序时 null totalScore 排末尾（NULLS LAST 语义）
        aroundWrapper.orderByDesc("total_score", "completed_tasks", "id");
        List<CampaignParticipation> allRanked = participationMapper.selectList(aroundWrapper);

        Map<Long, Long> ranksByUserId = new HashMap<>();
        long currentRank = 0;
        Integer previousScore = null;
        for (int i = 0; i < allRanked.size(); i++) {
            CampaignParticipation participation = allRanked.get(i);
            Integer totalScore = participation.getTotalScore();
            // null 视为 0 用于排名比较
            int scoreForCompare = totalScore != null ? totalScore : 0;
            int prevForCompare = previousScore != null ? previousScore : 0;
            if (previousScore == null || prevForCompare != scoreForCompare) {
                currentRank = i + 1L;
                previousScore = totalScore;
            }
            ranksByUserId.put(participation.getUserId(), currentRank);
        }

        // P2 修复: 批量查询用户和学校信息，避免 N+1 查询
        List<Long> participantUserIds = allRanked.stream()
                .map(CampaignParticipation::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<User> users = loadActiveUsersByIds(participantUserIds);
        Map<Long, User> usersById = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        SchoolStudentLookup lookup = buildSchoolStudentLookup(users);

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
                SchoolStudent schoolStudent = resolveSchoolStudent(user, lookup);

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
        // 查询当前用户参与记录，确认已报名
        QueryWrapper<CampaignParticipation> myWrapper = new QueryWrapper<>();
        myWrapper.eq("campaign_id", campaignId);
        myWrapper.eq("user_id", userId);
        CampaignParticipation myParticipation = participationMapper.selectOne(myWrapper);

        if (myParticipation == null || myParticipation.getEnrollmentStatus() != EnrollmentStatus.ENROLLED) {
            return new ArrayList<>();
        }

        // 查询所有已报名参与者，按总分降序（null 视为 0 排末尾）
        QueryWrapper<CampaignParticipation> rankWrapper = new QueryWrapper<>();
        rankWrapper.eq("campaign_id", campaignId);
        rankWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        rankWrapper.orderByDesc("total_score", "completed_tasks", "id");
        List<CampaignParticipation> allRanked = participationMapper.selectList(rankWrapper);

        // 定位当前用户位置
        int myIndex = -1;
        for (int i = 0; i < allRanked.size(); i++) {
            if (allRanked.get(i).getUserId().equals(userId)) {
                myIndex = i;
                break;
            }
        }

        if (myIndex < 0) {
            return new ArrayList<>();
        }

        // 截取 [myIndex - range, myIndex + range] 范围
        int from = Math.max(0, myIndex - range);
        int to = Math.min(allRanked.size(), myIndex + range + 1);
        return allRanked.subList(from, to);
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

        // 全部任务数
        long taskCount = taskMapper.selectCount(
            new QueryWrapper<CampaignTask>().eq("campaign_id", campaignId).eq("deleted", 0)
        );
        overview.put("taskCount", taskCount);

        // 必做任务数
        long requiredTaskCount = taskMapper.selectCount(
            new QueryWrapper<CampaignTask>()
                .eq("campaign_id", campaignId)
                .eq("is_required", 1)
                .eq("deleted", 0)
        );
        overview.put("requiredTaskCount", requiredTaskCount);

        // 完成规则（供前端进度条计算）
        CampaignActivity campaign = campaignService.getCampaignById(campaignId);
        if (campaign != null) {
            overview.put("completionRule", campaign.getCompletionRule());
            overview.put("completionThreshold", campaign.getCompletionThreshold());
        }

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

        // P2 修复: null totalScore 排名错误 — null 视为 0，使用 COALESCE 避免 null 比较恒 false
        Integer myTotalScore = myParticipation.getTotalScore();
        int myScoreForRank = myTotalScore != null ? myTotalScore : 0;

        QueryWrapper<CampaignParticipation> rankWrapper = new QueryWrapper<>();
        rankWrapper.eq("campaign_id", campaignId);
        rankWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
        rankWrapper.apply("(COALESCE(total_score, 0) > {0})", myScoreForRank);
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
            taskProgress.put("isRepeatable", task.getIsRepeatable());
            taskProgress.put("currentValue", completion != null ? completion.getCurrentValue() : BigDecimal.ZERO);
            taskProgress.put("isCompleted", completion != null && completion.getIsCompleted());
            taskProgress.put("completionCount", completion != null ? completion.getCompletionCount() : 0);
            // 返回实际已获积分（可重复任务可能多次加分，不能只显示单次 points）
            taskProgress.put("earnedScore", completion != null && completion.getEarnedScore() != null
                ? completion.getEarnedScore() : 0);

            progress.add(taskProgress);
        }

        return progress;
    }

    @Autowired
    private CampaignManualRecordMapper manualRecordMapper;

    @Override
    public void applyManualRecord(Long campaignId, Long taskId, Long userId, BigDecimal value) {
        CampaignTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("applyManualRecord: 任务{}不存在，跳过", taskId);
            return;
        }

        // 获取当前完成记录（若无则从0开始）
        QueryWrapper<CampaignTaskCompletion> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("task_id", taskId);
        wrapper.eq("user_id", userId);
        CampaignTaskCompletion completion = completionMapper.selectOne(wrapper);

        BigDecimal currentValue = (completion != null && completion.getCurrentValue() != null)
            ? completion.getCurrentValue() : BigDecimal.ZERO;

        // 手动录入值直接追加到当前进度
        BigDecimal newValue = currentValue.add(value);

        updateTaskCompletion(campaignId, taskId, userId, newValue, task.getTargetValue());
        log.debug("applyManualRecord: campaignId={}, taskId={}, userId={}, delta={}, newTotal={}",
            campaignId, taskId, userId, value, newValue);
    }

    @Override
    public List<Map<String, Object>> getMyScoreDetails(Long campaignId, Long userId) {
        List<Map<String, Object>> details = new ArrayList<>();

        List<CampaignTask> tasks = taskMapper.selectList(
            new QueryWrapper<CampaignTask>().eq("campaign_id", campaignId).eq("deleted", 0)
        );

        // 批量查手动录入记录（该用户在该活动下的全部）
        QueryWrapper<com.english.entity.CampaignManualRecord> mrWrapper = new QueryWrapper<>();
        mrWrapper.eq("campaign_id", campaignId);
        mrWrapper.eq("user_id", userId);
        mrWrapper.eq("deleted", 0);
        mrWrapper.orderByDesc("created_time");
        List<com.english.entity.CampaignManualRecord> allManualRecords =
            manualRecordMapper.selectList(mrWrapper);
        Map<Long, List<com.english.entity.CampaignManualRecord>> manualByTask = allManualRecords.stream()
            .collect(Collectors.groupingBy(com.english.entity.CampaignManualRecord::getTaskId));

        for (CampaignTask task : tasks) {
            QueryWrapper<CampaignTaskCompletion> cw = new QueryWrapper<>();
            cw.eq("campaign_id", campaignId);
            cw.eq("task_id", task.getId());
            cw.eq("user_id", userId);
            CampaignTaskCompletion completion = completionMapper.selectOne(cw);

            Map<String, Object> item = new HashMap<>();
            item.put("taskId", task.getId());
            item.put("taskName", task.getTaskName());
            item.put("taskType", task.getTaskType());
            item.put("taskUnit", task.getTaskUnit());
            item.put("targetValue", task.getTargetValue());
            item.put("points", task.getPoints());
            item.put("isRepeatable", task.getIsRepeatable());
            item.put("currentValue", completion != null ? completion.getCurrentValue() : java.math.BigDecimal.ZERO);
            item.put("earnedScore", completion != null && completion.getEarnedScore() != null
                ? completion.getEarnedScore() : 0);
            item.put("completionCount", completion != null ? completion.getCompletionCount() : 0);
            item.put("isCompleted", completion != null && Boolean.TRUE.equals(completion.getIsCompleted()));
            item.put("lastUpdateTime", completion != null ? completion.getLastUpdateTime() : null);

            // 最近5条手动录入记录作为积分来源说明
            List<com.english.entity.CampaignManualRecord> taskRecords =
                manualByTask.getOrDefault(task.getId(), java.util.Collections.emptyList());
            List<Map<String, Object>> recordSummaries = taskRecords.stream()
                .limit(5)
                .map(r -> {
                    Map<String, Object> rec = new HashMap<>();
                    rec.put("value", r.getRecordValue());
                    rec.put("type", r.getRecordType());
                    rec.put("remark", r.getRemark());
                    rec.put("createdTime", r.getCreatedTime());
                    return rec;
                })
                .collect(Collectors.toList());
            item.put("recentRecords", recordSummaries);

            details.add(item);
        }

        return details;
    }
}
