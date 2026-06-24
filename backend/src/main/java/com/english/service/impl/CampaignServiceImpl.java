package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CampaignActivity;
import com.english.entity.SchoolStudent;
import com.english.entity.User;
import com.english.enums.CampaignStatus;
import com.english.enums.EnrollmentScope;
import com.english.mapper.CampaignActivityMapper;
import com.english.mapper.SchoolStudentMapper;
import com.english.service.CampaignService;
import com.english.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 报名范围目标配置（向后兼容新旧 JSON 格式）
 * <li>旧格式：flat array，如 ["学院A", "学院B"] 或 ["2023", "2022"]
 * <li>新格式：对象，支持嵌套年级/班级，
 *   如 { "collegeIds": ["学院A"], "gradeIds": ["2023"] }
 */
@lombok.Data
class EnrollmentTargetConfig {
    /** 学院 ID / 名称列表 */
    private List<String> collegeIds = Collections.emptyList();
    /** 年级 ID / 名称列表 */
    private List<String> gradeIds = Collections.emptyList();
    /** 班级 ID / 名称列表 */
    private List<String> classIds = Collections.emptyList();
    /** 旧格式 flat list（降级兼容） */
    private List<String> flatList = Collections.emptyList();
}

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
    private SchoolStudentMapper schoolStudentMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public CampaignActivity createCampaign(CampaignActivity campaign) {
        // 不支持 CUSTOM 报名范围
        if (campaign.getEnrollmentScope() == EnrollmentScope.CUSTOM) {
            throw new RuntimeException("不支持「指定学生」报名范围");
        }

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

        // 序列化 enrollmentTargetIdList 为 JSON（向后兼容新旧格式）
        if (campaign.getEnrollmentTargetIdList() != null && !campaign.getEnrollmentTargetIdList().isEmpty()) {
            try {
                // COLLEGE 范围支持嵌套年级 → 尝试解析为对象格式 { collegeIds, gradeIds }
                if (campaign.getEnrollmentScope() == EnrollmentScope.COLLEGE) {
                    String firstElement = campaign.getEnrollmentTargetIdList().get(0);
                    // 尝试判断是否为 JSON 对象格式
                    if (firstElement.trim().startsWith("{")) {
                        // 前端传了对象结构，反序列化后重新序列化
                        @SuppressWarnings("unchecked")
                        Map<String, Object> cfg = objectMapper.readValue(firstElement, Map.class);
                        EnrollmentTargetConfig config = new EnrollmentTargetConfig();
                        config.setCollegeIds(toStringList(cfg.get("collegeIds")));
                        config.setGradeIds(toStringList(cfg.get("gradeIds")));
                        campaign.setEnrollmentTargetIds(objectMapper.writeValueAsString(config));
                    } else {
                        // 旧格式：flat array of college names
                        campaign.setEnrollmentTargetIds(objectMapper.writeValueAsString(campaign.getEnrollmentTargetIdList()));
                    }
                } else {
                    // 旧格式：flat array（GRADE / CLASS）
                    campaign.setEnrollmentTargetIds(objectMapper.writeValueAsString(campaign.getEnrollmentTargetIdList()));
                }
            } catch (Exception e) {
                log.error("序列化 enrollmentTargetIds 失败", e);
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

        // 不支持 CUSTOM 报名范围
        if (campaign.getEnrollmentScope() == EnrollmentScope.CUSTOM) {
            throw new RuntimeException("不支持「指定学生」报名范围");
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

        // 序列化 enrollmentTargetIdList 为 JSON（向后兼容新旧格式）
        if (campaign.getEnrollmentTargetIdList() != null && !campaign.getEnrollmentTargetIdList().isEmpty()) {
            try {
                // COLLEGE 范围支持嵌套年级 → 尝试解析为对象格式 { collegeIds, gradeIds }
                if (campaign.getEnrollmentScope() == EnrollmentScope.COLLEGE) {
                    String firstElement = campaign.getEnrollmentTargetIdList().get(0);
                    // 尝试判断是否为 JSON 对象格式
                    if (firstElement.trim().startsWith("{")) {
                        // 前端传了对象结构，反序列化后重新序列化
                        @SuppressWarnings("unchecked")
                        Map<String, Object> cfg = objectMapper.readValue(firstElement, Map.class);
                        EnrollmentTargetConfig config = new EnrollmentTargetConfig();
                        config.setCollegeIds(toStringList(cfg.get("collegeIds")));
                        config.setGradeIds(toStringList(cfg.get("gradeIds")));
                        campaign.setEnrollmentTargetIds(objectMapper.writeValueAsString(config));
                    } else {
                        // 旧格式：flat array of college names
                        campaign.setEnrollmentTargetIds(objectMapper.writeValueAsString(campaign.getEnrollmentTargetIdList()));
                    }
                } else {
                    // 旧格式：flat array（GRADE / CLASS）
                    campaign.setEnrollmentTargetIds(objectMapper.writeValueAsString(campaign.getEnrollmentTargetIdList()));
                }
            } catch (Exception e) {
                log.error("序列化 enrollmentTargetIds 失败", e);
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
            // 反序列化 JSON 为 List（向后兼容新旧格式）
            try {
                JsonNode node = objectMapper.readTree(campaign.getEnrollmentTargetIds());
                if (node.isArray()) {
                    // 旧格式：flat array，如 ["学院A", "2023"]
                    List<String> idList = objectMapper.convertValue(node, new TypeReference<List<String>>() {});
                    campaign.setEnrollmentTargetIdList(idList);
                } else if (node.isObject()) {
                    // 新格式：对象，如 { "collegeIds": [...], "gradeIds": [...] }
                    EnrollmentTargetConfig config = objectMapper.convertValue(node, EnrollmentTargetConfig.class);
                    // 前端期望收到的结构：单元素 List，包含完整 config Map
                    List<Object> wrapped = new ArrayList<>();
                    java.util.Map<String, Object> configMap = new java.util.HashMap<>();
                    configMap.put("collegeIds", config.getCollegeIds());
                    configMap.put("gradeIds", config.getGradeIds());
                    configMap.put("classIds", config.getClassIds());
                    wrapped.add(configMap);
                    campaign.setEnrollmentTargetIdList((List) wrapped);
                }
            } catch (Exception e) {
                log.error("反序列化 enrollmentTargetIds 失败", e);
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

        // 检查报名时间窗口（而非活动时间窗口）
        LocalDateTime now = LocalDateTime.now();
        if (campaign.getEnrollmentStartTime() != null && now.isBefore(campaign.getEnrollmentStartTime())) {
            return false; // 报名未开始
        }
        if (campaign.getEnrollmentEndTime() != null && now.isAfter(campaign.getEnrollmentEndTime())) {
            return false; // 报名已结束
        }

        // 检查报名范围
        if (campaign.getEnrollmentScope() == EnrollmentScope.ALL) {
            return true;
        }

        User user = userService.getUserById(userId);
        if (user == null) {
            return false;
        }

        // 解析报名范围配置（支持新旧格式）
        EnrollmentTargetConfig config = parseEnrollmentTargetConfig(campaign);
        if (config == null) {
            return true; // 无限制
        }

        // 根据不同的范围类型检查
        switch (campaign.getEnrollmentScope()) {
            case COLLEGE:
                return checkCollegeEnrollment(user, config);
            case GRADE:
                return checkGradeEnrollment(user, config);
            case CLASS:
                return checkClassEnrollment(user, config);
            case CUSTOM:
                // CUSTOM 已在创建时被拒绝，这里降级处理
                return false;
            default:
                return false;
        }
    }

    /**
     * 解析报名范围配置（兼容新旧格式）
     */
    private EnrollmentTargetConfig parseEnrollmentTargetConfig(CampaignActivity campaign) {
        if (!StringUtils.hasText(campaign.getEnrollmentTargetIds())) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(campaign.getEnrollmentTargetIds());
            if (node.isObject()) {
                // 新格式
                return objectMapper.convertValue(node, EnrollmentTargetConfig.class);
            } else if (node.isArray()) {
                // 旧格式：转为 flatList
                EnrollmentTargetConfig config = new EnrollmentTargetConfig();
                config.setFlatList(objectMapper.convertValue(node, new TypeReference<List<String>>() {}));
                return config;
            }
        } catch (Exception e) {
            log.error("解析 enrollmentTargetIds 失败", e);
        }
        return null;
    }

    /**
     * 检查用户是否符合学院（+ 可选年级）限制
     */
    private boolean checkCollegeEnrollment(User user, EnrollmentTargetConfig config) {
        SchoolStudent student = getSchoolStudent(user);
        if (student == null) {
            return false;
        }

        // 检查学院
        List<String> collegeIds = !config.getCollegeIds().isEmpty() ? config.getCollegeIds() : config.getFlatList();
        if (collegeIds.isEmpty() || !collegeIds.contains(student.getCollegeName())) {
            return false;
        }

        // 检查年级（可选）
        if (!config.getGradeIds().isEmpty() && !config.getGradeIds().contains(student.getGrade())) {
            return false;
        }

        return true;
    }

    /**
     * 检查用户是否符合年级限制
     */
    private boolean checkGradeEnrollment(User user, EnrollmentTargetConfig config) {
        SchoolStudent student = getSchoolStudent(user);
        if (student == null) {
            return false;
        }

        List<String> gradeIds = !config.getGradeIds().isEmpty() ? config.getGradeIds() : config.getFlatList();
        return !gradeIds.isEmpty() && gradeIds.contains(student.getGrade());
    }

    /**
     * 检查用户是否符合班级限制
     */
    private boolean checkClassEnrollment(User user, EnrollmentTargetConfig config) {
        SchoolStudent student = getSchoolStudent(user);
        if (student == null) {
            return false;
        }

        List<String> classIds = !config.getClassIds().isEmpty() ? config.getClassIds() : config.getFlatList();
        return !classIds.isEmpty() && classIds.contains(student.getClassName());
    }

    /**
     * 获取用户对应的学生信息
     */
    private SchoolStudent getSchoolStudent(User user) {
        if (user.getSchoolStudentId() != null) {
            return schoolStudentMapper.selectById(user.getSchoolStudentId());
        }
        if (StringUtils.hasText(user.getUsername())) {
            return schoolStudentMapper.selectByStudentId(user.getUsername());
        }
        return null;
    }

    /**
     * 辅助方法：Object 转 List<String>
     */
    private List<String> toStringList(Object obj) {
        if (obj == null) {
            return Collections.emptyList();
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return Collections.emptyList();
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

        // 检查报名时间窗口
        LocalDateTime now = LocalDateTime.now();
        if (campaign.getEnrollmentStartTime() != null && now.isBefore(campaign.getEnrollmentStartTime())) {
            restrictions.add("报名尚未开始");
        }
        if (campaign.getEnrollmentEndTime() != null && now.isAfter(campaign.getEnrollmentEndTime())) {
            restrictions.add("报名已结束");
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
