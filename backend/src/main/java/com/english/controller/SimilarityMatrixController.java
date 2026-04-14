package com.english.controller;

import com.english.config.SimilarityMatrixScheduleTask;
import com.english.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 相似度矩阵手动触发接口（仅供开发测试使用）
 */
@RestController
@RequestMapping("/api/admin/similarity")
@PreAuthorize("hasRole('ADMIN') or hasRole('OPS')")
public class SimilarityMatrixController {

    private static final Logger log = LoggerFactory.getLogger(SimilarityMatrixController.class);

    @Autowired
    private SimilarityMatrixScheduleTask scheduleTask;

    /**
     * 手动触发全文相似度矩阵重算
     */
    @PostMapping("/recompute/text")
    public Result<String> recomputeText() {
        long start = System.currentTimeMillis();
        try {
            scheduleTask.recomputeTextSimilarityMatrix();
            long cost = System.currentTimeMillis() - start;
            return Result.success("文章相似度矩阵重算完成，耗时 " + cost + "ms");
        } catch (Exception e) {
            log.error("文章相似度矩阵重算失败", e);
            return Result.error("文章相似度矩阵重算失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发用户相似度矩阵重算
     */
    @PostMapping("/recompute/user")
    public Result<String> recomputeUser() {
        long start = System.currentTimeMillis();
        try {
            scheduleTask.recomputeUserSimilarityMatrix();
            long cost = System.currentTimeMillis() - start;
            return Result.success("用户相似度矩阵重算完成，耗时 " + cost + "ms");
        } catch (Exception e) {
            log.error("用户相似度矩阵重算失败", e);
            return Result.error("用户相似度矩阵重算失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发全部矩阵重算（文本 + 用户）
     */
    @PostMapping("/recompute/all")
    public Result<String> recomputeAll() {
        long start = System.currentTimeMillis();
        try {
            scheduleTask.recomputeAll();
            long cost = System.currentTimeMillis() - start;
            return Result.success("全部相似度矩阵重算完成，耗时 " + cost + "ms");
        } catch (Exception e) {
            log.error("全部相似度矩阵重算失败", e);
            return Result.error("全部相似度矩阵重算失败: " + e.getMessage());
        }
    }
}
