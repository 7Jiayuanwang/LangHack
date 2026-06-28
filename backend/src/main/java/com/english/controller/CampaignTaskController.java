package com.english.controller;

import com.english.entity.CampaignTask;
import com.english.dto.Result;
import com.english.service.CampaignTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 活动任务Controller（管理员端）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/campaigns/{campaignId}/tasks")
@PreAuthorize("hasRole('ADMIN')")
public class CampaignTaskController {

    @Autowired
    private CampaignTaskService campaignTaskService;

    /**
     * 创建任务
     */
    @PostMapping
    public Result<CampaignTask> createTask(@PathVariable Long campaignId, @RequestBody CampaignTask task) {
        try {
            CampaignTask created = campaignTaskService.createTask(campaignId, task);
            return Result.success(created);
        } catch (Exception e) {
            log.error("创建任务失败", e);
            return Result.error("创建任务失败: " + e.getMessage());
        }
    }

    /**
     * 更新任务
     */
    @PutMapping("/{taskId}")
    public Result<CampaignTask> updateTask(@PathVariable Long campaignId, @PathVariable Long taskId, @RequestBody CampaignTask task) {
        try {
            CampaignTask updated = campaignTaskService.updateTask(taskId, task);
            return Result.success(updated);
        } catch (Exception e) {
            log.error("更新任务失败", e);
            return Result.error("更新任务失败: " + e.getMessage());
        }
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{taskId}")
    public Result<String> deleteTask(@PathVariable Long campaignId, @PathVariable Long taskId) {
        try {
            campaignTaskService.deleteTask(taskId);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除任务失败", e);
            return Result.error("删除任务失败: " + e.getMessage());
        }
    }

    /**
     * 获取活动的所有任务
     */
    @GetMapping
    public Result<List<CampaignTask>> getTasks(@PathVariable Long campaignId) {
        try {
            List<CampaignTask> tasks = campaignTaskService.getTasksByCampaign(campaignId);
            return Result.success(tasks);
        } catch (Exception e) {
            log.error("获取任务列表失败", e);
            return Result.error("获取任务列表失败: " + e.getMessage());
        }
    }
}
