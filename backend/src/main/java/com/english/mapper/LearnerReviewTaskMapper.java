package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.LearnerReviewTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LearnerReviewTaskMapper extends BaseMapper<LearnerReviewTask> {

    /** Ensure a row exists before taking a row lock; duplicate submissions remain idempotent. */
    @Insert("INSERT INTO learner_review_tasks "
            + "(user_id, text_id, last_attempt_id, repeat_count, last_correct, last_total, "
            + "last_attempt_time, due_at) VALUES "
            + "(#{userId}, #{textId}, #{attemptId}, 1, #{correct}, #{total}, #{submitted}, #{firstDue}) "
            + "ON DUPLICATE KEY UPDATE deleted = 0")
    int ensureTask(@Param("userId") Long userId, @Param("textId") Long textId,
                   @Param("attemptId") Long attemptId, @Param("correct") Integer correct,
                   @Param("total") Integer total, @Param("submitted") LocalDateTime submitted,
                   @Param("firstDue") LocalDateTime firstDue);

    @Select("SELECT r.* FROM learner_review_tasks r "
            + "JOIN texts t ON t.id = r.text_id AND COALESCE(t.deleted, 0) = 0 "
            + "WHERE r.user_id = #{userId} AND r.deleted = 0 AND r.due_at <= #{now} "
            + "ORDER BY (r.last_correct * 100 < r.last_total * 80) DESC, "
            + "r.due_at DESC, r.id DESC LIMIT #{limit}")
    List<LearnerReviewTask> listDue(@Param("userId") Long userId,
                                    @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM learner_review_tasks r "
            + "JOIN texts t ON t.id = r.text_id AND COALESCE(t.deleted, 0) = 0 "
            + "WHERE r.user_id = #{userId} AND r.deleted = 0 AND r.due_at <= #{now}")
    long countDue(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
