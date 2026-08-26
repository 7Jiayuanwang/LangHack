package com.english.controller;
import com.english.test.TestUtils;

import com.english.entity.Text;
import com.english.entity.User;
import com.english.service.TextService;
import com.english.service.UnitService;
import com.english.service.UserService;
import com.english.util.TestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserTextControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TextService textService;

    @Mock
    private UnitService unitService;

    @Mock
    private UserService userService;

    @Mock
    private com.english.service.ContentTagService contentTagService;

    @Mock
    private com.english.service.SystemConfigService systemConfigService;

    @InjectMocks
    private UserTextController userTextController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        doNothing().when(unitService).ensureUserUploadUnitExists(anyLong());
        when(systemConfigService.get("feature_toggle", "content.text_auto_approve")).thenReturn("false");
        // Mock filterValidTags to return valid tags
        when(contentTagService.filterValidTags(anyString())).thenAnswer(invocation -> {
            String tags = invocation.getArgument(0);
            if (tags == null || tags.trim().isEmpty()) return "";
            return tags; // Pass through for test simplicity
        });
        mockMvc = MockMvcBuilders.standaloneSetup(userTextController).addFilters(TestUtils.authenticationFilter()).build();
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== POST /api/user/texts/upload ==========

    @Test
    @DisplayName("POST /api/user/texts/upload - authenticated user uploads text successfully")
    void uploadText_authenticated_success() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);
        when(textService.getMaxOrderInUnit(999L)).thenReturn(5);
        when(textService.save(any(Text.class))).thenReturn(true);

        Map<String, Object> textData = new HashMap<>();
        textData.put("title", "My Article");
        textData.put("content", "This is test content");
        textData.put("difficulty", "EASY");
        textData.put("tags", "general");

        mockMvc.perform(post("/api/user/texts/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(textData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.message").value("课文上传成功"));

        verify(textService).save(any(Text.class));
    }

    @Test
    @DisplayName("POST /api/user/texts/upload - missing title returns error")
    void uploadText_missingTitle_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);

        Map<String, Object> textData = new HashMap<>();
        textData.put("content", "This is test content");
        textData.put("tags", "general");

        mockMvc.perform(post("/api/user/texts/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(textData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("课文标题不能为空"));
    }

    @Test
    @DisplayName("POST /api/user/texts/upload - missing content returns error")
    void uploadText_missingContent_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);

        Map<String, Object> textData = new HashMap<>();
        textData.put("title", "My Article");
        textData.put("tags", "general");

        mockMvc.perform(post("/api/user/texts/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(textData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("课文内容不能为空"));
    }

    @Test
    @DisplayName("POST /api/user/texts/upload - user not found returns error")
    void uploadText_userNotFound_returnsError() throws Exception {
        TestHelper.createMockAuthentication("unknown", "USER");
        when(userService.getUserByUsername("unknown")).thenReturn(null);

        Map<String, Object> textData = new HashMap<>();
        textData.put("title", "My Article");
        textData.put("content", "Content here");

        mockMvc.perform(post("/api/user/texts/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(textData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    @DisplayName("POST /api/user/texts/upload - default difficulty is MEDIUM")
    void uploadText_defaultDifficulty() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);
        when(textService.getMaxOrderInUnit(999L)).thenReturn(0);
        when(textService.save(any(Text.class))).thenReturn(true);

        Map<String, Object> textData = new HashMap<>();
        textData.put("title", "My Article");
        textData.put("content", "Content here");
        textData.put("tags", "general");

        mockMvc.perform(post("/api/user/texts/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(textData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Text> textCaptor = ArgumentCaptor.forClass(Text.class);
        verify(textService).save(textCaptor.capture());
        Assertions.assertEquals("MEDIUM", textCaptor.getValue().getDifficulty());
        Assertions.assertEquals(1, textCaptor.getValue().getIsPublic());
        Assertions.assertEquals("PENDING", textCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("POST /api/user/texts/upload - exception returns error")
    void uploadText_exception_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);
        when(textService.getMaxOrderInUnit(999L)).thenReturn(0);
        when(textService.save(any(Text.class))).thenThrow(new RuntimeException("DB error"));

        Map<String, Object> textData = new HashMap<>();
        textData.put("title", "My Article");
        textData.put("content", "Content here");
        textData.put("tags", "general");

        mockMvc.perform(post("/api/user/texts/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(textData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("上传失败，请稍后重试"));
    }

    // ========== GET /api/user/texts/my-uploads ==========

    @Test
    @DisplayName("GET /api/user/texts/my-uploads - returns user uploads")
    void getMyUploads_authenticated_returnsUploads() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);

        List<Text> uploads = List.of(
                createMockText(1L, "Text 1", 1L),
                createMockText(2L, "Text 2", 1L)
        );
        when(textService.getTextsByUploader(1L)).thenReturn(uploads);

        mockMvc.perform(get("/api/user/texts/my-uploads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/user/texts/my-uploads - user not found returns error")
    void getMyUploads_userNotFound_returnsError() throws Exception {
        TestHelper.createMockAuthentication("unknown", "USER");
        when(userService.getUserByUsername("unknown")).thenReturn(null);

        mockMvc.perform(get("/api/user/texts/my-uploads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    @DisplayName("GET /api/user/texts/my-uploads - exception returns error")
    void getMyUploads_exception_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);
        when(textService.getTextsByUploader(1L)).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/user/texts/my-uploads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取失败，请稍后重试"));
    }

    // ========== GET /api/user/texts/{textId} ==========

    @Test
    @DisplayName("GET /api/user/texts/{textId} - owner can view their text")
    void getTextDetail_owner_returnsDetail() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);

        Text text = createMockText(10L, "My Text", 1L);
        text.setIsPublic(0);
        when(textService.getTextById(10L)).thenReturn(text);
        doNothing().when(textService).incrementViewCount(10L);

        mockMvc.perform(get("/api/user/texts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.title").value("My Text"));

        verify(textService).incrementViewCount(10L);
    }

    @Test
    @DisplayName("GET /api/user/texts/{textId} - non-owner can view public text")
    void getTextDetail_nonOwner_publicText_returnsDetail() throws Exception {
        TestHelper.createMockAuthentication("student2", "USER");
        User user = TestHelper.createMockUser(2L, "student2", "USER");
        when(userService.getUserByUsername("student2")).thenReturn(user);

        Text text = createMockText(10L, "Public Text", 1L);
        text.setIsPublic(1);
        when(textService.getTextById(10L)).thenReturn(text);
        doNothing().when(textService).incrementViewCount(10L);

        mockMvc.perform(get("/api/user/texts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    @DisplayName("GET /api/user/texts/{textId} - non-owner cannot view private text")
    void getTextDetail_nonOwner_privateText_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student2", "USER");
        User user = TestHelper.createMockUser(2L, "student2", "USER");
        when(userService.getUserByUsername("student2")).thenReturn(user);

        Text text = createMockText(10L, "Private Text", 1L);
        text.setIsPublic(0);
        when(textService.getTextById(10L)).thenReturn(text);

        mockMvc.perform(get("/api/user/texts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限查看此课文"));
    }

    @Test
    @DisplayName("GET /api/user/texts/{textId} - non-existent text returns error")
    void getTextDetail_nonExistent_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);
        when(textService.getTextById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/user/texts/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("课文不存在"));
    }

    // ========== PUT /api/user/texts/{textId} ==========

    @Test
    @DisplayName("PUT /api/user/texts/{textId} - owner updates text successfully")
    void updateText_owner_success() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);

        Text text = createMockText(10L, "Old Title", 1L);
        when(textService.getTextById(10L)).thenReturn(text);
        when(textService.updateById(any(Text.class))).thenReturn(true);

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("title", "New Title");
        updateData.put("content", "New content");

        mockMvc.perform(put("/api/user/texts/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("更新成功"));

        verify(textService).updateById(any(Text.class));
    }

    @Test
    @DisplayName("PUT /api/user/texts/{textId} - non-owner cannot update")
    void updateText_nonOwner_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student2", "USER");
        User user = TestHelper.createMockUser(2L, "student2", "USER");
        when(userService.getUserByUsername("student2")).thenReturn(user);

        Text text = createMockText(10L, "Title", 1L);
        when(textService.getTextById(10L)).thenReturn(text);

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("title", "Hacked Title");

        mockMvc.perform(put("/api/user/texts/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限修改此课文"));
    }

    @Test
    @DisplayName("PUT /api/user/texts/{textId} - empty title returns error")
    void updateText_emptyTitle_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);

        Text text = createMockText(10L, "Old Title", 1L);
        when(textService.getTextById(10L)).thenReturn(text);

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("title", "");

        mockMvc.perform(put("/api/user/texts/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("课文标题不能为空"));
    }

    @Test
    @DisplayName("PUT /api/user/texts/{textId} - empty content returns error")
    void updateText_emptyContent_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);

        Text text = createMockText(10L, "Title", 1L);
        when(textService.getTextById(10L)).thenReturn(text);

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("content", "");

        mockMvc.perform(put("/api/user/texts/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("课文内容不能为空"));
    }

    @Test
    @DisplayName("PUT /api/user/texts/{textId} - text not found returns error")
    void updateText_textNotFound_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);
        when(textService.getTextById(999L)).thenReturn(null);

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("title", "Title");

        mockMvc.perform(put("/api/user/texts/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("课文不存在"));
    }

    @Test
    @DisplayName("PUT /api/user/texts/{textId} - exception returns error")
    void updateText_exception_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);

        Text text = createMockText(10L, "Title", 1L);
        when(textService.getTextById(10L)).thenReturn(text);
        when(textService.updateById(any(Text.class))).thenThrow(new RuntimeException("DB error"));

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("title", "New Title");

        mockMvc.perform(put("/api/user/texts/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("更新失败，请稍后重试"));
    }

    // ========== DELETE /api/user/texts/{textId} ==========

    @Test
    @DisplayName("DELETE /api/user/texts/{textId} - owner deletes text successfully")
    void deleteText_owner_success() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);

        Text text = createMockText(10L, "My Text", 1L);
        when(textService.getTextById(10L)).thenReturn(text);
        doNothing().when(textService).deleteText(10L);

        mockMvc.perform(delete("/api/user/texts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("删除成功"));

        verify(textService).deleteText(10L);
    }

    @Test
    @DisplayName("DELETE /api/user/texts/{textId} - non-owner cannot delete")
    void deleteText_nonOwner_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student2", "USER");
        User user = TestHelper.createMockUser(2L, "student2", "USER");
        when(userService.getUserByUsername("student2")).thenReturn(user);

        Text text = createMockText(10L, "Not Mine", 1L);
        when(textService.getTextById(10L)).thenReturn(text);

        mockMvc.perform(delete("/api/user/texts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限删除此课文"));
    }

    @Test
    @DisplayName("DELETE /api/user/texts/{textId} - text not found returns error")
    void deleteText_textNotFound_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);
        when(textService.getTextById(999L)).thenReturn(null);

        mockMvc.perform(delete("/api/user/texts/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("课文不存在"));
    }

    @Test
    @DisplayName("DELETE /api/user/texts/{textId} - exception returns error")
    void deleteText_exception_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        User user = TestHelper.createMockUser(1L, "student1", "USER");
        when(userService.getUserByUsername("student1")).thenReturn(user);

        Text text = createMockText(10L, "Title", 1L);
        when(textService.getTextById(10L)).thenReturn(text);
        doThrow(new RuntimeException("DB error")).when(textService).deleteText(10L);

        mockMvc.perform(delete("/api/user/texts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("删除失败，请稍后重试"));
    }

    // ========== GET /api/user/texts/public ==========

    @Test
    @DisplayName("GET /api/user/texts/public - returns public texts")
    void getPublicTexts_returnsList() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");

        List<Text> publicTexts = List.of(
                createMockText(1L, "Public 1", 10L),
                createMockText(2L, "Public 2", 20L)
        );
        when(textService.getPublicTexts(anyInt(), anyInt(), any(), any())).thenReturn(publicTexts);

        mockMvc.perform(get("/api/user/texts/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/user/texts/public - with difficulty and keyword parameters")
    void getPublicTexts_withParams() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");

        when(textService.getPublicTexts(eq(1), eq(20), eq("EASY"), eq("hello")))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/user/texts/public")
                        .param("difficulty", "EASY")
                        .param("keyword", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/user/texts/public - keyword too long is truncated")
    void getPublicTexts_longKeywordTruncated() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");

        String longKeyword = "a".repeat(150);
        when(textService.getPublicTexts(anyInt(), anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/user/texts/public")
                        .param("keyword", longKeyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Verify keyword was truncated to 100 chars
        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        verify(textService).getPublicTexts(anyInt(), anyInt(), any(), keywordCaptor.capture());
        Assertions.assertEquals(100, keywordCaptor.getValue().length());
    }

    @Test
    @DisplayName("GET /api/user/texts/public - exception returns error")
    void getPublicTexts_exception_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");

        when(textService.getPublicTexts(anyInt(), anyInt(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/user/texts/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取失败，请稍后重试"));
    }

    // ========== POST /api/user/texts/{textId}/like ==========

    @Test
    @DisplayName("POST /api/user/texts/{textId}/like - like public text successfully")
    void likeText_publicText_success() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");

        Text text = createMockText(10L, "Popular Text", 5L);
        text.setIsPublic(1);
        when(textService.getTextById(10L)).thenReturn(text);
        doNothing().when(textService).incrementLikeCount(10L);

        mockMvc.perform(post("/api/user/texts/10/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("点赞成功"));

        verify(textService).incrementLikeCount(10L);
    }

    @Test
    @DisplayName("POST /api/user/texts/{textId}/like - private text returns error")
    void likeText_privateText_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");

        Text text = createMockText(10L, "Private Text", 5L);
        text.setIsPublic(0);
        when(textService.getTextById(10L)).thenReturn(text);

        mockMvc.perform(post("/api/user/texts/10/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("只能点赞公开课文"));
    }

    @Test
    @DisplayName("POST /api/user/texts/{textId}/like - text not found returns error")
    void likeText_textNotFound_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        when(textService.getTextById(999L)).thenReturn(null);

        mockMvc.perform(post("/api/user/texts/999/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("课文不存在"));
    }

    @Test
    @DisplayName("POST /api/user/texts/{textId}/like - exception returns error")
    void likeText_exception_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");
        when(textService.getTextById(10L)).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(post("/api/user/texts/10/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("点赞失败，请稍后重试"));
    }

    // ========== Helper Methods ==========

    private Text createMockText(Long id, String title, Long uploadedBy) {
        Text text = new Text();
        text.setId(id);
        text.setTitle(title);
        text.setUploadedBy(uploadedBy);
        text.setDeleted(0);
        text.setDifficulty("MEDIUM");
        text.setContent("Default content");
        return text;
    }
}
