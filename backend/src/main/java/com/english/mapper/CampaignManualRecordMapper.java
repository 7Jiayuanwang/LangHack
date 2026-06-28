package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.CampaignManualRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 手动数据录入Mapper
 */
@Mapper
public interface CampaignManualRecordMapper extends BaseMapper<CampaignManualRecord> {
}
