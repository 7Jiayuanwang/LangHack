package com.english.service;
import com.english.test.TestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.Assignment;
import com.english.entity.Unit;
import com.english.exception.BusinessException;
import com.english.mapper.AssignmentMapper;
import com.english.mapper.TextMapper;
import com.english.mapper.TextUnitMapper;
import com.english.mapper.UnitMapper;
import com.english.service.impl.UnitServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UnitServiceImpl.
 *
 * Key observations from source code:
 * - UnitServiceImpl extends ServiceImpl<UnitMapper, Unit>
 * - Injects AssignmentMapper and TextMapper
 * - getUnitsByPage() parses page/size/courseId/keyword from a Map
 * - deleteUnit() validates existence, checks for active assignments, soft-deletes
 *   assignments/texts, clears FK references, then removeById()
 */
@ExtendWith(MockitoExtension.class)
class UnitServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Unit.class);
    }

    @InjectMocks
    private UnitServiceImpl unitService;

    @Mock
    private UnitMapper baseMapper;

    @Mock
    private AssignmentMapper assignmentMapper;

    @Mock
    private TextMapper textMapper;

    @Mock
    private TextUnitMapper textUnitMapper;

    private Unit testUnit;

    @BeforeEach
    void setUp() {
        testUnit = new Unit();
        testUnit.setId(1L);
        testUnit.setName("Unit 1 - Greetings");
        testUnit.setCourseId(10L);
        testUnit.setDescription("Basic greetings");
        testUnit.setOrderNum(1);
        testUnit.setCreatedTime(LocalDateTime.now());
        testUnit.setUpdatedTime(LocalDateTime.now());
    }

    // ==================== getUnitsByPage tests ====================

    @Test
    void getUnitsByPage_defaultParams_returnsPage() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        // page/size default to 1/10

        Page<Unit> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(testUnit));
        mockPage.setTotal(1);

        when(baseMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        // Act
        Map<String, Object> result = unitService.getUnitsByPage(params);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.get("total"));
        assertNotNull(result.get("records"));
    }

    @Test
    void getUnitsByPage_withCourseIdAndKeyword_filtersCorrectly() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("size", 5);
        params.put("courseId", 10L);
        params.put("keyword", "greeting");

        Page<Unit> mockPage = new Page<>(1, 5);
        mockPage.setRecords(List.of(testUnit));
        mockPage.setTotal(1);

        when(baseMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        // Act
        Map<String, Object> result = unitService.getUnitsByPage(params);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("records"));
    }

    @Test
    void getUnitsByPage_invalidCourseIdFormat_throwsBusinessException() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("courseId", "not-a-number");

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> unitService.getUnitsByPage(params));
        assertEquals("courseId 格式错误", exception.getMessage());
    }

    @Test
    void getUnitsByPage_negativePage_clampedToOne() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("page", -5);
        params.put("size", -3);

        Page<Unit> mockPage = new Page<>(1, 1);
        mockPage.setRecords(Collections.emptyList());
        mockPage.setTotal(0);

        when(baseMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        // Act
        Map<String, Object> result = unitService.getUnitsByPage(params);

        // Assert: should not throw, params are clamped
        assertNotNull(result);
    }

    // ==================== getUnitsByCourseId tests ====================

    @Test
    void getUnitsByCourseId_returnsUnitsOrdered() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(testUnit));

        // Act
        List<Unit> result = unitService.getUnitsByCourseId(10L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Unit 1 - Greetings", result.get(0).getName());
    }

    @Test
    void getUnitsByCourseId_noUnits_returnsEmptyList() {
        // Arrange
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        // Act
        List<Unit> result = unitService.getUnitsByCourseId(999L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getUnitById tests ====================

    @Test
    void getUnitById_found_returnsUnit() {
        // Arrange
        when(baseMapper.selectById(1L)).thenReturn(testUnit);

        // Act
        Unit result = unitService.getUnitById(1L);

        // Assert
        assertNotNull(result);
        assertEquals("Unit 1 - Greetings", result.getName());
    }

    @Test
    void getUnitById_notFound_returnsNull() {
        // Arrange
        when(baseMapper.selectById(999L)).thenReturn(null);

        // Act
        Unit result = unitService.getUnitById(999L);

        // Assert
        assertNull(result);
    }

    // ==================== createUnit tests ====================

    @Test
    void createUnit_success_insertsViaBaseMapper() {
        // Arrange
        Unit newUnit = new Unit();
        newUnit.setName("New Unit");
        newUnit.setCourseId(10L);

        when(baseMapper.insert(any(Unit.class))).thenReturn(1);

        // Act
        unitService.createUnit(newUnit);

        // Assert
        verify(baseMapper).insert(any(Unit.class));
    }

    // ==================== updateUnit tests ====================

    @Test
    void updateUnit_success_updatesViaBaseMapper() {
        // Arrange
        Unit updateUnit = new Unit();
        updateUnit.setId(1L);
        updateUnit.setName("Updated Name");

        when(baseMapper.updateById(any(Unit.class))).thenReturn(1);

        // Act
        unitService.updateUnit(updateUnit);

        // Assert
        verify(baseMapper).updateById(updateUnit);
    }

    // ==================== deleteUnit tests ====================

    @Test
    void deleteUnit_success_fullCascadeCleanup() {
        // Arrange
        when(baseMapper.selectById(1L)).thenReturn(testUnit);
        // No active assignments
        when(assignmentMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        // Soft-delete inactive assignments
        when(assignmentMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);
        // Soft-delete texts
        when(textMapper.softDeleteByUnitId(1L)).thenReturn(3);
        // Clear text unit references
        when(textMapper.clearUnitReference(1L)).thenReturn(3);
        // removeById (Unit has no @TableLogic, so this is physical delete)
        when(baseMapper.deleteById(1L)).thenReturn(1);

        // Act
        unitService.deleteUnit(1L);

        // Assert: verify full cascade
        verify(assignmentMapper).selectCount(any(QueryWrapper.class));
        verify(assignmentMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
        verify(textMapper).softDeleteByUnitId(1L);
        verify(textMapper).clearUnitReference(1L);
        verify(baseMapper).deleteById(1L);
    }

    @Test
    void deleteUnit_unitNotFound_throwsBusinessException() {
        // Arrange
        when(baseMapper.selectById(999L)).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> unitService.deleteUnit(999L));
        assertEquals("单元不存在", exception.getMessage());
    }

    @Test
    void deleteUnit_hasActiveAssignments_throwsBusinessException() {
        // Arrange
        when(baseMapper.selectById(1L)).thenReturn(testUnit);
        when(assignmentMapper.selectCount(any(QueryWrapper.class))).thenReturn(3L);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> unitService.deleteUnit(1L));
        assertEquals("该单元下有 3 个活跃的任务，请先关闭或删除这些任务后再删除单元", exception.getMessage());
    }

    @Test
    void deleteUnit_zeroActiveAssignments_proceedsWithDeletion() {
        // Arrange
        when(baseMapper.selectById(1L)).thenReturn(testUnit);
        when(assignmentMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(assignmentMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);
        when(textMapper.softDeleteByUnitId(1L)).thenReturn(0);
        when(textMapper.clearUnitReference(1L)).thenReturn(0);
        when(baseMapper.deleteById(1L)).thenReturn(1);

        // Act: should not throw
        assertDoesNotThrow(() -> unitService.deleteUnit(1L));

        // Assert
        verify(baseMapper).deleteById(1L);
    }

    @Test
    void deleteUnit_nullActiveCount_proceedsWithDeletion() {
        // Arrange: assignmentMapper.selectCount returns null (edge case)
        when(baseMapper.selectById(1L)).thenReturn(testUnit);
        when(assignmentMapper.selectCount(any(QueryWrapper.class))).thenReturn(null);
        when(assignmentMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);
        when(textMapper.softDeleteByUnitId(1L)).thenReturn(0);
        when(textMapper.clearUnitReference(1L)).thenReturn(0);
        when(baseMapper.deleteById(1L)).thenReturn(1);

        // Act: null count is treated as 0, should proceed
        assertDoesNotThrow(() -> unitService.deleteUnit(1L));
        verify(baseMapper).deleteById(1L);
    }
}
