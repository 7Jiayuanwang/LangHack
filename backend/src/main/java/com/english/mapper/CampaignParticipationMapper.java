package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.CampaignParticipation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 活动参与记录Mapper
 */
@Mapper
public interface CampaignParticipationMapper extends BaseMapper<CampaignParticipation> {
}
