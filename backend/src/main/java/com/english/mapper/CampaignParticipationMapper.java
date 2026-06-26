package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
}
