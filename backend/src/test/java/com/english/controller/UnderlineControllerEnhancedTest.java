package com.english.controller;

import com.english.test.TestUtils;
import com.english.entity.Underline;
import com.english.entity.User;
import com.english.service.UnderlineService;
import com.english.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.MediaType;
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
 * Enhanced UnderlineController unit tests.
 *
 * Covers:
 * - Create underline (valid payload with userId, textId, selectedText, startIndex, endIndex)
 * - Create underline with serializedRange (DOM range serialization)
 * - Get underlines by text and user (only current user's underlines)
 * - Delete underline with IDOR protection (must be owner)
 * - Missing required fields (textId)
 * - Index validation (startIndex, endIndex)
 * - User not found error
 * - Underline not found error
 * - Authentication required checks
 * - Service exception handling
 */
@DisplayName("UnderlineController Enhanced Tests")
class UnderlineControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UnderlineService underlineService;

    @Mock
    private UserService userService;

    @InjectMocks
    private UnderlineController underlineController;

    private User studentUser;
    private User otherStudent;
    private Underline underline1;
    private Underline underline2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(underlineController)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        setUpSecurityContext("student1", "ROLE_USER");

        studentUser = new User();
        studentUser.setId(1L);
        studentUser.setUsername("student1");
        studentUser.setRole("USER");
        when(userService.getUserByUsername("student1")).thenReturn(studentUser);

        otherStudent = new User();
        otherStudent.setId(2L);
        otherStudent.setUsername("student2");
        otherStudent.setRole("USER");

        underline1 = new Underline();
        underline1.setId(10L);
        underline1.setUserId(1L);
        underline1.setTextId(100L);
        underline1.setSelectedText("important text");
        underline1.setStartIndex(5);
        underline1.setEndIndex(19);

        underline2 = new Underline();
        underline2.setId(11L);
        underline2.setUserId(2L);
        underline2.setTextId(100L);
        underline2.setSelectedText("other underline");
        underline2.setStartIndex(25);
        underline2.setEndIndex(40);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ========== Helpers ==========

    private void setUpSecurityContext(String username, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null, Collections.singletonList(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Map<String, Object> buildCreateUnderlineRequest(Long textId, String selectedText,
                                                            int startIndex, int endIndex) {
        Map<String, Object> req = new HashMap<>();
        req.put("textId", textId);
        req.put("selectedText", selectedText);
        req.put("startIndex", startIndex);
        req.put("endIndex", endIndex);
        return req;
    }

    private Map<String, Object> buildCreateUnderlineRequestWithRange(Long textId, String selectedText,
                                                                      int startIndex, int endIndex,
                                                                      String serializedRange) {
        Map<String, Object> req = buildCreateUnderlineRequest(textId, selectedText, startIndex, endIndex);
        req.put("serializedRange", serializedRange);
        return req;
    }

    // ========== 1. Create Underline Basic ==========

    @Test
    @DisplayName("POST /api/underlines - create underline with basic fields success")
    void createUnderline_basicFields_success() throws Exception {
        doNothing().when(underlineService).createUnderline(
                eq(1L), eq(100L), eq("important text"), eq(5), eq(19), any());

        Map<String, Object> req = buildCreateUnderlineRequest(100L, "important text", 5, 19);

        mockMvc.perform(post("/api/underlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(underlineService).createUnderline(
                eq(1L), eq(100L), eq("important text"), eq(5), eq(19), any());
    }

    @Test
    @DisplayName("POST /api/underlines - create underline with serializedRange success")
    void createUnderline_withSerializedRange_success() throws Exception {
        String range = "{\"start\": 5, \"end\": 19}";
        doNothing().when(underlineService).createUnderline(
                eq(1L), eq(100L), eq("important text"), eq(5), eq(19), eq(range));

        Map<String, Object> req = buildCreateUnderlineRequestWithRange(100L, "important text", 5, 19, range);

        mockMvc.perform(post("/api/underlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(underlineService).createUnderline(
                eq(1L), eq(100L), eq("important text"), eq(5), eq(19), eq(range));
    }

    @Test
    @DisplayName("POST /api/underlines - create underline without indices (only selectedText)")
    void createUnderline_withoutIndices_success() throws Exception {
        doNothing().when(underlineService).createUnderline(
                eq(1L), eq(100L), eq("some text"), isNull(), isNull(), isNull());

        Map<String, Object> req = new HashMap<>();
        req.put("textId", 100L);
        req.put("selectedText", "some text");

        mockMvc.perform(post("/api/underlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(underlineService).createUnderline(
                eq(1L), eq(100L), eq("some text"), isNull(), isNull(), isNull());
    }

    // ========== 2. Missing Required Fields ==========

    @Test
    @DisplayName("POST /api/underlines - missing textId returns error")
    void createUnderline_missingTextId_returnsError() throws Exception {
        Map<String, Object> req = new HashMap<>();
        // Missing textId
        req.put("selectedText", "important text");
        req.put("startIndex", 5);
        req.put("endIndex", 19);

        mockMvc.perform(post("/api/underlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("textId不能为空"));

        verify(underlineService, never()).createUnderline(anyLong(), anyLong(), anyString(),
                any(Integer.class), any(Integer.class), any());
    }

    @Test
    @DisplayName("POST /api/underlines - null textId returns error")
    void createUnderline_nullTextId_returnsError() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("textId", null);
        req.put("selectedText", "important text");
        req.put("startIndex", 5);
        req.put("endIndex", 19);

        mockMvc.perform(post("/api/underlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("textId不能为空"));

        verify(underlineService, never()).createUnderline(anyLong(), anyLong(), anyString(),
                any(Integer.class), any(Integer.class), any());
    }

    // ========== 3. Index Validation ==========

    @Test
    @DisplayName("POST /api/underlines - zero startIndex success")
    void createUnderline_zeroStartIndex_success() throws Exception {
        doNothing().when(underlineService).createUnderline(
                eq(1L), eq(100L), eq("text"), eq(0), eq(4), any());

        Map<String, Object> req = buildCreateUnderlineRequest(100L, "text", 0, 4);

        mockMvc.perform(post("/api/underlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(underlineService).createUnderline(
                eq(1L), eq(100L), eq("text"), eq(0), eq(4), any());
    }

    @Test
    @DisplayName("POST /api/underlines - large indices success")
    void createUnderline_largeIndices_success() throws Exception {
        doNothing().when(underlineService).createUnderline(
                eq(1L), eq(100L), eq("text"), eq(1000), eq(2000), any());

        Map<String, Object> req = buildCreateUnderlineRequest(100L, "text", 1000, 2000);

        mockMvc.perform(post("/api/underlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(underlineService).createUnderline(
                eq(1L), eq(100L), eq("text"), eq(1000), eq(2000), any());
    }

    // ========== 4. Get Underlines By Text ==========

    @Test
    @DisplayName("GET /api/underlines/text/{textId} - get student's underlines by text success")
    void getUnderlines_success() throws Exception {
        List<Underline> underlines = List.of(underline1);
        when(underlineService.getUnderlinesByTextIdAndUserId(100L, 1L)).thenReturn(underlines);

        mockMvc.perform(get("/api/underlines/text/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(10L))
                .andExpect(jsonPath("$.data[0].selectedText").value("important text"));

        verify(underlineService).getUnderlinesByTextIdAndUserId(100L, 1L);
    }

    @Test
    @DisplayName("GET /api/underlines/text/{textId} - no underlines returns empty array")
    void getUnderlines_empty_returnsEmpty() throws Exception {
        when(underlineService.getUnderlinesByTextIdAndUserId(200L, 1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/underlines/text/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(underlineService).getUnderlinesByTextIdAndUserId(200L, 1L);
    }

    @Test
    @DisplayName("GET /api/underlines/text/{textId} - gets only current user's underlines")
    void getUnderlines_onlyCurrenUserUnderlines() throws Exception {
        // Service only returns underlines for current user (student1, id=1)
        when(underlineService.getUnderlinesByTextIdAndUserId(100L, 1L)).thenReturn(List.of(underline1));

        // Other student's underline should not be returned
        when(underlineService.getUnderlinesByTextIdAndUserId(100L, 2L)).thenReturn(List.of(underline2));

        mockMvc.perform(get("/api/underlines/text/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].userId").value(1L));

        verify(underlineService).getUnderlinesByTextIdAndUserId(100L, 1L);
    }

    // ========== 5. Delete Underline ==========

    @Test
    @DisplayName("DELETE /api/underlines/{underlineId} - delete own underline success")
    void deleteUnderline_ownUnderline_success() throws Exception {
        when(underlineService.getUnderlineById(10L)).thenReturn(underline1);
        when(underlineService.deleteUnderline(10L, 1L)).thenReturn(true);

        mockMvc.perform(delete("/api/underlines/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(underlineService).deleteUnderline(10L, 1L);
    }

    // ========== 6. IDOR Protection on Delete ==========

    @Test
    @DisplayName("DELETE /api/underlines/{underlineId} - cannot delete other user's underline")
    void deleteUnderline_idorProtection_returnsError() throws Exception {
        // underline2 belongs to student2, but current user is student1
        when(underlineService.getUnderlineById(11L)).thenReturn(underline2);

        mockMvc.perform(delete("/api/underlines/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限删除此划线"));

        verify(underlineService, never()).deleteUnderline(11L, 1L);
    }

    @Test
    @DisplayName("DELETE /api/underlines/{underlineId} - other user cannot delete returns error")
    void deleteUnderline_otherUserDelete_returnsError() throws Exception {
        // Switch to student2 context
        setUpSecurityContext("student2", "ROLE_USER");
        when(userService.getUserByUsername("student2")).thenReturn(otherStudent);

        // Try to delete student1's underline
        when(underlineService.getUnderlineById(10L)).thenReturn(underline1);

        mockMvc.perform(delete("/api/underlines/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限删除此划线"));

        verify(underlineService, never()).deleteUnderline(10L, 2L);
    }

    // ========== 7. Delete Non-Existent Underline ==========

    @Test
    @DisplayName("DELETE /api/underlines/{underlineId} - underline not found returns error")
    void deleteUnderline_notFound_returnsError() throws Exception {
        when(underlineService.getUnderlineById(999L)).thenReturn(null);

        mockMvc.perform(delete("/api/underlines/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("划线记录不存在"));

        verify(underlineService, never()).deleteUnderline(anyLong(), anyLong());
    }

    // ========== 8. Delete Failure ==========

    @Test
    @DisplayName("DELETE /api/underlines/{underlineId} - delete operation fails")
    void deleteUnderline_deleteFails_returnsError() throws Exception {
        when(underlineService.getUnderlineById(10L)).thenReturn(underline1);
        when(underlineService.deleteUnderline(10L, 1L)).thenReturn(false);

        mockMvc.perform(delete("/api/underlines/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("删除失败，请稍后重试"));

        verify(underlineService).deleteUnderline(10L, 1L);
    }

    // ========== 9. User Not Found ==========

    @Test
    @DisplayName("POST /api/underlines - user not found returns error")
    void createUnderline_userNotFound_returnsError() throws Exception {
        when(userService.getUserByUsername("student1")).thenReturn(null);

        Map<String, Object> req = buildCreateUnderlineRequest(100L, "text", 5, 19);

        mockMvc.perform(post("/api/underlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));

        verify(underlineService, never()).createUnderline(anyLong(), anyLong(), anyString(),
                any(Integer.class), any(Integer.class), any());
    }

    @Test
    @DisplayName("GET /api/underlines/text/{textId} - user not found returns error")
    void getUnderlines_userNotFound_returnsError() throws Exception {
        when(userService.getUserByUsername("student1")).thenReturn(null);

        mockMvc.perform(get("/api/underlines/text/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));

        verify(underlineService, never()).getUnderlinesByTextIdAndUserId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("DELETE /api/underlines/{underlineId} - user not found returns error")
    void deleteUnderline_userNotFound_returnsError() throws Exception {
        when(userService.getUserByUsername("student1")).thenReturn(null);

        mockMvc.perform(delete("/api/underlines/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));

        verify(underlineService, never()).deleteUnderline(anyLong(), anyLong());
    }

    // ========== 10. Service Exception Handling ==========

    @Test
    @DisplayName("POST /api/underlines - service throws exception returns error")
    void createUnderline_serviceThrowsException_returnsError() throws Exception {
        doThrow(new RuntimeException("Database error"))
                .when(underlineService).createUnderline(anyLong(), anyLong(), anyString(),
                        any(Integer.class), any(Integer.class), any());

        Map<String, Object> req = buildCreateUnderlineRequest(100L, "text", 5, 19);

        mockMvc.perform(post("/api/underlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("创建划线失败，请稍后重试"));
    }

    @Test
    @DisplayName("GET /api/underlines/text/{textId} - service throws exception returns error")
    void getUnderlines_serviceThrowsException_returnsError() throws Exception {
        when(underlineService.getUnderlinesByTextIdAndUserId(100L, 1L))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/api/underlines/text/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取划线失败，请稍后重试"));
    }

    @Test
    @DisplayName("DELETE /api/underlines/{underlineId} - service throws exception returns error")
    void deleteUnderline_serviceThrowsException_returnsError() throws Exception {
        when(underlineService.getUnderlineById(10L)).thenReturn(underline1);
        when(underlineService.deleteUnderline(10L, 1L))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(delete("/api/underlines/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("删除划线失败，请稍后重试"));
    }
}
