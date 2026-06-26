package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CampaignParticipation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 活动参与记录Mapper
 */
@Mapper
public interface CampaignParticipationMapper extends BaseMapper<CampaignParticipation> {

    /**
     * 批量统计多个活动的已报名人数（避免 N+1 查询）
     * 返回 List<Map>，每个 Map 含 campaignId 和 cnt
     */
    @Select({
        "<script>",
        "SELECT campaign_id AS campaignId, COUNT(*) AS cnt",
        "FROM campaign_participations",
        "WHERE enrollment_status = 'ENROLLED'",
        "  AND campaign_id IN",
        "  <foreach item='id' collection='campaignIds' open='(' separator=',' close=')'>#{id}</foreach>",
        "GROUP BY campaign_id",
        "</script>"
    })
    List<Map<String, Object>> countEnrolledByCampaignIds(@Param("campaignIds") List<Long> campaignIds);

    /**
     * 获取个人排名（支持班级和学院筛选，按名称过滤）
     */
    @Select({
        "<script>",
        "SELECT p.* FROM campaign_participations p",
        "LEFT JOIN users u ON p.user_id = u.id",
        "LEFT JOIN school_students ss ON u.school_student_id = ss.id",
        "WHERE p.campaign_id = #{campaignId}",
        "  AND p.enrollment_status = 'ENROLLED'",
        "  <if test='className != null and className != \"\"'>AND ss.class_name = #{className}</if>",
        "  <if test='collegeName != null and collegeName != \"\"'>AND ss.college_name = #{collegeName}</if>",
        "ORDER BY p.total_score DESC, p.completed_tasks DESC",
        "</script>"
    })
    List<CampaignParticipation> selectRankingWithFilters(
        @Param("campaignId") Long campaignId,
        @Param("className") String className,
        @Param("collegeName") String collegeName,
        Page<CampaignParticipation> page
    );
}
