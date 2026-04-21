package com.english.config;

import com.english.mapper.SimilarityMatrixMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 相似度矩阵定时重算任务（Shadow Table 零停机策略）
 * <p>
 * 每日凌晨 3:00 执行：
 *   1. TRUNCATE _tmp 表
 *   2. INSERT 新计算数据到 _tmp
 *   3. 原子 RENAME TABLE 切换
 *   4. DROP 旧表 + 重建空 _tmp 备下次使用
 * <p>
 * 注意：本方法不能标 @Transactional —— 内部使用 TRUNCATE/RENAME/DROP/CREATE，
 *       MySQL DDL 隐式提交事务，声明式事务无效且会破坏回滚语义。
 *       每一步失败会抛出异常，但已执行的 DDL 无法回滚（这是 MySQL 的特性，不是 bug）。
 */
@Component
public class SimilarityMatrixScheduleTask {

    private static final Logger log = LoggerFactory.getLogger(SimilarityMatrixScheduleTask.class);

    @Autowired
    private SimilarityMatrixMapper similarityMatrixMapper;

    /**
     * 每日凌晨 3:00 重算全量相似度矩阵。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledRecompute() {
        log.info("[SimilarityMatrix] 定时任务触发：开始全量重算...");
        recomputeAll();
    }

    public void recomputeTextSimilarityMatrix() {
        log.info("[SimilarityMatrix] 开始重算文章相似度矩阵...");

        log.info("[SimilarityMatrix] Step1: 清空 text_similarity_matrix_tmp...");
        similarityMatrixMapper.truncateTmpText();

        log.info("[SimilarityMatrix] Step2: 计算并插入新相似度数据到 tmp 表...");
        int insertedCount = similarityMatrixMapper.recomputeTextSimilarityIntoTmp();
        log.info("[SimilarityMatrix] 插入 {} 条记录到 tmp 表", insertedCount);

        if (insertedCount == 0) {
            log.warn("[SimilarityMatrix] tmp 表未插入任何数据，跳过切换");
            return;
        }

        log.info("[SimilarityMatrix] Step3: 执行原子 RENAME TABLE 切换...");
        similarityMatrixMapper.atomicRenameToShadowText();
        log.info("[SimilarityMatrix] RENAME TABLE 执行成功");

        log.info("[SimilarityMatrix] Step4: 删除旧表并重建空 tmp 表...");
        similarityMatrixMapper.dropOldAndRecreateTmpText();

        log.info("[SimilarityMatrix] ✅ 文章相似度矩阵重算完成，共 {} 条记录", insertedCount);
    }

    public void recomputeUserSimilarityMatrix() {
        log.info("[SimilarityMatrix] 开始重算用户相似度矩阵...");

        log.info("[SimilarityMatrix] Step1: 清空 user_similarity_matrix_tmp...");
        similarityMatrixMapper.truncateTmpUser();

        log.info("[SimilarityMatrix] Step2: 计算并插入新相似度数据到 tmp 表...");
        int insertedCount = similarityMatrixMapper.recomputeUserSimilarityIntoTmp();
        log.info("[SimilarityMatrix] 插入 {} 条记录到 tmp 表", insertedCount);

        if (insertedCount == 0) {
            log.warn("[SimilarityMatrix] tmp 表未插入任何数据，跳过切换");
            return;
        }

        log.info("[SimilarityMatrix] Step3: 执行原子 RENAME TABLE 切换...");
        similarityMatrixMapper.atomicRenameToShadowUser();
        log.info("[SimilarityMatrix] RENAME TABLE 执行成功");

        log.info("[SimilarityMatrix] Step4: 删除旧表并重建空 tmp 表...");
        similarityMatrixMapper.dropOldAndRecreateTmpUser();

        log.info("[SimilarityMatrix] ✅ 用户相似度矩阵重算完成，共 {} 条记录", insertedCount);
    }

    public void recomputeAll() {
        recomputeTextSimilarityMatrix();
        recomputeUserSimilarityMatrix();
    }
}
