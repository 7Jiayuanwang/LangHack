package com.english.service;

import com.english.entity.FieldMapping;
import com.english.exception.BusinessException;
import com.english.mapper.FieldMappingMapper;
import com.english.service.impl.FieldMappingServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FieldMappingServiceImpl.
 *
 * Key implementation facts:
 * - 4 converters: DATE, BOOLEAN, SPLIT, JSON
 * - applyMapping: builds lookup from sourceField->mapping, iterates source data
 * - getMappingsByDataSource: queries by data_source + is_active=1, ordered by sort_order
 * - updateFieldMapping: validates id, existence, and converter type
 */
@ExtendWith(MockitoExtension.class)
class FieldMappingServiceTest {

    private FieldMappingServiceImpl fieldMappingService;

    @Mock
    private FieldMappingMapper fieldMappingMapper;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        fieldMappingService = new FieldMappingServiceImpl(fieldMappingMapper, objectMapper);
    }

    // ==================== getMappingsByDataSource tests ====================

    @Nested
    @DisplayName("getMappingsByDataSource 获取字段映射配置")
    class GetMappingsByDataSourceTests {

        @Test
        @DisplayName("返回指定数据源的活跃映射")
        void getMappingsByDataSource_returnsActiveMappings() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(1L);
            mapping.setDataSource("administrative_class");
            mapping.setSourceField("XZBH");
            mapping.setTargetField("classCode");
            mapping.setIsActive(true);

            when(fieldMappingMapper.selectList(any())).thenReturn(List.of(mapping));

            List<FieldMapping> result = fieldMappingService.getMappingsByDataSource("administrative_class");

            assertEquals(1, result.size());
            assertEquals("XZBH", result.get(0).getSourceField());
            verify(fieldMappingMapper).selectList(any());
        }

        @Test
        @DisplayName("无映射配置时返回空列表")
        void getMappingsByDataSource_noMappings_returnsEmptyList() {
            when(fieldMappingMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<FieldMapping> result = fieldMappingService.getMappingsByDataSource("unknown_source");

            assertTrue(result.isEmpty());
        }
    }

    // ==================== convertField tests ====================

    @Nested
    @DisplayName("convertField 字段值转换")
    class ConvertFieldTests {

        @Test
        @DisplayName("null值直接返回null")
        void convertField_nullValue_returnsNull() {
            Object result = fieldMappingService.convertField("test", "field", null);
            assertNull(result);
        }

        @Test
        @DisplayName("无映射配置时原样返回")
        void convertField_noMapping_returnsValueAsIs() {
            when(fieldMappingMapper.selectOne(any())).thenReturn(null);

            Object result = fieldMappingService.convertField("test", "unknown", "hello");
            assertEquals("hello", result);
        }
    }

    // ==================== applyMapping tests ====================

    @Nested
    @DisplayName("applyMapping 整条数据映射")
    class ApplyMappingTests {

        @Test
        @DisplayName("null或空数据返回空Map")
        void applyMapping_nullData_returnsEmptyMap() {
            assertTrue(fieldMappingService.applyMapping("test", null).isEmpty());
            assertTrue(fieldMappingService.applyMapping("test", Collections.emptyMap()).isEmpty());
        }

        @Test
        @DisplayName("无映射配置时返回源数据副本（所有字段原样透传）")
        void applyMapping_noMappings_returnsSourceDataCopy() {
            when(fieldMappingMapper.selectList(any())).thenReturn(Collections.emptyList());

            Map<String, Object> source = Map.of("XZBH", "20230101");
            Map<String, Object> result = fieldMappingService.applyMapping("test", source);

            assertFalse(result.isEmpty());
            assertEquals("20230101", result.get("XZBH"));
        }

        @Test
        @DisplayName("简单字符串字段映射正确转换")
        void applyMapping_simpleStringMapping_convertsCorrectly() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(1L);
            mapping.setDataSource("administrative_class");
            mapping.setSourceField("XZBH");
            mapping.setTargetField("classCode");
            mapping.setTargetType("STRING");
            mapping.setConverter("NONE");
            mapping.setIsActive(true);
            mapping.setIsRequired(false);

            when(fieldMappingMapper.selectList(any())).thenReturn(List.of(mapping));

            Map<String, Object> source = Map.of("XZBH", "20230101");
            Map<String, Object> result = fieldMappingService.applyMapping("administrative_class", source);

            assertEquals("20230101", result.get("classCode"));
        }

        @Test
        @DisplayName("BOOLEAN转换器正确转换中文值")
        void applyMapping_booleanConverter_convertsChineseValues() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(1L);
            mapping.setDataSource("school_course");
            mapping.setSourceField("SFSYJX");
            mapping.setTargetField("isBilingual");
            mapping.setTargetType("STRING");
            mapping.setConverter("BOOLEAN");
            mapping.setConverterConfig("{\"trueValues\":[\"是\",\"1\",\"true\"],\"falseValues\":[\"否\",\"0\",\"false\"]}");
            mapping.setIsActive(true);
            mapping.setIsRequired(false);

            when(fieldMappingMapper.selectList(any())).thenReturn(List.of(mapping));

            Map<String, Object> source = Map.of("SFSYJX", "是");
            Map<String, Object> result = fieldMappingService.applyMapping("school_course", source);

            assertEquals(true, result.get("isBilingual"));
        }

        @Test
        @DisplayName("SPLIT转换器正确拆分字符串")
        void applyMapping_splitConverter_splitsCorrectly() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(1L);
            mapping.setDataSource("school_course");
            mapping.setSourceField("SYZY");
            mapping.setTargetField("applicableMajors");
            mapping.setTargetType("STRING");
            mapping.setConverter("SPLIT");
            mapping.setConverterConfig("{\"delimiter\":\",\"}");
            mapping.setIsActive(true);
            mapping.setIsRequired(false);

            when(fieldMappingMapper.selectList(any())).thenReturn(List.of(mapping));

            Map<String, Object> source = Map.of("SYZY", "计算机科学,软件工程,信息安全");
            Map<String, Object> result = fieldMappingService.applyMapping("school_course", source);

            assertEquals("计算机科学,软件工程,信息安全", result.get("applicableMajors"));
        }

        @Test
        @DisplayName("DATE转换器正确解析日期")
        void applyMapping_dateConverter_parsesDate() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(1L);
            mapping.setDataSource("administrative_class");
            mapping.setSourceField("JLRQ");
            mapping.setTargetField("establishDate");
            mapping.setTargetType("DATE");
            mapping.setConverter("DATE");
            mapping.setConverterConfig("{\"pattern\":\"yyyy-MM-dd\"}");
            mapping.setIsActive(true);
            mapping.setIsRequired(false);

            when(fieldMappingMapper.selectList(any())).thenReturn(List.of(mapping));

            Map<String, Object> source = Map.of("JLRQ", "2023-09-01");
            Map<String, Object> result = fieldMappingService.applyMapping("administrative_class", source);

            assertEquals(LocalDate.of(2023, 9, 1), result.get("establishDate"));
        }

        @Test
        @DisplayName("必填字段为null且无默认值时跳过该字段")
        void applyMapping_requiredFieldNull_skipField() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(1L);
            mapping.setDataSource("test");
            mapping.setSourceField("required_field");
            mapping.setTargetField("targetField");
            mapping.setTargetType("STRING");
            mapping.setConverter("NONE");
            mapping.setIsActive(true);
            mapping.setIsRequired(true);

            when(fieldMappingMapper.selectList(any())).thenReturn(List.of(mapping));

            Map<String, Object> source = new HashMap<>();
            source.put("required_field", null);
            Map<String, Object> result = fieldMappingService.applyMapping("test", source);

            assertFalse(result.containsKey("targetField"));
        }

        @Test
        @DisplayName("null值使用默认值")
        void applyMapping_nullValue_usesDefaultValue() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(1L);
            mapping.setDataSource("test");
            mapping.setSourceField("field");
            mapping.setTargetField("targetField");
            mapping.setTargetType("STRING");
            mapping.setConverter("NONE");
            mapping.setIsActive(true);
            mapping.setIsRequired(false);
            mapping.setDefaultValue("DEFAULT");

            when(fieldMappingMapper.selectList(any())).thenReturn(List.of(mapping));

            Map<String, Object> source = new HashMap<>();
            source.put("field", null);
            Map<String, Object> result = fieldMappingService.applyMapping("test", source);

            assertEquals("DEFAULT", result.get("targetField"));
        }

        @Test
        @DisplayName("中文 key 经字典兜底翻译为英文别名后命中 mapping（CDSP 实际返回场景）")
        void applyMapping_chineseKey_resolvesToAlias() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(1L);
            mapping.setDataSource("teaching_plans");
            mapping.setSourceField("kkjhid");
            mapping.setTargetField("planId");
            mapping.setTargetType("STRING");
            mapping.setConverter("NONE");
            mapping.setIsActive(true);
            mapping.setIsRequired(true);

            when(fieldMappingMapper.selectList(any())).thenReturn(List.of(mapping));

            Map<String, Object> source = new HashMap<>();
            source.put("开课计划id", "1244967");
            Map<String, Object> result = fieldMappingService.applyMapping("teaching_plans", source);

            assertEquals("1244967", result.get("planId"));
        }

        @Test
        @DisplayName("UPPERCASE 端点同样支持中文 key 兜底（course_selection 场景）")
        void applyMapping_chineseKeyForUppercaseEndpoint_resolvesToAlias() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(1L);
            mapping.setDataSource("course_selection");
            mapping.setSourceField("XH");
            mapping.setTargetField("studentId");
            mapping.setTargetType("STRING");
            mapping.setConverter("NONE");
            mapping.setIsActive(true);
            mapping.setIsRequired(true);

            when(fieldMappingMapper.selectList(any())).thenReturn(List.of(mapping));

            Map<String, Object> source = new HashMap<>();
            source.put("学号", "2023122066164");
            Map<String, Object> result = fieldMappingService.applyMapping("course_selection", source);

            assertEquals("2023122066164", result.get("studentId"));
        }

        @Test
        @DisplayName("中文 key 存在时必填字段不应误判为缺失（兜底翻译加入 keys 集合）")
        void applyMapping_chineseKeyPresent_doesNotTriggerMissingDefault() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(1L);
            mapping.setDataSource("student_schedule");
            mapping.setSourceField("xh");
            mapping.setTargetField("studentId");
            mapping.setTargetType("STRING");
            mapping.setConverter("NONE");
            mapping.setIsActive(true);
            mapping.setIsRequired(true);
            mapping.setDefaultValue("FALLBACK");

            when(fieldMappingMapper.selectList(any())).thenReturn(List.of(mapping));

            Map<String, Object> source = new HashMap<>();
            source.put("学号", "20231001");
            Map<String, Object> result = fieldMappingService.applyMapping("student_schedule", source);

            // 应该取中文 key 对应的值，而不是触发 default 回填
            assertEquals("20231001", result.get("studentId"));
        }
    }

    // ==================== updateFieldMapping tests ====================

    @Nested
    @DisplayName("updateFieldMapping 更新字段映射配置")
    class UpdateFieldMappingTests {

        @Test
        @DisplayName("ID为null时抛出BusinessException")
        void updateFieldMapping_nullId_throwsBusinessException() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(null);

            assertThrows(BusinessException.class,
                    () -> fieldMappingService.updateFieldMapping(mapping));
        }

        @Test
        @DisplayName("记录不存在时抛出BusinessException")
        void updateFieldMapping_nonExistentRecord_throwsBusinessException() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(999L);

            when(fieldMappingMapper.selectById(999L)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> fieldMappingService.updateFieldMapping(mapping));
        }

        @Test
        @DisplayName("不支持的转换器类型时抛出BusinessException")
        void updateFieldMapping_invalidConverter_throwsBusinessException() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(1L);
            mapping.setConverter("INVALID");

            when(fieldMappingMapper.selectById(1L)).thenReturn(new FieldMapping());

            assertThrows(BusinessException.class,
                    () -> fieldMappingService.updateFieldMapping(mapping));
        }

        @Test
        @DisplayName("有效更新成功返回更新后的对象")
        void updateFieldMapping_validUpdate_returnsUpdatedMapping() {
            FieldMapping existing = new FieldMapping();
            existing.setId(1L);
            existing.setDataSource("test");
            existing.setSourceField("oldField");

            FieldMapping update = new FieldMapping();
            update.setId(1L);
            update.setConverter("NONE");

            FieldMapping afterUpdate = new FieldMapping();
            afterUpdate.setId(1L);
            afterUpdate.setConverter("NONE");

            when(fieldMappingMapper.selectById(1L)).thenReturn(existing).thenReturn(afterUpdate);
            when(fieldMappingMapper.updateById(any())).thenReturn(1);

            FieldMapping result = fieldMappingService.updateFieldMapping(update);

            assertNotNull(result);
            verify(fieldMappingMapper).updateById(update);
        }
    }
}
