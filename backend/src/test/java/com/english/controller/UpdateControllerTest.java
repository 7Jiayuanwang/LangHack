package com.english.controller;

import com.english.config.DynamicUpdateScheduler;
import com.english.entity.UpdateConfig;
import com.english.exception.BusinessException;
import com.english.service.UpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UpdateControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private UpdateService updateService;

    @Mock
    private DynamicUpdateScheduler dynamicUpdateScheduler;

    @InjectMocks
    private UpdateController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/ops/update/status returns update metadata")
    void getStatus_success() throws Exception {
        UpdateConfig config = new UpdateConfig();
        config.setEnabled(true);
        config.setCronExpr("0 0 3 * * ?");
        config.setGitBranch("main");
        config.setLastRunStatus("SUCCESS");

        when(updateService.getConfig()).thenReturn(config);
        when(updateService.isSupported()).thenReturn(true);
        when(updateService.getUnsupportedReason()).thenReturn("");

        mockMvc.perform(get("/api/ops/update/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.cronExpr").value("0 0 3 * * ?"))
                .andExpect(jsonPath("$.data.gitBranch").value("main"))
                .andExpect(jsonPath("$.data.lastRunStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.supported").value(true));
    }

    @Test
    @DisplayName("POST /api/ops/update/config saves config and reschedules task")
    void saveConfig_success() throws Exception {
        UpdateConfig saved = new UpdateConfig();
        saved.setId(1L);
        saved.setEnabled(true);
        saved.setCronExpr("0 30 2 * * ?");
        saved.setGitBranch("main");
        saved.setBackupBeforeUpdate(true);

        when(updateService.saveConfig(any(UpdateConfig.class))).thenReturn(saved);

        mockMvc.perform(post("/api/ops/update/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"cronExpr\":\"0 30 2 * * ?\",\"gitBranch\":\"main\",\"backupBeforeUpdate\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cronExpr").value("0 30 2 * * ?"))
                .andExpect(jsonPath("$.data.gitBranch").value("main"));

        verify(dynamicUpdateScheduler).reschedule(saved);
    }

    @Test
    @DisplayName("GET /api/ops/update/config returns business error message")
    void getConfig_businessException_returnsError() throws Exception {
        when(updateService.getConfig()).thenThrow(new BusinessException("unsupported"));

        mockMvc.perform(get("/api/ops/update/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("unsupported"));

        verify(dynamicUpdateScheduler, never()).reschedule(any(UpdateConfig.class));
    }

    @Test
    @DisplayName("GET /api/ops/update/trigger converts unexpected exception to SSE error event")
    void triggerUpdate_unexpectedException_returnsEmitter() {
        when(updateService.triggerUpdate(any(SseEmitter.class)))
                .thenThrow(new RuntimeException("db down"));

        SseEmitter emitter = controller.triggerUpdate();

        verify(updateService).triggerUpdate(any(SseEmitter.class));
        org.junit.jupiter.api.Assertions.assertNotNull(emitter);
    }
}
