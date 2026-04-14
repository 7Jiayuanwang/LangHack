package com.english.config;

import com.english.mapper.SimilarityMatrixMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 相似度矩阵定时重算任务
 * <p>
 * 每日凌晨 3:00 执行，使用 Shadow Table 策略实现零停机更新：
 * 1. 清空 _tmp 表
 * 2. 插入新计算的相似度数据到 _tmp 表
 * 3. 原子 RENAME TABLE 切换（新表 ↔ 旧表）
 * 4. 删除旧表并重建空 _tmp 表备下次使用
 */
@Component
public class SimilarityMatrixScheduleTask {

    private static final Logger log = LoggerFactory.getLogger(SimilarityMatrixScheduleTask.class);

    @Autowired
    private SimilarityMatrixMapper similarityMatrixMapper;

/**
     * 每日凌晨 3:00 执行
     * 开发测试时可注释掉，手动调用 recomputeTextSimilarityMatrix() / recomputeUserSimilarityMatrix()
     */
    // @Scheduled(cron = "0 0 3 * * ?")

    @Transactional(rollbackFor = Exception.class)
    public void recomputeTextSimilarityMatrix() {
        log.info("[SimilarityMatrix] 开始重算文章相似度矩阵...");

        // Step 1: 清空 tmp 表
        log.info("[SimilarityMatrix] Step1: 清空 text_similarity_matrix_tmp...");
        similarityMatrixMapper.truncateTmpText();

        // Step 2: 插入新数据到 tmp 表
        log.info("[SimilarityMatrix] Step2: 计算并插入新相似度数据到 tmp 表...");
        AtomicInteger insertedCount = new AtomicInteger(0);
        try {
            // MySQL JDBC 对 INSERT ... WITH ... SELECT 返回的影响行数可能为 0，
            // 故插入后主动查 count，不再依赖 INSERT 返回值
            similarityMatrixMapper.recomputeTextSimilarityIntoTmp();
            int actualCount = similarityMatrixMapper.countTmpText();
            insertedCount.set(actualCount);
            log.info("[SimilarityMatrix] 插入 {} 条记录到 tmp 表", actualCount);
        } catch (Exception e) {
            log.error("[SimilarityMatrix] 插入文本相似度数据失败", e);
            throw e;
        }

        if (insertedCount.get() == 0) {
            log.warn("[SimilarityMatrix] tmp 表未插入任何数据，跳过切换");
            return;
        }

        // Step 3: 原子切换 RENAME TABLE
        log.info("[SimilarityMatrix] Step3: 执行原子 RENAME TABLE 切换...");
        try {
            similarityMatrixMapper.atomicRenameToShadowText();
            log.info("[SimilarityMatrix] RENAME TABLE 执行成功");
        } catch (Exception e) {
            log.error("[SimilarityMatrix] RENAME TABLE 失败", e);
            throw e;
        }

        // Step 4: 删除旧表并重建空 tmp 表
        log.info("[SimilarityMatrix] Step4: 删除旧表 text_similarity_matrix_old 并重建空 tmp 表...");
        try {
            similarityMatrixMapper.dropOldAndRecreateTmpText();
            log.info("[SimilarityMatrix] 旧表清理完成");
        } catch (Exception e) {
            log.error("[SimilarityMatrix] 清理旧表失败", e);
            throw e;
        }

        log.info("[SimilarityMatrix] ✅ 文章相似度矩阵重算完成，共 {} 条记录", insertedCount.get());
    }

    /**
     * 重算用户相似度矩阵（Shadow Table 策略）
     */
    @Transactional(rollbackFor = Exception.class)
    public void recomputeUserSimilarityMatrix() {
        log.info("[SimilarityMatrix] 开始重算用户相似度矩阵...");

        // Step 1: 清空 tmp 表
        log.info("[SimilarityMatrix] Step1: 清空 user_similarity_matrix_tmp...");
        similarityMatrixMapper.truncateTmpUser();

        // Step 2: 插入新数据到 tmp 表
        log.info("[SimilarityMatrix] Step2: 计算并插入新相似度数据到 tmp 表...");
        AtomicInteger insertedCount = new AtomicInteger(0);
        try {
            similarityMatrixMapper.recomputeUserSimilarityIntoTmp();
            int actualCount = similarityMatrixMapper.countTmpUser();
            insertedCount.set(actualCount);
            log.info("[SimilarityMatrix] 插入 {} 条记录到 tmp 表", actualCount);
        } catch (Exception e) {
            log.error("[SimilarityMatrix] 插入用户相似度数据失败", e);
            throw e;
        }

        if (insertedCount.get() == 0) {
            log.warn("[SimilarityMatrix] tmp 表未插入任何数据，跳过切换");
            return;
        }

        // Step 3: 原子切换 RENAME TABLE
        log.info("[SimilarityMatrix] Step3: 执行原子 RENAME TABLE 切换...");
        try {
            similarityMatrixMapper.atomicRenameToShadowUser();
            log.info("[SimilarityMatrix] RENAME TABLE 执行成功");
        } catch (Exception e) {
            log.error("[SimilarityMatrix] RENAME TABLE 失败", e);
            throw e;
        }

        // Step 4: 删除旧表并重建空 tmp 表
        log.info("[SimilarityMatrix] Step4: 删除旧表 user_similarity_matrix_old 并重建空 tmp 表...");
        try {
            similarityMatrixMapper.dropOldAndRecreateTmpUser();
            log.info("[SimilarityMatrix] 旧表清理完成");
        } catch (Exception e) {
            log.error("[SimilarityMatrix] 清理旧表失败", e);
            throw e;
        }

        log.info("[SimilarityMatrix] ✅ 用户相似度矩阵重算完成，共 {} 条记录", insertedCount.get());
    }

    /**
     * 同时重算两个矩阵（供外部一键调用）
     */
    public void recomputeAll() {
        recomputeTextSimilarityMatrix();
        recomputeUserSimilarityMatrix();
    }
}
