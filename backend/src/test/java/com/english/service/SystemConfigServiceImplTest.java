package com.english.service;

import com.english.entity.SystemConfig;
import com.english.mapper.SystemConfigMapper;
import com.english.service.impl.SystemConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemConfigServiceImplTest {

    private SystemConfigMapper mapper;
    private SystemConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(SystemConfigMapper.class);
        service = new SystemConfigServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "featureToggleService", mock(FeatureToggleService.class));
    }

    @Test
    void listByGroup_repairsMojibakeDescription() {
        SystemConfig config = new SystemConfig();
        config.setConfigGroup("feature_toggle");
        config.setConfigKey("page_dashboard");
        config.setConfigValue("true");
        config.setDescription(new String("学生端-仪表盘页".getBytes(StandardCharsets.UTF_8), Charset.forName("windows-1252")));

        when(mapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(config));

        List<SystemConfig> result = service.listByGroup("feature_toggle");

        assertEquals("学生端-仪表盘页", result.get(0).getDescription());
    }

    @Test
    void listByGroup_keepsNormalDescriptionUntouched() {
        SystemConfig config = new SystemConfig();
        config.setConfigGroup("feature_toggle");
        config.setConfigKey("page_dashboard");
        config.setConfigValue("true");
        config.setDescription("学生端-仪表盘页");

        when(mapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(config));

        List<SystemConfig> result = service.listByGroup("feature_toggle");

        assertEquals("学生端-仪表盘页", result.get(0).getDescription());
    }
}
