package com.english.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CampaignActivity;
import com.english.dto.Result;
import com.english.service.CampaignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 活动管理Controller（管理员端）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/campaigns")
@PreAuthorize("hasRole('ADMIN')")
public class CampaignController {

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 创建活动
     */
    @PostMapping
    public Result<CampaignActivity> createCampaign(@RequestBody CampaignActivity campaign) {
        try {
            CampaignActivity created = campaignService.createCampaign(campaign);
            return Result.success(created);
        } catch (Exception e) {
            log.error("创建活动失败", e);
            return Result.error("创建活动失败: " + e.getMessage());
        }
    }

    /**
     * 更新活动
     */
    @PutMapping("/{id}")
    public Result<CampaignActivity> updateCampaign(@PathVariable Long id, @RequestBody CampaignActivity campaign) {
        try {
            CampaignActivity updated = campaignService.updateCampaign(id, campaign);
            return Result.success(updated);
        } catch (Exception e) {
            log.error("更新活动失败", e);
            return Result.error("更新活动失败: " + e.getMessage());
        }
    }

    /**
     * 删除活动
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteCampaign(@PathVariable Long id) {
        try {
            campaignService.deleteCampaign(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除活动失败", e);
            return Result.error("删除活动失败: " + e.getMessage());
        }
    }

    /**
     * 获取活动详情
     */
    @GetMapping("/{id}")
    public Result<CampaignActivity> getCampaignById(@PathVariable Long id) {
        try {
            CampaignActivity campaign = campaignService.getCampaignById(id);
            if (campaign == null) {
                return Result.error("活动不存在");
            }
            return Result.success(campaign);
        } catch (Exception e) {
            log.error("获取活动详情失败", e);
            return Result.error("获取活动详情失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询活动列表
     */
    @GetMapping
    public Result<Page<CampaignActivity>> getCampaigns(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orderBy) {
        try {
            Map<String, Object> params = Map.of(
                "status", status != null ? status : "",
                "keyword", keyword != null ? keyword : "",
                "orderBy", orderBy != null ? orderBy : ""
            );
            Page<CampaignActivity> page = campaignService.getCampaigns(current, size, params);
            return Result.success(page);
        } catch (Exception e) {
            log.error("查询活动列表失败", e);
            return Result.error("查询活动列表失败: " + e.getMessage());
        }
    }

    /**
     * 启动活动
     */
    @PutMapping("/{id}/start")
    public Result<String> startCampaign(@PathVariable Long id) {
        try {
            campaignService.startCampaign(id);
            return Result.success("活动已启动");
        } catch (Exception e) {
            log.error("启动活动失败", e);
            return Result.error("启动活动失败: " + e.getMessage());
        }
    }

    /**
     * 结束活动
     */
    @PutMapping("/{id}/end")
    public Result<String> endCampaign(@PathVariable Long id) {
        try {
            campaignService.endCampaign(id);
            return Result.success("活动已结束");
        } catch (Exception e) {
            log.error("结束活动失败", e);
            return Result.error("结束活动失败: " + e.getMessage());
        }
    }

    /**
     * 获取学院列表（用于活动报名范围选择）
     */
    @GetMapping("/enrollment-options/colleges")
    public Result<List<Map<String, Object>>> getColleges() {
        try {
            String sql = "SELECT DISTINCT college_name as label, college_name as value " +
                        "FROM school_student WHERE college_name IS NOT NULL ORDER BY college_name";
            List<Map<String, Object>> colleges = jdbcTemplate.queryForList(sql);
            return Result.success(colleges);
        } catch (Exception e) {
            log.error("获取学院列表失败", e);
            return Result.error("获取学院列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取年级列表（用于活动报名范围选择）
     */
    @GetMapping("/enrollment-options/grades")
    public Result<List<Map<String, Object>>> getGrades() {
        try {
            String sql = "SELECT DISTINCT grade as label, grade as value " +
                        "FROM school_student WHERE grade IS NOT NULL ORDER BY grade DESC";
            List<Map<String, Object>> grades = jdbcTemplate.queryForList(sql);
            return Result.success(grades);
        } catch (Exception e) {
            log.error("获取年级列表失败", e);
            return Result.error("获取年级列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取班级列表（用于活动报名范围选择）
     */
    @GetMapping("/enrollment-options/classes")
    public Result<List<Map<String, Object>>> getClasses() {
        try {
            String sql = "SELECT DISTINCT class_name as label, class_name as value " +
                        "FROM school_student WHERE class_name IS NOT NULL ORDER BY class_name";
            List<Map<String, Object>> classes = jdbcTemplate.queryForList(sql);
            return Result.success(classes);
        } catch (Exception e) {
            log.error("获取班级列表失败", e);
            return Result.error("获取班级列表失败: " + e.getMessage());
        }
    }
}
