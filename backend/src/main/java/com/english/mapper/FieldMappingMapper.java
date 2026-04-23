package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.FieldMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字段映射配置Mapper接口
 */
@Mapper
public interface FieldMappingMapper extends BaseMapper<FieldMapping> {
}
