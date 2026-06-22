package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.CampaignTask;
import com.english.enums.TaskType;
import com.english.mapper.CampaignTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 活动任务服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class CampaignTaskServiceImplTest {

    @Mock
    private CampaignTaskMapper taskMapper;

    @InjectMocks
    private CampaignTaskServiceImpl taskService;

    private CampaignTask testTask;

    @BeforeEach
    void setUp() {
        testTask = new CampaignTask();
        testTask.setId(1L);
        testTask.setCampaignId(100L);
        testTask.setTaskName("阅读任务");
        testTask.setTaskType(TaskType.READING_TIME);
        testTask.setTargetValue(60);
        testTask.setPoints(10);
        testTask.setIsRequired(true);
        testTask.setAutoCollect(true);
        testTask.setSortOrder(1);
    }

    @Test
    void testCreateTask_Success() {
        // Given
        when(taskMapper.insert(any(CampaignTask.class))).thenReturn(1);

        // When
        CampaignTask result = taskService.createTask(testTask);

        // Then
        assertNotNull(result);
        verify(taskMapper, times(1)).insert(any(CampaignTask.class));
    }

    @Test
    void testUpdateTask_Success() {
        // Given
        when(taskMapper.selectById(1L)).thenReturn(testTask);
        when(taskMapper.updateById(any(CampaignTask.class))).thenReturn(1);

        CampaignTask updateData = new CampaignTask();
        updateData.setTaskName("更新后的阅读任务");
        updateData.setTargetValue(90);

        // When
        CampaignTask result = taskService.updateTask(1L, updateData);

        // Then
        assertNotNull(result);
        verify(taskMapper, times(1)).updateById(any(CampaignTask.class));
    }

    @Test
    void testUpdateTask_NotFound() {
        // Given
        when(taskMapper.selectById(1L)).thenReturn(null);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            taskService.updateTask(1L, testTask);
        });
    }

    @Test
    void testDeleteTask_Success() {
        // Given
        when(taskMapper.selectById(1L)).thenReturn(testTask);
        when(taskMapper.deleteById(1L)).thenReturn(1);

        // When
        taskService.deleteTask(1L);

        // Then
        verify(taskMapper, times(1)).deleteById(1L);
    }

    @Test
    void testDeleteTask_NotFound() {
        // Given
        when(taskMapper.selectById(1L)).thenReturn(null);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            taskService.deleteTask(1L);
        });
    }

    @Test
    void testGetTaskById_Success() {
        // Given
        when(taskMapper.selectById(1L)).thenReturn(testTask);

        // When
        CampaignTask result = taskService.getTaskById(1L);

        // Then
        assertNotNull(result);
        assertEquals("阅读任务", result.getTaskName());
        verify(taskMapper, times(1)).selectById(1L);
    }

    @Test
    void testGetTasksByCampaign_Success() {
        // Given
        CampaignTask task2 = new CampaignTask();
        task2.setId(2L);
        task2.setCampaignId(100L);
        task2.setTaskName("单词学习任务");
        task2.setSortOrder(2);

        List<CampaignTask> tasks = Arrays.asList(testTask, task2);
        when(taskMapper.selectList(any(QueryWrapper.class))).thenReturn(tasks);

        // When
        List<CampaignTask> result = taskService.getTasksByCampaign(100L);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(taskMapper, times(1)).selectList(any(QueryWrapper.class));
    }

    @Test
    void testGetTasksByCampaign_Empty() {
        // Given
        when(taskMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList());

        // When
        List<CampaignTask> result = taskService.getTasksByCampaign(100L);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
