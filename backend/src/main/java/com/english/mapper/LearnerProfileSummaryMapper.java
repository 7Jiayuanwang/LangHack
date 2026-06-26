package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.LearnerProfileSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LearnerProfileSummaryMapper extends BaseMapper<LearnerProfileSummary> {

    @Select("SELECT * FROM learner_profile_summaries " +
            "WHERE user_id = #{userId} AND summary_type = #{summaryType} " +
            "ORDER BY created_time DESC LIMIT 1")
    LearnerProfileSummary selectLatestByUserAndType(@Param("userId") Long userId,
                                                     @Param("summaryType") String summaryType);
}
