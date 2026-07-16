package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CampaignActivity;
import com.english.entity.CampaignParticipation;
import com.english.entity.CampaignTask;
import com.english.entity.SchoolStudent;
import com.english.entity.User;
import com.english.enums.CampaignStatus;
import com.english.enums.EnrollmentMode;
import com.english.enums.EnrollmentStatus;
import com.english.mapper.CampaignActivityMapper;
import com.english.mapper.CampaignParticipationMapper;
import com.english.mapper.CampaignTaskMapper;
import com.english.mapper.SchoolStudentMapper;
import com.english.mapper.UserMapper;
import com.english.service.CampaignEnrollmentService;
import com.english.service.CampaignService;
import com.english.service.CampaignStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动报名管理服务实现
 */
@Slf4j
@Service
public class CampaignEnrollmentServiceImpl implements CampaignEnrollmentService {

    @Autowired
    private CampaignParticipationMapper participationMapper;

    @Autowired
    private CampaignActivityMapper campaignActivityMapper;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private CampaignStatsService campaignStatsService;

    @Autowired
    private CampaignTaskMapper taskMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SchoolStudentMapper schoolStudentMapper;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(value = "campaign_overview", key = "#campaignId", cacheManager = "campaignCacheManager")
    public void enrollCampaign(Long campaignId, Long userId) {
        CampaignActivity campaign = campaignService.getCampaignById(campaignId);
        if (campaign == null) {
            throw new RuntimeException("活动不存在");
        }

        // 检查是否已报名
        if (isEnrolled(campaignId, userId)) {
            throw new RuntimeException("您已报名该活动");
        }

        // P0-10 修复: 检查是否有退出记录，防止刷报名奖励
        QueryWrapper<CampaignParticipation> withdrawnWrapper = new QueryWrapper<>();
        withdrawnWrapper.eq("campaign_id", campaignId);
        withdrawnWrapper.eq("user_id", userId);
        withdrawnWrapper.eq("enrollment_status", EnrollmentStatus.WITHDRAWN);
        long withdrawnCount = participationMapper.selectCount(withdrawnWrapper);
        if (withdrawnCount > 0) {
            throw new RuntimeException("您已退出该活动，无法重新报名");
        }

        // 检查报名资格
        if (!campaignService.canUserEnroll(campaignId, userId)) {
            List<String> restrictions = campaignService.getEnrollmentRestrictions(campaignId, userId);
            throw new RuntimeException("无法报名：" + String.join("; ", restrictions));
        }

        // P0-01 修复: 人数限制检查（带事务隔离，减少竞态条件）
        // 注意：完全消除竞态需要数据库唯一约束 (campaign_id, user_id)
        if (campaign.getMaxParticipants() != null && campaign.getMaxParticipants() > 0) {
            QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
            wrapper.eq("campaign_id", campaignId);
            wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
            long count = participationMapper.selectCount(wrapper);
            if (count >= campaign.getMaxParticipants()) {
                throw new RuntimeException("活动参与人数已满");
            }
        }

        // 创建参与记录
        CampaignParticipation participation = new CampaignParticipation();
        participation.setCampaignId(campaignId);
        participation.setUserId(userId);
        participation.setEnrollmentTime(LocalDateTime.now());
        participation.setEnrollmentSource("MANUAL");

        // 根据报名模式设置状态
        if (campaign.getEnrollmentMode() == EnrollmentMode.INVITE_ONLY) {
            participation.setEnrollmentStatus(EnrollmentStatus.PENDING);
        } else {
            participation.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
        }

        participation.setCompletionStatus("IN_PROGRESS");
        participation.setJoinTime(LocalDateTime.now());

        participationMapper.insert(participation);
    }

    @Override
    @CacheEvict(value = "campaign_overview", key = "#campaignId", cacheManager = "campaignCacheManager")
    public void withdrawCampaign(Long campaignId, Long userId) {
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        CampaignParticipation participation = participationMapper.selectOne(wrapper);

        if (participation == null) {
            throw new RuntimeException("您未报名该活动");
        }

        participation.setEnrollmentStatus(EnrollmentStatus.WITHDRAWN);
        participation.setLastUpdateTime(LocalDateTime.now());
        participationMapper.updateById(participation);
    }

    @Override
    public boolean isEnrolled(Long campaignId, Long userId) {
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        wrapper.in("enrollment_status", EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING);
        return participationMapper.selectCount(wrapper) > 0;
    }

    @Override
    public CampaignParticipation getParticipation(Long campaignId, Long userId) {
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("user_id", userId);
        return participationMapper.selectOne(wrapper);
    }

    @Override
    public Page<CampaignActivity> getEnrollableCampaigns(Long userId, int current, int size, Map<String, Object> params) {
        Page<CampaignActivity> page = new Page<>(current, size);
        QueryWrapper<CampaignActivity> wrapper = new QueryWrapper<>();

        // 只显示进行中的活动；启动后立即可见（不考虑报名时间窗口）
        wrapper.eq("status", CampaignStatus.ACTIVE);

        // 时间范围筛选：活动尚未结束
        LocalDateTime now = LocalDateTime.now();
        wrapper.ge("end_time", now);

        if (params.containsKey("keyword")) {
            String keyword = (String) params.get("keyword");
            // P0-07 修复: 转义 SQL LIKE 通配符，防止 SQL 注入
            String escapedKeyword = escapeSqlLikeWildcards(keyword);
            wrapper.and(w -> w.like("title", escapedKeyword).or().like("description", escapedKeyword));
        }

        wrapper.orderByDesc("created_time");
        Page<CampaignActivity> result = campaignActivityMapper.selectPage(page, wrapper);

        // 填充参与人数和当前用户报名状态
        enrichCampaignList(result.getRecords(), userId);

        return result;
    }

    @Override
    public Page<CampaignActivity> getMyEnrolledCampaigns(Long userId, int current, int size, Map<String, Object> params) {
        // 获取用户参与的活动ID列表（包含 ENROLLED 和 PENDING，PENDING 为 INVITE_ONLY 待审核状态）
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.in("enrollment_status", EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING);
        List<CampaignParticipation> participations = participationMapper.selectList(wrapper);

        Page<CampaignActivity> page = new Page<>(current, size);
        if (participations.isEmpty()) {
            return page;
        }

        List<Long> campaignIds = participations.stream()
            .map(CampaignParticipation::getCampaignId)
            .toList();

        QueryWrapper<CampaignActivity> campaignWrapper = new QueryWrapper<>();
        campaignWrapper.in("id", campaignIds);

        if (params.containsKey("status") && params.get("status") != null
                && !params.get("status").toString().isEmpty()) {
            campaignWrapper.eq("status", params.get("status"));
        }

        campaignWrapper.orderByDesc("created_time");
        Page<CampaignActivity> result = campaignActivityMapper.selectPage(page, campaignWrapper);

        // 填充参与人数、报名状态和进度信息
        enrichCampaignList(result.getRecords(), userId);
        enrichMyEnrolledProgress(result.getRecords(), userId);

        return result;
    }

    /**
     * 填充"我的活动"列表的进度信息（总分、完成任务数、排名等）
     */
    private void enrichMyEnrolledProgress(List<CampaignActivity> campaigns, Long userId) {
        if (campaigns == null || campaigns.isEmpty() || userId == null) {
            return;
        }

        for (CampaignActivity campaign : campaigns) {
            // 获取用户进度数据
            Map<String, Object> progress = campaignStatsService.getMyProgress(campaign.getId(), userId);

            campaign.setCompletionStatus((String) progress.get("completionStatus"));
            campaign.setTotalScore((Integer) progress.get("totalScore"));
            campaign.setCompletedTasks((Integer) progress.get("completedTasks"));

            // 获取活动总任务数
            QueryWrapper<CampaignTask> taskWrapper = new QueryWrapper<>();
            taskWrapper.eq("campaign_id", campaign.getId());
            long totalTasks = taskMapper.selectCount(taskWrapper);
            campaign.setTotalTasks((int) totalTasks);

            // 排名
            Object rankObj = progress.get("myRank");
            if (rankObj instanceof Long) {
                campaign.setMyRank((Long) rankObj);
            } else if (rankObj instanceof Integer) {
                campaign.setMyRank(((Integer) rankObj).longValue());
            }
        }
    }

    /**
     * 填充活动列表的额外信息（参与人数、当前用户报名状态）
     */
    private void enrichCampaignList(List<CampaignActivity> campaigns, Long userId) {
        if (campaigns == null || campaigns.isEmpty()) {
            return;
        }

        List<Long> campaignIds = campaigns.stream()
            .map(CampaignActivity::getId)
            .toList();

        // 批量查询每个活动的参与人数（避免 N+1 查询）
        List<Map<String, Object>> countRows = participationMapper.countEnrolledByCampaignIds(campaignIds);
        java.util.Map<Long, Integer> countMap = new java.util.HashMap<>();
        if (countRows != null) {
            for (Map<String, Object> row : countRows) {
                Object cidObj = row.get("campaignId");
                Object cntObj = row.get("cnt");
                if (cidObj == null || cntObj == null) continue;
                Long cid = ((Number) cidObj).longValue();
                int cnt = ((Number) cntObj).intValue();
                countMap.put(cid, cnt);
            }
        }
        for (CampaignActivity campaign : campaigns) {
            campaign.setParticipantCount(countMap.getOrDefault(campaign.getId(), 0));
        }

        // 批量查询当前用户的报名状态
        if (userId != null) {
            QueryWrapper<CampaignParticipation> userWrapper = new QueryWrapper<>();
            userWrapper.eq("user_id", userId);
            userWrapper.in("campaign_id", campaignIds);
            userWrapper.in("enrollment_status", EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING);
            List<CampaignParticipation> userParticipations = participationMapper.selectList(userWrapper);

            // 构建 campaignId -> isEnrolled 的映射
            java.util.Set<Long> enrolledCampaignIds = userParticipations.stream()
                .map(CampaignParticipation::getCampaignId)
                .collect(java.util.stream.Collectors.toSet());

            // 设置每个活动的 isEnrolled 状态
            for (CampaignActivity campaign : campaigns) {
                campaign.setIsEnrolled(enrolledCampaignIds.contains(campaign.getId()));
            }
        }
    }

    @Override
    @CacheEvict(value = "campaign_overview", key = "#campaignId", cacheManager = "campaignCacheManager")
    public void approveEnrollment(Long campaignId, Long participationId) {
        CampaignParticipation participation = participationMapper.selectById(participationId);
        if (participation == null) {
            throw new RuntimeException("报名记录不存在");
        }

        // P0-02 修复: 验证报名记录是否属于该活动
        if (!participation.getCampaignId().equals(campaignId)) {
            throw new RuntimeException("报名记录不属于该活动");
        }

        if (participation.getEnrollmentStatus() != EnrollmentStatus.PENDING) {
            throw new RuntimeException("只能审核待审核状态的报名");
        }

        participation.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
        participation.setLastUpdateTime(LocalDateTime.now());
        participationMapper.updateById(participation);
    }

    @Override
    @CacheEvict(value = "campaign_overview", key = "#campaignId", cacheManager = "campaignCacheManager")
    public void rejectEnrollment(Long campaignId, Long participationId, String reason) {
        CampaignParticipation participation = participationMapper.selectById(participationId);
        if (participation == null) {
            throw new RuntimeException("报名记录不存在");
        }

        // P0-02 修复: 验证报名记录是否属于该活动
        if (!participation.getCampaignId().equals(campaignId)) {
            throw new RuntimeException("报名记录不属于该活动");
        }

        if (participation.getEnrollmentStatus() != EnrollmentStatus.PENDING) {
            throw new RuntimeException("只能审核待审核状态的报名");
        }

        participation.setEnrollmentStatus(EnrollmentStatus.REJECTED);
        participation.setLastUpdateTime(LocalDateTime.now());
        participationMapper.updateById(participation);
    }

    @Override
    public List<CampaignParticipation> getPendingEnrollments(Long campaignId) {
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("enrollment_status", EnrollmentStatus.PENDING);
        wrapper.orderByAsc("enrollment_time");
        List<CampaignParticipation> list = participationMapper.selectList(wrapper);
        enrichWithUserInfo(list);
        return list;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(value = "campaign_overview", key = "#campaignId", cacheManager = "campaignCacheManager")
    public com.english.dto.BatchEnrollResult batchEnrollUsers(Long campaignId, List<Long> userIds, boolean force) {
        com.english.dto.BatchEnrollResult result = new com.english.dto.BatchEnrollResult();

        // P0-09 修复: 限制批量操作大小，防止 DoS 攻击
        final int MAX_BATCH_SIZE = 1000;
        if (userIds == null || userIds.isEmpty()) {
            throw new RuntimeException("用户ID列表不能为空");
        }
        if (userIds.size() > MAX_BATCH_SIZE) {
            throw new RuntimeException("批量添加数量超过限制（最多 " + MAX_BATCH_SIZE + " 个）");
        }

        CampaignActivity campaign = campaignService.getCampaignById(campaignId);
        if (campaign == null) {
            throw new RuntimeException("活动不存在");
        }

        // 人数上限检查：提前计算剩余名额，避免逐个插入时超额
        Integer maxParticipants = campaign.getMaxParticipants();
        int currentEnrolled = 0;
        if (maxParticipants != null && maxParticipants > 0) {
            QueryWrapper<CampaignParticipation> countWrapper = new QueryWrapper<>();
            countWrapper.eq("campaign_id", campaignId);
            countWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
            currentEnrolled = participationMapper.selectCount(countWrapper).intValue();
        }
        result.setMaxParticipants(maxParticipants);

        for (Long userId : userIds) {
            try {
                // 检查是否已报名
                if (isEnrolled(campaignId, userId)) {
                    result.getAlreadyEnrolledUserIds().add(userId);
                    continue;
                }

                // 人数上限检查（逐个插入前再次校验，防止超额）
                if (maxParticipants != null && maxParticipants > 0) {
                    if (currentEnrolled >= maxParticipants) {
                        result.getCapacityExceededUserIds().add(userId);
                        continue;
                    }
                }

                // 报名范围检查（force=false 时不符合范围的用户跳过并记录原因）
                if (!force && campaign.getEnrollmentScope() != null
                        && campaign.getEnrollmentScope() != com.english.enums.EnrollmentScope.ALL) {
                    if (!campaignService.canUserEnroll(campaignId, userId)) {
                        List<String> restrictions = campaignService.getEnrollmentRestrictions(campaignId, userId);
                        // 过滤掉"已报名"等无关原因，只保留范围相关
                        String reason = restrictions.isEmpty()
                                ? "不在活动参与范围内"
                                : String.join("; ", restrictions);
                        result.getOutOfScopeUserIds().add(
                                new com.english.dto.BatchEnrollResult.UserReason(userId, reason));
                        continue;
                    }
                }

                // 创建参与记录
                CampaignParticipation participation = new CampaignParticipation();
                participation.setCampaignId(campaignId);
                participation.setUserId(userId);
                participation.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
                participation.setEnrollmentSource(force ? "ADMIN_FORCE" : "ADMIN");
                participation.setEnrollmentTime(LocalDateTime.now());
                participation.setCompletionStatus("IN_PROGRESS");
                participation.setJoinTime(LocalDateTime.now());
                participationMapper.insert(participation);

                result.getSuccessUserIds().add(userId);
                if (maxParticipants != null && maxParticipants > 0) {
                    currentEnrolled++;
                }
            } catch (Exception e) {
                log.error("批量添加用户{}失败", userId, e);
                result.getFailedUserIds().add(
                        new com.english.dto.BatchEnrollResult.UserReason(userId, e.getMessage()));
            }
        }

        result.setCurrentCount(currentEnrolled);
        return result;
    }

    @Override
    public List<CampaignParticipation> getEnrolledList(Long campaignId) {
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED.name());
        wrapper.orderByDesc("join_time");
        List<CampaignParticipation> list = participationMapper.selectList(wrapper);
        enrichWithUserInfo(list);
        return list;
    }

    @Override
    public List<CampaignParticipation> getRejectedList(Long campaignId) {
        QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
        wrapper.eq("campaign_id", campaignId);
        wrapper.eq("enrollment_status", EnrollmentStatus.REJECTED.name());
        wrapper.orderByDesc("enrollment_time");
        List<CampaignParticipation> list = participationMapper.selectList(wrapper);
        enrichWithUserInfo(list);
        return list;
    }

    private void enrichWithUserInfo(List<CampaignParticipation> participations) {
        if (participations == null || participations.isEmpty()) {
            return;
        }

        // 批量查询用户信息
        List<Long> userIds = participations.stream()
                .map(CampaignParticipation::getUserId)
                .distinct()
                .toList();

        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        // 批量查询学校学生信息
        List<Long> schoolStudentIds = users.stream()
                .map(User::getSchoolStudentId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        List<SchoolStudent> students = schoolStudentIds.isEmpty()
                ? java.util.Collections.emptyList()
                : schoolStudentMapper.selectBatchIds(schoolStudentIds);

        Map<Long, SchoolStudent> studentsBySchoolId = students.stream()
                .collect(java.util.stream.Collectors.toMap(SchoolStudent::getId, s -> s));

        Map<String, SchoolStudent> studentsByStudentId = students.stream()
                .filter(s -> s.getStudentId() != null && !s.getStudentId().isBlank())
                .collect(java.util.stream.Collectors.toMap(SchoolStudent::getStudentId, s -> s, (s1, s2) -> s1));

        // 填充用户信息
        for (CampaignParticipation participation : participations) {
            User user = userMap.get(participation.getUserId());
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
            participation.setStudentId(schoolStudent != null ? schoolStudent.getStudentId() : user.getUsername());
            participation.setClassName(schoolStudent != null ? schoolStudent.getClassName() : null);
            participation.setCollegeName(schoolStudent != null ? schoolStudent.getCollegeName() : null);
        }
    }

    /**
     * 转义 SQL LIKE 通配符，防止 SQL 注入
     *
     * @param input 用户输入
     * @return 转义后的字符串
     */
    private String escapeSqlLikeWildcards(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // 转义 SQL LIKE 特殊字符: % _ \
        return input.replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
    }

    @Override
    public int countEnrolledParticipants(Long campaignId) {
        if (campaignId == null) {
            return 0;
        }
        // 复用已有的批量统计接口，避免重复实现 SQL
        List<Map<String, Object>> rows = participationMapper.countEnrolledByCampaignIds(List.of(campaignId));
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        Object cntObj = rows.get(0).get("cnt");
        return cntObj == null ? 0 : ((Number) cntObj).intValue();
    }
}
