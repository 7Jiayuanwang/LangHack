package com.english.service;

import com.english.entity.FieldMapping;

import java.util.List;
import java.util.Map;

/**
 * 字段映射服务 - 负责字段映射配置的读取和字段转换
 */
public interface FieldMappingService {

    /**
     * 获取指定数据源的所有字段映射配置
     *
     * @param dataSource 数据源名称
     * @return 字段映射列表
     */
    List<FieldMapping> getMappingsByDataSource(String dataSource);

    /**
     * 转换单个字段值
     *
     * @param dataSource  数据源名称
     * @param sourceField 源字段名
     * @param value       源字段值
     * @return 转换后的值
     */
    Object convertField(String dataSource, String sourceField, Object value);

    /**
     * 对整条数据应用字段映射
     * 将源数据（key=源字段名）转换为目标数据（key=目标字段名），并执行转换器逻辑
     *
     * @param dataSource 数据源名称
     * @param sourceData 源数据（key=源字段名, value=字段值）
     * @return 转换后的数据（key=目标字段名, value=转换后的值）
     */
    Map<String, Object> applyMapping(String dataSource, Map<String, Object> sourceData);

    /**
     * 更新字段映射配置
     *
     * @param fieldMapping 更新后的字段映射对象
     * @return 更新后的字段映射
     */
    FieldMapping updateFieldMapping(FieldMapping fieldMapping);
}
