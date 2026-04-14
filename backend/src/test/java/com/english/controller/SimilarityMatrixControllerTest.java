package com.english.controller;

import com.english.config.SimilarityMatrixScheduleTask;
import com.english.test.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for SimilarityMatrixController.
 *
 * Endpoints:
 *   POST /api/admin/similarity/recompute/text
 *   POST /api/admin/similarity/recompute/user
 *   POST /api/admin/similarity/recompute/all
 */
class SimilarityMatrixControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SimilarityMatrixScheduleTask scheduleTask;

    private SimilarityMatrixController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new SimilarityMatrixController();
        controller.scheduleTask = scheduleTask;

        // 设置 ADMIN 认证
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilter(TestUtils.authenticationFilter())
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/admin/similarity/recompute/text
    // ══════════════════════════════════════════════════════════════

    @Test
    void recomputeText_success_returns200() throws Exception {
        doNothing().when(scheduleTask).recomputeTextSimilarityMatrix();

        mockMvc.perform(post("/api/admin/similarity/recompute/text")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文章相似度矩阵重算完成"));

        verify(scheduleTask).recomputeTextSimilarityMatrix();
    }

    @Test
    void recomputeText_failure_returns500() throws Exception {
        doThrow(new RuntimeException("DB error"))
                .when(scheduleTask).recomputeTextSimilarityMatrix();

        mockMvc.perform(post("/api/admin/similarity/recompute/text")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("文章相似度矩阵重算失败: DB error"));

        verify(scheduleTask).recomputeTextSimilarityMatrix();
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/admin/similarity/recompute/user
    // ══════════════════════════════════════════════════════════════

    @Test
    void recomputeUser_success_returns200() throws Exception {
        doNothing().when(scheduleTask).recomputeUserSimilarityMatrix();

        mockMvc.perform(post("/api/admin/similarity/recompute/user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("用户相似度矩阵重算完成"));

        verify(scheduleTask).recomputeUserSimilarityMatrix();
    }

    @Test
    void recomputeUser_failure_returns500() throws Exception {
        doThrow(new RuntimeException("CTE error"))
                .when(scheduleTask).recomputeUserSimilarityMatrix();

        mockMvc.perform(post("/api/admin/similarity/recompute/user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户相似度矩阵重算失败: CTE error"));
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/admin/similarity/recompute/all
    // ══════════════════════════════════════════════════════════════

    @Test
    void recomputeAll_success_returns200() throws Exception {
        doNothing().when(scheduleTask).recomputeAll();

        mockMvc.perform(post("/api/admin/similarity/recompute/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("全部相似度矩阵重算完成"));

        verify(scheduleTask).recomputeAll();
    }

    @Test
    void recomputeAll_failure_returns500() throws Exception {
        doThrow(new RuntimeException("Rename failed"))
                .when(scheduleTask).recomputeAll();

        mockMvc.perform(post("/api/admin/similarity/recompute/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("全部相似度矩阵重算失败: Rename failed"));
    }
}
