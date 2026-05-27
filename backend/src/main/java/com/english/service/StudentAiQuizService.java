package com.english.service;

import com.english.entity.StudentAiQuizAttempt;
import com.english.entity.StudentAiQuizRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 学生 AI 题做题记录服务
 *
 * 写入：saveAttempt 在单个事务内插入 1 行 attempt 汇总 + N 行 record 明细
 * 查询：
 *  - 学生端按 studentId 隔离
 *  - 管理员端跨用户分页
 */
public interface StudentAiQuizService {

    /**
     * 保存一次完整的 AI 题做题（attempt 汇总 + 明细）
     *
     * @param attempt 调用方构建好的 attempt 实体（不需要 id，由 DB 生成）
     * @param records 明细列表（attemptId 由 service 在插入 attempt 后回填）
     * @return 已落库的 attempt（含 id）
     */
    StudentAiQuizAttempt saveAttempt(StudentAiQuizAttempt attempt, List<StudentAiQuizRecord> records);

    /**
     * 学生端：查自己在某文章下的做题尝试列表
     */
    List<StudentAiQuizAttempt> listMyAttempts(Long studentId, Long textId);

    /**
     * 学生端：查自己某次尝试的明细（含权限校验 — attempt 必须属于该学生）
     * @return {attempt, records}
     */
    Map<String, Object> getMyAttemptDetail(Long studentId, Long attemptId);

    /**
     * 管理员端：分页查询做题记录（任一过滤条件可为 null）
     * @return {list, total, page, pageSize}
     */
    Map<String, Object> queryAttemptsForAdmin(Long studentId,
                                              Long textId,
                                              LocalDateTime startTime,
                                              LocalDateTime endTime,
                                              int page,
                                              int pageSize);

    /**
     * 管理员端：查任意学生某次尝试的明细
     */
    Map<String, Object> getAttemptDetailForAdmin(Long attemptId);
}
