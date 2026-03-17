package com.english.controller;
import com.english.test.TestUtils;

import com.english.entity.Media;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.MediaService;
import com.english.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MediaController unit tests.
 *
 * Covers: upload, upload with invalid MIME type, download (public), delete by owner,
 * delete by admin, delete by non-owner, filename sanitization, get detail,
 * and error paths.
 */
class MediaControllerTest {

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

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(mediaController).addFilters(TestUtils.authenticationFilter()).build();

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
        when(userService.getUserByUsername("admin1")).thenReturn(adminUser);
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

    private Media buildMedia(Long id, Long uploadedBy, String fileName, String mimeType) {
        Media media = new Media();
        media.setId(id);
        media.setUploadedBy(uploadedBy);
        media.setFileName(fileName);
        media.setMimeType(mimeType);
        media.setMediaType("image");
        media.setFileSize(1024L);
        media.setFileContent(new byte[]{1, 2, 3, 4});
        return media;
    }

    // ---- 1. Upload file ----

    @Test
    void uploadMedia_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", new byte[]{1, 2, 3});

        Media savedMedia = buildMedia(1L, 1L, "test.png", "image/png");
        when(mediaService.uploadMedia(any(), eq("image"), eq(1L))).thenReturn(savedMedia);

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .param("mediaType", "image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.mediaId").value(1))
                .andExpect(jsonPath("$.data.fileName").value("test.png"));

        verify(mediaService).uploadMedia(any(), eq("image"), eq(1L));
    }

    // ---- 2. Upload with invalid MIME type -> service throws BusinessException ----

    @Test
    void uploadMedia_serviceThrowsBusinessException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/x-msdownload", new byte[]{0x4D, 0x5A});

        when(mediaService.uploadMedia(any(), eq("application"), eq(1L)))
                .thenThrow(new BusinessException("不支持的文件类型"));

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .param("mediaType", "application"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("不支持的文件类型"));
    }

    // ---- 3. Download file (public access, no auth required in controller) ----

    @Test
    void downloadMedia_success() throws Exception {
        Media media = buildMedia(1L, 1L, "test.png", "image/png");
        when(mediaService.getMediaById(1L)).thenReturn(media);

        mockMvc.perform(get("/api/media/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"test.png\""))
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4}));
    }

    // ---- 4. Delete file (owner can delete) ----

    @Test
    void deleteMedia_ownerCanDelete() throws Exception {
        Media media = buildMedia(1L, 1L, "test.png", "image/png");
        when(mediaService.getMediaById(1L)).thenReturn(media);

        mockMvc.perform(delete("/api/media/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("删除成功"));

        verify(mediaService).deleteMedia(1L);
    }

    // ---- 5. Delete file (ADMIN can delete anyone's media) ----

    @Test
    void deleteMedia_adminCanDeleteAnyMedia() throws Exception {
        setUpSecurityContext("admin1", "ROLE_ADMIN");

        Media media = buildMedia(1L, 1L, "test.png", "image/png"); // uploaded by teacher (id=1)
        when(mediaService.getMediaById(1L)).thenReturn(media);

        mockMvc.perform(delete("/api/media/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("删除成功"));

        verify(mediaService).deleteMedia(1L);
    }

    // ---- 6. Delete file (non-owner rejected) ----

    @Test
    void deleteMedia_nonOwnerRejected() throws Exception {
        // teacher1 (id=1) tries to delete media uploaded by user id=999
        Media media = buildMedia(1L, 999L, "other.png", "image/png");
        when(mediaService.getMediaById(1L)).thenReturn(media);

        mockMvc.perform(delete("/api/media/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无权删除此资源"));

        verify(mediaService, never()).deleteMedia(anyLong());
    }

    // ---- 7. Filename sanitization (special characters in download) ----

    @Test
    void downloadMedia_filenameIsSanitized() throws Exception {
        Media media = buildMedia(1L, 1L, "bad\"file\rname\n.png", "image/png");
        when(mediaService.getMediaById(1L)).thenReturn(media);

        mockMvc.perform(get("/api/media/1/download"))
                .andExpect(status().isOk())
                // Verify that CR, LF, quotes are replaced with underscores
                .andExpect(header().string("Content-Disposition", "inline; filename=\"bad_file_name_.png\""));
    }

    // ---- 8. Download returns 404 for non-existent media ----

    @Test
    void downloadMedia_notFound() throws Exception {
        when(mediaService.getMediaById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/media/999/download"))
                .andExpect(status().isNotFound());
    }

    // ---- 9. MIME type whitelist: unknown type falls back to application/octet-stream ----

    @Test
    void downloadMedia_unknownMimeType_fallsBackToOctetStream() throws Exception {
        Media media = buildMedia(1L, 1L, "data.bin", "text/html"); // text/html NOT in whitelist
        when(mediaService.getMediaById(1L)).thenReturn(media);

        mockMvc.perform(get("/api/media/1/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"));
    }

    // ---- 10. Get media detail ----

    @Test
    void getMediaDetail_success() throws Exception {
        Media media = buildMedia(1L, 1L, "test.png", "image/png");
        when(mediaService.getMediaById(1L)).thenReturn(media);

        mockMvc.perform(get("/api/media/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileName").value("test.png"));
    }

    @Test
    void getMediaDetail_notFound() throws Exception {
        when(mediaService.getMediaById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/media/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("媒体不存在"));
    }
}
