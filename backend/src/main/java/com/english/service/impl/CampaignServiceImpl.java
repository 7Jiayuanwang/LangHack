package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CampaignActivity;
import com.english.entity.User;
import com.english.enums.CampaignStatus;
import com.english.enums.EnrollmentScope;
import com.english.mapper.CampaignActivityMapper;
import com.english.service.CampaignService;
import com.english.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 活动管理服务实现
 */
@Slf4j
@Service
public class CampaignServiceImpl implements CampaignService {

    @Autowired
    private CampaignActivityMapper campaignActivityMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public CampaignActivity createCampaign(CampaignActivity campaign) {
        LocalDateTime now = LocalDateTime.now();

        // 验证活动时间范围
        if (campaign.getEndTime().isBefore(campaign.getStartTime())) {
            throw new RuntimeException("活动结束时间不能早于开始时间");
        }

        // 验证活动开始时间不能早于当前时间
        if (campaign.getStartTime().isBefore(now)) {
            throw new RuntimeException("活动开始时间不能早于当前时间");
        }

        // 验证报名时间范围
        if (campaign.getEnrollmentStartTime() != null && campaign.getEnrollmentEndTime() != null) {
            // 报名结束时间不能晚于活动结束时间
            if (campaign.getEnrollmentEndTime().isAfter(campaign.getEndTime())) {
                throw new RuntimeException("报名结束时间不能晚于活动结束时间");
            }
            // 报名结束时间不能早于报名开始时间
            if (campaign.getEnrollmentEndTime().isBefore(campaign.getEnrollmentStartTime())) {
                throw new RuntimeException("报名结束时间不能早于报名开始时间");
            }
        }

        // 设置默认值
        if (campaign.getStatus() == null) {
            campaign.setStatus(CampaignStatus.DRAFT);
        }
        campaign.setCreatedTime(now);
        campaign.setUpdatedTime(now);

        // 序列化enrollmentTargetIdList为JSON
        if (campaign.getEnrollmentTargetIdList() != null && !campaign.getEnrollmentTargetIdList().isEmpty()) {
            try {
                campaign.setEnrollmentTargetIds(objectMapper.writeValueAsString(campaign.getEnrollmentTargetIdList()));
            } catch (Exception e) {
                log.error("序列化enrollmentTargetIds失败", e);
            }
        }

        campaignActivityMapper.insert(campaign);
        return campaign;
    }

    @Override
    public CampaignActivity updateCampaign(Long id, CampaignActivity campaign) {
        CampaignActivity existing = getCampaignById(id);
        if (existing == null) {
            throw new RuntimeException("活动不存在");
        }

        // 合并时间字段（使用新值或保留旧值）
        LocalDateTime startTime = campaign.getStartTime() != null ? campaign.getStartTime() : existing.getStartTime();
        LocalDateTime endTime = campaign.getEndTime() != null ? campaign.getEndTime() : existing.getEndTime();
        LocalDateTime enrollmentStartTime = campaign.getEnrollmentStartTime() != null
            ? campaign.getEnrollmentStartTime()
            : existing.getEnrollmentStartTime();
        LocalDateTime enrollmentEndTime = campaign.getEnrollmentEndTime() != null
            ? campaign.getEnrollmentEndTime()
            : existing.getEnrollmentEndTime();

        // 验证活动时间范围
        if (endTime.isBefore(startTime)) {
            throw new RuntimeException("活动结束时间不能早于开始时间");
        }

        // 验证报名时间范围
        if (enrollmentStartTime != null && enrollmentEndTime != null) {
            // 报名结束时间不能晚于活动结束时间
            if (enrollmentEndTime.isAfter(endTime)) {
                throw new RuntimeException("报名结束时间不能晚于活动结束时间");
            }
            // 报名结束时间不能早于报名开始时间
            if (enrollmentEndTime.isBefore(enrollmentStartTime)) {
                throw new RuntimeException("报名结束时间不能早于报名开始时间");
            }
        }

        campaign.setId(id);
        campaign.setUpdatedTime(LocalDateTime.now());

        // 序列化enrollmentTargetIdList为JSON
        if (campaign.getEnrollmentTargetIdList() != null) {
            try {
                campaign.setEnrollmentTargetIds(objectMapper.writeValueAsString(campaign.getEnrollmentTargetIdList()));
            } catch (Exception e) {
                log.error("序列化enrollmentTargetIds失败", e);
            }
        }

        campaignActivityMapper.updateById(campaign);
        return getCampaignById(id);
    }

    @Override
    public void deleteCampaign(Long id) {
        CampaignActivity campaign = getCampaignById(id);
        if (campaign == null) {
            throw new RuntimeException("活动不存在");
        }
        campaignActivityMapper.deleteById(id);
    }

    @Override
    public CampaignActivity getCampaignById(Long id) {
        CampaignActivity campaign = campaignActivityMapper.selectById(id);
        if (campaign != null && StringUtils.hasText(campaign.getEnrollmentTargetIds())) {
            // 反序列化JSON为List
            try {
                List<String> idList = objectMapper.readValue(
                    campaign.getEnrollmentTargetIds(),
                    new TypeReference<List<String>>() {}
                );
                campaign.setEnrollmentTargetIdList(idList);
            } catch (Exception e) {
                log.error("反序列化enrollmentTargetIds失败", e);
            }
        }
        return campaign;
    }

    @Override
    public Page<CampaignActivity> getCampaigns(int current, int size, Map<String, Object> params) {
        Page<CampaignActivity> page = new Page<>(current, size);
        QueryWrapper<CampaignActivity> wrapper = new QueryWrapper<>();

        // 筛选条件 - 只有非空字符串才应用筛选
        String status = (String) params.get("status");
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }

        String keyword = (String) params.get("keyword");
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("title", keyword).or().like("description", keyword));
        }

        if (params.containsKey("createdBy")) {
            wrapper.eq("created_by", params.get("createdBy"));
        }

        // 排序处理
        String orderBy = (String) params.get("orderBy");
        if (orderBy != null && !orderBy.isEmpty()) {
            switch (orderBy) {
                case "created_time_desc":
                    wrapper.orderByDesc("created_time");
                    break;
                case "created_time_asc":
                    wrapper.orderByAsc("created_time");
                    break;
                case "start_time_desc":
                    wrapper.orderByDesc("start_time");
                    break;
                case "start_time_asc":
                    wrapper.orderByAsc("start_time");
                    break;
                case "updated_time_desc":
                    wrapper.orderByDesc("updated_time");
                    break;
                default:
                    wrapper.orderByDesc("created_time");
            }
        } else {
            wrapper.orderByDesc("created_time");
        }

        return campaignActivityMapper.selectPage(page, wrapper);
    }

    @Override
    public void startCampaign(Long id) {
        CampaignActivity campaign = getCampaignById(id);
        if (campaign == null) {
            throw new RuntimeException("活动不存在");
        }
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new RuntimeException("只有草稿状态的活动可以启动");
        }

        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setUpdatedTime(LocalDateTime.now());
        campaignActivityMapper.updateById(campaign);
    }

    @Override
    public void endCampaign(Long id) {
        CampaignActivity campaign = getCampaignById(id);
        if (campaign == null) {
            throw new RuntimeException("活动不存在");
        }
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new RuntimeException("只有进行中的活动可以结束");
        }

        campaign.setStatus(CampaignStatus.ENDED);
        campaign.setUpdatedTime(LocalDateTime.now());
        campaignActivityMapper.updateById(campaign);
    }

    @Override
    public void archiveCampaign(Long id) {
        CampaignActivity campaign = getCampaignById(id);
        if (campaign == null) {
            throw new RuntimeException("活动不存在");
        }
        if (campaign.getStatus() != CampaignStatus.ENDED) {
            throw new RuntimeException("只有已结束的活动可以归档");
        }

        campaign.setStatus(CampaignStatus.ARCHIVED);
        campaign.setUpdatedTime(LocalDateTime.now());
        campaignActivityMapper.updateById(campaign);
    }

    @Override
    public boolean canUserEnroll(Long campaignId, Long userId) {
        CampaignActivity campaign = getCampaignById(campaignId);
        if (campaign == null) {
            return false;
        }

        // 检查活动状态
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            return false;
        }

        // 检查时间范围
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(campaign.getStartTime()) || now.isAfter(campaign.getEndTime())) {
            return false;
        }

        // 检查报名范围
        if (campaign.getEnrollmentScope() == EnrollmentScope.ALL) {
            return true;
        }

        User user = userService.getUserById(userId);
        if (user == null) {
            return false;
        }

        List<String> targetIds = campaign.getEnrollmentTargetIdList();
        if (targetIds == null || targetIds.isEmpty()) {
            return true;
        }

        // 根据不同的范围类型检查
        switch (campaign.getEnrollmentScope()) {
            case COLLEGE:
                // 检查学院（需要从school_student表获取）
                // TODO: 实现学院检查逻辑
                return true;
            case CLASS:
                // 检查班级
                // TODO: 实现班级检查逻辑
                return true;
            case CUSTOM:
                // 检查用户ID是否在白名单中
                return targetIds.contains(String.valueOf(userId));
            default:
                return false;
        }
    }

    @Override
    public List<String> getEnrollmentRestrictions(Long campaignId, Long userId) {
        List<String> restrictions = new ArrayList<>();
        CampaignActivity campaign = getCampaignById(campaignId);

        if (campaign == null) {
            restrictions.add("活动不存在");
            return restrictions;
        }

        // 检查活动状态
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            restrictions.add("活动未开始或已结束");
        }

        // 检查时间范围
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(campaign.getStartTime())) {
            restrictions.add("活动尚未开始");
        }
        if (now.isAfter(campaign.getEndTime())) {
            restrictions.add("活动已结束");
        }

        // 检查报名范围
        if (campaign.getEnrollmentScope() != EnrollmentScope.ALL) {
            if (!canUserEnroll(campaignId, userId)) {
                restrictions.add("您不在活动参与范围内");
            }
        }

        return restrictions;
    }

    @Override
    public List<CampaignActivity> getActiveCampaigns() {
        return campaignActivityMapper.selectList(
            new LambdaQueryWrapper<CampaignActivity>()
                .eq(CampaignActivity::getStatus, CampaignStatus.ACTIVE)
                .eq(CampaignActivity::getDeleted, 0)
        );
    }

    @Override
    public List<CampaignActivity> getExpiredActiveCampaigns() {
        LocalDateTime now = LocalDateTime.now();
        return campaignActivityMapper.selectList(
            new LambdaQueryWrapper<CampaignActivity>()
                .eq(CampaignActivity::getStatus, CampaignStatus.ACTIVE)
                .lt(CampaignActivity::getEndTime, now)
                .eq(CampaignActivity::getDeleted, 0)
        );
    }
}
