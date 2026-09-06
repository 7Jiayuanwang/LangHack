package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.PastPaperAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface PastPaperAttemptMapper extends BaseMapper<PastPaperAttempt> {
    @Select("SELECT COUNT(DISTINCT paper_key) FROM past_paper_attempts " +
            "WHERE student_id = #{studentId} AND submitted_time >= #{since} AND submitted_time < #{until}")
    int countDistinctSubmittedPapers(@Param("studentId") Long studentId,
                                     @Param("since") LocalDateTime since,
                                     @Param("until") LocalDateTime until);
}
