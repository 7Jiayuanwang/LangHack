package com.english.controller;

import com.english.test.TestUtils;
import com.english.entity.Media;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.MediaService;
import com.english.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("MediaController Enhanced Tests")
class MediaControllerEnhancedTest {

    private MockMvc mockMvc;

    @Mock private MediaService mediaService;
    @Mock private UserService userService;

    @InjectMocks
    private MediaController controller;

    private User currentUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        setUpSecurityContext("user1", "ROLE_USER");

        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("user1");
        currentUser.setRole("USER");
        when(userService.getUserByUsername("user1")).thenReturn(currentUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUpSecurityContext(String username, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null, Collections.singletonList(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ========== GET /api/media/{mediaId} ==========

    @Test
    @DisplayName("GET /api/media/{id} - success")
    void getMediaDetail_success() throws Exception {
        Media media = new Media();
        media.setId(1L);
        media.setFileName("test.png");
        media.setMediaType("image");
        when(mediaService.getMediaById(1L)).thenReturn(media);

        mockMvc.perform(get("/api/media/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/media/{id} - not found")
    void getMediaDetail_notFound() throws Exception {
        when(mediaService.getMediaById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/media/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("媒体不存在"));
    }

    @Test
    @DisplayName("GET /api/media/{id} - service exception")
    void getMediaDetail_serviceException() throws Exception {
        when(mediaService.getMediaById(1L)).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/media/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取失败，请稍后重试"));
    }

    // ========== GET /api/media/{mediaId}/download ==========

    @Test
    @DisplayName("GET /api/media/{id}/download - success")
    void downloadMedia_success() throws Exception {
        Media media = new Media();
        media.setId(1L);
        media.setFileName("test.png");
        media.setMimeType("image/png");
        media.setFileContent(new byte[]{1, 2, 3});
        when(mediaService.getMediaById(1L)).thenReturn(media);

        mockMvc.perform(get("/api/media/1/download"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/media/{id}/download - not found returns 404")
    void downloadMedia_notFound() throws Exception {
        when(mediaService.getMediaById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/media/99/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/media/{id}/download - empty content returns 204")
    void downloadMedia_emptyContent() throws Exception {
        Media media = new Media();
        media.setId(1L);
        media.setFileName("empty.png");
        media.setFileContent(null);
        when(mediaService.getMediaById(1L)).thenReturn(media);

        mockMvc.perform(get("/api/media/1/download"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/media/{id}/download - unsafe mime type falls back to octet-stream")
    void downloadMedia_unsafeMimeType() throws Exception {
        Media media = new Media();
        media.setId(1L);
        media.setFileName("file.html");
        media.setMimeType("text/html"); // not in whitelist
        media.setFileContent(new byte[]{1, 2, 3});
        when(mediaService.getMediaById(1L)).thenReturn(media);

        mockMvc.perform(get("/api/media/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    // ========== DELETE /api/media/{mediaId} ==========

    @Test
    @DisplayName("DELETE /api/media/{id} - success by owner")
    void deleteMedia_success() throws Exception {
        Media media = new Media();
        media.setId(1L);
        media.setUploadedBy(1L); // same as currentUser.getId()
        when(mediaService.getMediaById(1L)).thenReturn(media);
        doNothing().when(mediaService).deleteMedia(1L);

        mockMvc.perform(delete("/api/media/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("删除成功"));
    }

    @Test
    @DisplayName("DELETE /api/media/{id} - not found")
    void deleteMedia_notFound() throws Exception {
        when(mediaService.getMediaById(99L)).thenReturn(null);

        mockMvc.perform(delete("/api/media/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("媒体不存在"));
    }

    @Test
    @DisplayName("DELETE /api/media/{id} - no permission")
    void deleteMedia_noPermission() throws Exception {
        Media media = new Media();
        media.setId(1L);
        media.setUploadedBy(99L); // different user
        when(mediaService.getMediaById(1L)).thenReturn(media);

        mockMvc.perform(delete("/api/media/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无权删除此资源"));
    }

    @Test
    @DisplayName("DELETE /api/media/{id} - admin can delete any")
    void deleteMedia_adminBypass() throws Exception {
        setUpSecurityContext("admin1", "ROLE_ADMIN");
        User admin = new User();
        admin.setId(100L);
        admin.setUsername("admin1");
        admin.setRole("ADMIN");
        when(userService.getUserByUsername("admin1")).thenReturn(admin);

        Media media = new Media();
        media.setId(1L);
        media.setUploadedBy(99L); // different user
        when(mediaService.getMediaById(1L)).thenReturn(media);
        doNothing().when(mediaService).deleteMedia(1L);

        mockMvc.perform(delete("/api/media/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("删除成功"));
    }
}
