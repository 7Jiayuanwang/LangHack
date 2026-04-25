package com.english.config;

import com.english.mapper.SimilarityMatrixMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SimilarityMatrixScheduleTask.
 *
 * Shadow Table 策略流程：
 *   Step1: TRUNCATE TABLE *_matrix_tmp
 *   Step2: INSERT ... CTE 余弦相似度 → tmp 表
 *   Step3: RENAME TABLE 原子切换
 *   Step4: DROP old + CREATE TABLE tmp
 */
@ExtendWith(MockitoExtension.class)
class SimilarityMatrixScheduleTaskTest {

    @Mock
    private SimilarityMatrixMapper similarityMatrixMapper;

    @InjectMocks
    private SimilarityMatrixScheduleTask scheduleTask;

    @BeforeEach
    void setUp() {
        // similarityMatrixMapper 已由 @InjectMocks 注入
    }

    // ══════════════════════════════════════════════════════════════
    // recomputeTextSimilarityMatrix() 正常流程
    // ══════════════════════════════════════════════════════════════

    @Test
    void recomputeTextSimilarityMatrix_normalFlow_truncatesTmpAndInsertsAndRenames() {
        // Step1: truncateTmpText 返回 0（TRUNCATE 不返回影响行数，MyBatis 返回 0）
        when(similarityMatrixMapper.truncateTmpText()).thenReturn(0);
        // Step2: 插入 150 条记录
        when(similarityMatrixMapper.recomputeTextSimilarityIntoTmp()).thenReturn(150);
        // Step3: RENAME 成功
        when(similarityMatrixMapper.atomicRenameToShadowText()).thenReturn(0);
        // Step4: DROP + CREATE 成功
        when(similarityMatrixMapper.dropOldAndRecreateTmpText()).thenReturn(0);

        // 执行
        scheduleTask.recomputeTextSimilarityMatrix();

        // 验证调用顺序和次数
        verify(similarityMatrixMapper).truncateTmpText();
        verify(similarityMatrixMapper).recomputeTextSimilarityIntoTmp();
        verify(similarityMatrixMapper).atomicRenameToShadowText();
        verify(similarityMatrixMapper).dropOldAndRecreateTmpText();
    }

    @Test
    void recomputeTextSimilarityMatrix_zeroInserted_skipsRename() {
        when(similarityMatrixMapper.truncateTmpText()).thenReturn(0);
        when(similarityMatrixMapper.recomputeTextSimilarityIntoTmp()).thenReturn(0);

        scheduleTask.recomputeTextSimilarityMatrix();

        // 不应执行 RENAME 和 DROP/CREATE
        verify(similarityMatrixMapper, never()).atomicRenameToShadowText();
        verify(similarityMatrixMapper, never()).dropOldAndRecreateTmpText();
    }

    @Test
    void recomputeTextSimilarityMatrix_insertThrows_propagatesException() {
        when(similarityMatrixMapper.truncateTmpText()).thenReturn(0);
        when(similarityMatrixMapper.recomputeTextSimilarityIntoTmp())
                .thenThrow(new RuntimeException("SQL syntax error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> scheduleTask.recomputeTextSimilarityMatrix());

        assertEquals("SQL syntax error", ex.getMessage());
        // 事务回滚：后续步骤不应执行
        verify(similarityMatrixMapper, never()).atomicRenameToShadowText();
    }

    @Test
    void recomputeTextSimilarityMatrix_renameThrows_propagatesException() {
        when(similarityMatrixMapper.truncateTmpText()).thenReturn(0);
        when(similarityMatrixMapper.recomputeTextSimilarityIntoTmp()).thenReturn(50);
        when(similarityMatrixMapper.atomicRenameToShadowText())
                .thenThrow(new RuntimeException("RENAME failed"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> scheduleTask.recomputeTextSimilarityMatrix());

        assertEquals("RENAME failed", ex.getMessage());
        verify(similarityMatrixMapper, never()).dropOldAndRecreateTmpText();
    }

    @Test
    void recomputeTextSimilarityMatrix_dropOldThrows_propagatesException() {
        when(similarityMatrixMapper.truncateTmpText()).thenReturn(0);
        when(similarityMatrixMapper.recomputeTextSimilarityIntoTmp()).thenReturn(50);
        when(similarityMatrixMapper.atomicRenameToShadowText()).thenReturn(0);
        when(similarityMatrixMapper.dropOldAndRecreateTmpText())
                .thenThrow(new RuntimeException("DROP TABLE failed"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> scheduleTask.recomputeTextSimilarityMatrix());

        assertEquals("DROP TABLE failed", ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════
    // recomputeUserSimilarityMatrix() 正常流程
    // ══════════════════════════════════════════════════════════════

    @Test
    void recomputeUserSimilarityMatrix_normalFlow_truncatesTmpAndInsertsAndRenames() {
        when(similarityMatrixMapper.truncateTmpUser()).thenReturn(0);
        when(similarityMatrixMapper.recomputeUserSimilarityIntoTmp()).thenReturn(200);
        when(similarityMatrixMapper.atomicRenameToShadowUser()).thenReturn(0);
        when(similarityMatrixMapper.dropOldAndRecreateTmpUser()).thenReturn(0);

        scheduleTask.recomputeUserSimilarityMatrix();

        verify(similarityMatrixMapper).truncateTmpUser();
        verify(similarityMatrixMapper).recomputeUserSimilarityIntoTmp();
        verify(similarityMatrixMapper).atomicRenameToShadowUser();
        verify(similarityMatrixMapper).dropOldAndRecreateTmpUser();
    }

    @Test
    void recomputeUserSimilarityMatrix_zeroInserted_skipsRename() {
        when(similarityMatrixMapper.truncateTmpUser()).thenReturn(0);
        when(similarityMatrixMapper.recomputeUserSimilarityIntoTmp()).thenReturn(0);

        scheduleTask.recomputeUserSimilarityMatrix();

        verify(similarityMatrixMapper, never()).atomicRenameToShadowUser();
        verify(similarityMatrixMapper, never()).dropOldAndRecreateTmpUser();
    }

    @Test
    void recomputeUserSimilarityMatrix_insertThrows_propagatesException() {
        when(similarityMatrixMapper.truncateTmpUser()).thenReturn(0);
        when(similarityMatrixMapper.recomputeUserSimilarityIntoTmp())
                .thenThrow(new RuntimeException(" CTE error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> scheduleTask.recomputeUserSimilarityMatrix());

        assertEquals(" CTE error", ex.getMessage());
        verify(similarityMatrixMapper, never()).atomicRenameToShadowUser();
    }

    @Test
    void recomputeUserSimilarityMatrix_renameThrows_propagatesException() {
        when(similarityMatrixMapper.truncateTmpUser()).thenReturn(0);
        when(similarityMatrixMapper.recomputeUserSimilarityIntoTmp()).thenReturn(80);
        when(similarityMatrixMapper.atomicRenameToShadowUser())
                .thenThrow(new RuntimeException("RENAME failed"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> scheduleTask.recomputeUserSimilarityMatrix());

        assertEquals("RENAME failed", ex.getMessage());
        verify(similarityMatrixMapper, never()).dropOldAndRecreateTmpUser();
    }

    @Test
    void recomputeUserSimilarityMatrix_dropOldThrows_propagatesException() {
        when(similarityMatrixMapper.truncateTmpUser()).thenReturn(0);
        when(similarityMatrixMapper.recomputeUserSimilarityIntoTmp()).thenReturn(80);
        when(similarityMatrixMapper.atomicRenameToShadowUser()).thenReturn(0);
        when(similarityMatrixMapper.dropOldAndRecreateTmpUser())
                .thenThrow(new RuntimeException("DROP failed"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> scheduleTask.recomputeUserSimilarityMatrix());

        assertEquals("DROP failed", ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════
    // recomputeAll() — 顺序执行两个矩阵
    // ══════════════════════════════════════════════════════════════

    @Test
    void recomputeAll_textFirstThenUser_bothSucceed() {
        // Text 矩阵
        when(similarityMatrixMapper.truncateTmpText()).thenReturn(0);
        when(similarityMatrixMapper.recomputeTextSimilarityIntoTmp()).thenReturn(100);
        when(similarityMatrixMapper.atomicRenameToShadowText()).thenReturn(0);
        when(similarityMatrixMapper.dropOldAndRecreateTmpText()).thenReturn(0);
        // User 矩阵
        when(similarityMatrixMapper.truncateTmpUser()).thenReturn(0);
        when(similarityMatrixMapper.recomputeUserSimilarityIntoTmp()).thenReturn(300);
        when(similarityMatrixMapper.atomicRenameToShadowUser()).thenReturn(0);
        when(similarityMatrixMapper.dropOldAndRecreateTmpUser()).thenReturn(0);

        scheduleTask.recomputeAll();

        // Text 在前，User 在后
        var inOrder = inOrder(similarityMatrixMapper);
        inOrder.verify(similarityMatrixMapper).recomputeTextSimilarityIntoTmp();
        inOrder.verify(similarityMatrixMapper).recomputeUserSimilarityIntoTmp();
    }

    @Test
    void recomputeAll_textFails_userNotExecuted() {
        when(similarityMatrixMapper.truncateTmpText()).thenReturn(0);
        when(similarityMatrixMapper.recomputeTextSimilarityIntoTmp())
                .thenThrow(new RuntimeException("Text failed"));

        assertThrows(RuntimeException.class, () -> scheduleTask.recomputeAll());

        verify(similarityMatrixMapper, never()).recomputeUserSimilarityIntoTmp();
    }
}
