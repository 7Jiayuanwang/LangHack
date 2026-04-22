package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.FieldMapping;
import com.english.exception.BusinessException;
import com.english.mapper.FieldMappingMapper;
import com.english.service.FieldMappingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 字段映射服务实现 - 包含4种转换器（DATE/BOOLEAN/SPLIT/JSON）
 */
@Service
public class FieldMappingServiceImpl implements FieldMappingService {

    private static final Logger logger = LoggerFactory.getLogger(FieldMappingServiceImpl.class);

    private final FieldMappingMapper fieldMappingMapper;
    private final ObjectMapper objectMapper;

    public FieldMappingServiceImpl(FieldMappingMapper fieldMappingMapper, ObjectMapper objectMapper) {
        this.fieldMappingMapper = fieldMappingMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<FieldMapping> getMappingsByDataSource(String dataSource) {
        QueryWrapper<FieldMapping> query = new QueryWrapper<>();
        query.eq("data_source", dataSource)
             .eq("is_active", 1)
             .orderByAsc("sort_order");
        return fieldMappingMapper.selectList(query);
    }

    @Override
    public Object convertField(String dataSource, String sourceField, Object value) {
        if (value == null) {
            return null;
        }

        FieldMapping mapping = findActiveMapping(dataSource, sourceField);
        if (mapping == null) {
            // 无映射配置，原样返回
            return value;
        }

        String converter = mapping.getConverter();
        if (converter == null || "NONE".equals(converter)) {
            return convertType(value, mapping.getTargetType(), mapping.getConverterConfig());
        }

        switch (converter) {
            case "DATE":
                return convertDate(value, mapping.getConverterConfig());
            case "BOOLEAN":
                return convertBoolean(value, mapping.getConverterConfig());
            case "SPLIT":
                return convertSplit(value, mapping.getConverterConfig());
            case "JSON":
                return convertJson(value, mapping.getConverterConfig());
            default:
                logger.warn("Unknown converter type: {} for field: {}", converter, sourceField);
                return value;
        }
    }

    @Override
    public Map<String, Object> applyMapping(String dataSource, Map<String, Object> sourceData) {
        if (sourceData == null || sourceData.isEmpty()) {
            return new HashMap<>();
        }

        List<FieldMapping> mappings = getMappingsByDataSource(dataSource);
        if (mappings.isEmpty()) {
            logger.warn("No field mappings found for data source: {}", dataSource);
            return new HashMap<>(sourceData);
        }

        // Build lookup: sourceField(lowercase) -> mapping
        // CDSP 字段大小写按端点区分（3 个 UPPERCASE + 6 个 lowercase），V12 seed 按 PDF 原文保留；
        // 此处做大小写不敏感的查表以兼容 API 返回任意大小写
        Map<String, FieldMapping> mappingLookup = mappings.stream()
                .collect(Collectors.toMap(
                        m -> m.getSourceField() == null ? "" : m.getSourceField().toLowerCase(Locale.ROOT),
                        m -> m,
                        (a, b) -> a,  // duplicate key: keep first
                        LinkedHashMap::new
                ));

        // 预构建 sourceData 的 lowercase key 集合，用于必填字段缺失检测（case-insensitive）
        // 中文 key 额外翻译为英文别名一并加入，避免中文 key 被误判为"源数据缺失"
        Set<String> sourceDataKeysLower = new HashSet<>();
        for (String key : sourceData.keySet()) {
            if (key == null) {
                continue;
            }
            sourceDataKeysLower.add(normalizeFieldKey(key));
            String alias = SchoolDataFieldAlias.resolveAlias(dataSource, key);
            if (alias != null) {
                sourceDataKeysLower.add(normalizeFieldKey(alias));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : sourceData.entrySet()) {
            String sourceField = entry.getKey();
            Object value = entry.getValue();

            // 1. 先按原字段 lowercase 查（英文别名或大小写变体）
            String lookupKey = normalizeFieldKey(sourceField);
            FieldMapping mapping = mappingLookup.get(lookupKey);

            // 2. 未命中兜底：中文参数名 → 英文别名 lowercase
            //    CDSP API 实测返回中文 key（"学号"/"开课计划id"等），与 V12 seed 英文别名不一致
            if (mapping == null && sourceField != null) {
                String alias = SchoolDataFieldAlias.resolveAlias(dataSource, sourceField);
                if (alias != null) {
                    mapping = mappingLookup.get(normalizeFieldKey(alias));
                }
            }

            if (mapping == null) {
                // No mapping configured for this source field, skip it
                continue;
            }

            String targetField = mapping.getTargetField();
            Object convertedValue;

            if (value == null) {
                // Use default value if configured
                if (mapping.getDefaultValue() != null && !mapping.getDefaultValue().isEmpty()) {
                    convertedValue = convertType(mapping.getDefaultValue(), mapping.getTargetType(), mapping.getConverterConfig());
                } else if (Boolean.TRUE.equals(mapping.getIsRequired())) {
                    logger.warn("Required field '{}' is null for data source: {}", sourceField, dataSource);
                    continue;
                } else {
                    continue;
                }
            } else {
                String converter = mapping.getConverter();
                if (converter == null || "NONE".equals(converter)) {
                    convertedValue = convertType(value, mapping.getTargetType(), mapping.getConverterConfig());
                } else {
                    switch (converter) {
                        case "DATE":
                            convertedValue = convertDate(value, mapping.getConverterConfig());
                            break;
                        case "BOOLEAN":
                            convertedValue = convertBoolean(value, mapping.getConverterConfig());
                            break;
                        case "SPLIT":
                            convertedValue = convertSplit(value, mapping.getConverterConfig());
                            break;
                        case "JSON":
                            convertedValue = convertJson(value, mapping.getConverterConfig());
                            break;
                        default:
                            logger.warn("Unknown converter: {} for field: {}", converter, sourceField);
                            convertedValue = value;
                    }
                }
            }

            // Convert target field to camelCase for API response
            String camelCaseField = toCamelCase(targetField);
            result.put(camelCaseField, convertedValue);
        }

        // Check for required fields that were missing from source data
        for (FieldMapping mapping : mappings) {
            String camelCaseField = toCamelCase(mapping.getTargetField());
            String srcKeyLower = normalizeFieldKey(mapping.getSourceField());
            if (Boolean.TRUE.equals(mapping.getIsRequired())
                    && !result.containsKey(camelCaseField)
                    && !sourceDataKeysLower.contains(srcKeyLower)) {
                // Required field entirely missing from source
                if (mapping.getDefaultValue() != null && !mapping.getDefaultValue().isEmpty()) {
                    Object defaultValue = convertType(mapping.getDefaultValue(), mapping.getTargetType(), mapping.getConverterConfig());
                    result.put(camelCaseField, defaultValue);
                }
            }
        }

        return result;
    }

    @Override
    public FieldMapping updateFieldMapping(FieldMapping fieldMapping) {
        if (fieldMapping.getId() == null) {
            throw new BusinessException("字段映射ID不能为空");
        }

        FieldMapping existing = fieldMappingMapper.selectById(fieldMapping.getId());
        if (existing == null) {
            throw new BusinessException("字段映射配置不存在");
        }

        // Validate converter type
        if (fieldMapping.getConverter() != null) {
            String converter = fieldMapping.getConverter();
            if (!"NONE".equals(converter) && !"DATE".equals(converter)
                    && !"BOOLEAN".equals(converter) && !"SPLIT".equals(converter)
                    && !"JSON".equals(converter)) {
                throw new BusinessException("不支持的转换器类型: " + converter);
            }
        }

        fieldMappingMapper.updateById(fieldMapping);
        logger.info("Field mapping updated: id={}, dataSource={}, sourceField={}",
                fieldMapping.getId(), existing.getDataSource(), existing.getSourceField());
        return fieldMappingMapper.selectById(fieldMapping.getId());
    }

    private FieldMapping findActiveMapping(String dataSource, String sourceField) {
        if (dataSource == null || sourceField == null) {
            return null;
        }

        String normalizedSourceField = normalizeFieldKey(sourceField);
        QueryWrapper<FieldMapping> query = new QueryWrapper<>();
        query.eq("data_source", dataSource)
             .eq("is_active", 1)
             .apply("LOWER(source_field) = {0}", normalizedSourceField);
        FieldMapping mapping = fieldMappingMapper.selectOne(query);
        if (mapping != null) {
            return mapping;
        }

        String alias = SchoolDataFieldAlias.resolveAlias(dataSource, sourceField);
        if (alias == null) {
            return null;
        }

        QueryWrapper<FieldMapping> aliasQuery = new QueryWrapper<>();
        aliasQuery.eq("data_source", dataSource)
                  .eq("is_active", 1)
                  .apply("LOWER(source_field) = {0}", normalizeFieldKey(alias));
        return fieldMappingMapper.selectOne(aliasQuery);
    }

    private String normalizeFieldKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT);
    }

    // =========================================================================
    // Type conversion helpers
    // =========================================================================

    /**
     * Convert a value to the specified target type
     */
    private Object convertType(Object value, String targetType, String converterConfig) {
        if (value == null) {
            return null;
        }

        if (targetType == null) {
            return value;
        }

        String strValue = String.valueOf(value);

        switch (targetType) {
            case "NUMBER":
                return parseNumber(strValue);
            case "DATE":
                return convertDate(value, converterConfig);
            case "BOOLEAN":
                return convertBoolean(value, converterConfig);
            case "STRING":
            default:
                return strValue;
        }
    }

    /**
     * DATE converter - parse string to LocalDate or LocalDateTime
     */
    private Object convertDate(Object value, String converterConfig) {
        if (value == null) {
            return null;
        }

        // If already a date type, return as-is
        if (value instanceof LocalDate) {
            return value;
        }
        if (value instanceof LocalDateTime) {
            return value;
        }

        String strValue = String.valueOf(value).trim();
        if (strValue.isEmpty()) {
            return null;
        }

        String pattern = "yyyy-MM-dd"; // default pattern
        if (converterConfig != null && !converterConfig.isEmpty()) {
            try {
                Map<String, String> config = objectMapper.readValue(converterConfig,
                        new TypeReference<Map<String, String>>() {});
                if (config.containsKey("pattern")) {
                    pattern = config.get("pattern");
                }
            } catch (Exception e) {
                logger.warn("Failed to parse DATE converter config: {}", converterConfig, e);
            }
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            if (pattern.contains("HH") || pattern.contains("mm") || pattern.contains("ss")) {
                return LocalDateTime.parse(strValue, formatter);
            } else {
                return LocalDate.parse(strValue, formatter);
            }
        } catch (DateTimeParseException e) {
            // Try common fallback patterns
            String[] fallbackPatterns = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd", "yyyy-MM-dd HH:mm:ss"};
            for (String fallback : fallbackPatterns) {
                try {
                    DateTimeFormatter fallbackFormatter = DateTimeFormatter.ofPattern(fallback);
                    if (fallback.contains("HH") || fallback.contains("mm") || fallback.contains("ss")) {
                        return LocalDateTime.parse(strValue, fallbackFormatter);
                    } else {
                        return LocalDate.parse(strValue, fallbackFormatter);
                    }
                } catch (DateTimeParseException ignored) {
                    // Try next pattern
                }
            }
            logger.warn("Failed to parse date value: '{}' with pattern: {}", strValue, pattern);
            return null;
        }
    }

    /**
     * BOOLEAN converter - convert string to Boolean using configured true/false values
     */
    private Object convertBoolean(Object value, String converterConfig) {
        if (value == null) {
            return null;
        }

        if (value instanceof Boolean) {
            return value;
        }

        String strValue = String.valueOf(value).trim();
        if (strValue.isEmpty()) {
            return null;
        }

        // Default true/false values
        List<String> trueValues = List.of("是", "1", "true", "TRUE", "yes", "Y");
        List<String> falseValues = List.of("否", "0", "false", "FALSE", "no", "N");

        if (converterConfig != null && !converterConfig.isEmpty()) {
            try {
                Map<String, List<String>> config = objectMapper.readValue(converterConfig,
                        new TypeReference<Map<String, List<String>>>() {});
                if (config.containsKey("trueValues")) {
                    trueValues = config.get("trueValues");
                }
                if (config.containsKey("falseValues")) {
                    falseValues = config.get("falseValues");
                }
            } catch (Exception e) {
                logger.warn("Failed to parse BOOLEAN converter config: {}", converterConfig, e);
            }
        }

        if (trueValues.contains(strValue)) {
            return true;
        }
        if (falseValues.contains(strValue)) {
            return false;
        }

        logger.warn("Cannot convert value '{}' to Boolean", strValue);
        return null;
    }

    /**
     * SPLIT converter - split string by delimiter into a comma-separated string
     */
    private Object convertSplit(Object value, String converterConfig) {
        if (value == null) {
            return null;
        }

        String strValue = String.valueOf(value).trim();
        if (strValue.isEmpty()) {
            return null;
        }

        String delimiter = ","; // default delimiter
        if (converterConfig != null && !converterConfig.isEmpty()) {
            try {
                Map<String, String> config = objectMapper.readValue(converterConfig,
                        new TypeReference<Map<String, String>>() {});
                if (config.containsKey("delimiter")) {
                    delimiter = config.get("delimiter");
                }
            } catch (Exception e) {
                logger.warn("Failed to parse SPLIT converter config: {}", converterConfig, e);
            }
        }

        // Split and re-join with comma as standard separator
        String[] parts = strValue.split(java.util.regex.Pattern.quote(delimiter));
        return String.join(",", parts);
    }

    /**
     * JSON converter - parse JSON string
     */
    private Object convertJson(Object value, String converterConfig) {
        if (value == null) {
            return null;
        }

        String strValue = String.valueOf(value).trim();
        if (strValue.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(strValue, Object.class);
        } catch (Exception e) {
            logger.warn("Failed to parse JSON value: {}", strValue, e);
            return strValue;
        }
    }

    /**
     * Parse a string value to Number (Integer or Double)
     */
    private Number parseNumber(String strValue) {
        if (strValue == null || strValue.isEmpty()) {
            return 0;
        }

        try {
            if (strValue.contains(".") || strValue.toLowerCase().contains("e")) {
                return Double.parseDouble(strValue);
            } else {
                return Long.parseLong(strValue);
            }
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse number: {}", strValue);
            return 0;
        }
    }

    /**
     * Convert snake_case to camelCase
     */
    private String toCamelCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) {
            return snakeCase;
        }

        // If no underscore, return as-is
        if (!snakeCase.contains("_")) {
            return snakeCase;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
        }

        return result.toString();
    }
}
