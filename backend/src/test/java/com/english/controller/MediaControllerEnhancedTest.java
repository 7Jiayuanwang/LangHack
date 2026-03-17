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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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

/**
 * Enhanced MediaController unit tests.
 * Covers: file upload (valid/invalid MIME), download, delete with IDOR,
 * filename sanitization, path traversal protection, get detail, etc.
 */
@DisplayName("MediaController Enhanced Tests")
class MediaControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MediaService mediaService;

    @Mock
    private UserService userService;

    @InjectMocks
    private MediaController mediaController;

    private User teacherUser;
    private User adminUser;
    private Media mediaFile;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(mediaController)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        setUpSecurityContext("teacher1", "ROLE_TEACHER");

        teacherUser = new User();
        teacherUser.setId(1L);
        teacherUser.setUsername("teacher1");
        teacherUser.setRole("TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);

        adminUser = new User();
        adminUser.setId(99L);
        adminUser.setUsername("admin1");
        adminUser.setRole("ADMIN");

        mediaFile = new Media();
        mediaFile.setId(10L);
        mediaFile.setFilename("audio.mp3");
        mediaFile.setFilepath("/uploads/audio.mp3");
        mediaFile.setFilesize(1024000L);
        mediaFile.setMimeType("audio/mpeg");
        mediaFile.setUploadedBy(1L);
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

    // ========== File Upload ==========

    @Test
    @DisplayName("POST /api/media/upload - upload mp3 file success")
    void uploadMedia_mp3_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "audio.mp3", "audio/mpeg",
                new byte[1024]);
        when(mediaService.uploadFile(any(), anyString())).thenReturn(mediaFile);

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.filename").value("audio.mp3"));

        verify(mediaService).uploadFile(any(), anyString());
    }

    @Test
    @DisplayName("POST /api/media/upload - upload image file success")
    void uploadMedia_image_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                new byte[1024]);

        Media imageMedia = new Media();
        imageMedia.setId(11L);
        imageMedia.setFilename("photo.jpg");
        imageMedia.setMimeType("image/jpeg");
        imageMedia.setUploadedBy(1L);

        when(mediaService.uploadFile(any(), anyString())).thenReturn(imageMedia);

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/media/upload - invalid MIME type returns error")
    void uploadMedia_invalidMimeType_returnsError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "script.exe", "application/x-msdownload",
                new byte[1024]);

        when(mediaService.uploadFile(any(), anyString()))
                .thenThrow(new BusinessException("不支持的文件类型"));

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @DisplayName("POST /api/media/upload - file size exceeds limit returns error")
    void uploadMedia_fileTooLarge_returnsError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "large.mp3", "audio/mpeg",
                new byte[1000000001]); // Over 1GB

        when(mediaService.uploadFile(any(), anyString()))
                .thenThrow(new BusinessException("文件过大"));

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ========== Download (Public) ==========

    @Test
    @DisplayName("GET /api/media/{mediaId}/download - download file success")
    void downloadMedia_success() throws Exception {
        when(mediaService.getMediaById(10L)).thenReturn(mediaFile);

        mockMvc.perform(get("/api/media/10/download"))
                .andExpect(status().isOk());
    }

    // ========== Delete ==========

    @Test
    @DisplayName("DELETE /api/media/{mediaId} - owner deletes file success")
    void deleteMedia_owner_success() throws Exception {
        when(mediaService.getMediaById(10L)).thenReturn(mediaFile);
        doNothing().when(mediaService).deleteMedia(10L);

        mockMvc.perform(delete("/api/media/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(mediaService).deleteMedia(10L);
    }

    @Test
    @DisplayName("DELETE /api/media/{mediaId} - IDOR: non-owner cannot delete")
    void deleteMedia_nonOwner_returnsError() throws Exception {
        Media otherUserMedia = new Media();
        otherUserMedia.setId(10L);
        otherUserMedia.setUploadedBy(999L); // Different user

        when(mediaService.getMediaById(10L)).thenReturn(otherUserMedia);

        mockMvc.perform(delete("/api/media/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限删除此文件"));

        verify(mediaService, never()).deleteMedia(10L);
    }

    @Test
    @DisplayName("DELETE /api/media/{mediaId} - admin can delete any file")
    void deleteMedia_admin_success() throws Exception {
        setUpSecurityContext("admin1", "ROLE_ADMIN");
        when(userService.getUserByUsername("admin1")).thenReturn(adminUser);

        when(mediaService.getMediaById(10L)).thenReturn(mediaFile);
        doNothing().when(mediaService).deleteMedia(10L);

        mockMvc.perform(delete("/api/media/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(mediaService).deleteMedia(10L);
    }

    @Test
    @DisplayName("DELETE /api/media/{mediaId} - file not found returns error")
    void deleteMedia_notFound_returnsError() throws Exception {
        when(mediaService.getMediaById(999L)).thenReturn(null);

        mockMvc.perform(delete("/api/media/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("文件不存在"));

        verify(mediaService, never()).deleteMedia(999L);
    }

    // ========== Get Detail ==========

    @Test
    @DisplayName("GET /api/media/{mediaId} - get media detail success")
    void getMediaDetail_success() throws Exception {
        when(mediaService.getMediaById(10L)).thenReturn(mediaFile);

        mockMvc.perform(get("/api/media/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.filename").value("audio.mp3"));

        verify(mediaService).getMediaById(10L);
    }

    // ========== Filename Sanitization ==========

    @Test
    @DisplayName("POST /api/media/upload - path traversal in filename blocked")
    void uploadMedia_pathTraversal_blocked() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "../../../etc/passwd", "audio/mpeg",
                new byte[1024]);

        when(mediaService.uploadFile(any(), anyString()))
                .thenThrow(new BusinessException("非法文件名"));

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ========== Service Exception Handling ==========

    @Test
    @DisplayName("POST /api/media/upload - service exception returns error")
    void uploadMedia_serviceException_returnsError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "audio.mp3", "audio/mpeg",
                new byte[1024]);

        when(mediaService.uploadFile(any(), anyString()))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }
}
