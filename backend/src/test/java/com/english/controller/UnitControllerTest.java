package com.english.controller;
import com.english.test.TestUtils;

import com.english.entity.Unit;
import com.english.entity.User;
import com.english.service.ClassService;
import com.english.service.UnitService;
import com.english.service.UserService;
import com.english.util.TestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UnitControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UnitService unitService;

    @Mock
    private UserService userService;

    @Mock
    private ClassService classService;

    @InjectMocks
    private UnitController unitController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(unitController).addFilters(TestUtils.authenticationFilter()).build();
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== GET /api/units ==========

    @Test
    @DisplayName("GET /api/units - returns paginated unit list")
    void getUnits_returnsPaginatedList() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("records", List.of(createMockUnit(1L, "Unit 1", 1L)));
        result.put("total", 1L);

        when(unitService.getUnitsByPage(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/units")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("GET /api/units - with courseId filter")
    void getUnits_withCourseIdFilter() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("records", Collections.emptyList());
        result.put("total", 0L);

        when(unitService.getUnitsByPage(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/units")
                        .param("courseId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(unitService).getUnitsByPage(paramsCaptor.capture());
        Assertions.assertEquals(5L, paramsCaptor.getValue().get("courseId"));
    }

    @Test
    @DisplayName("GET /api/units - with keyword search")
    void getUnits_withKeywordSearch() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("records", Collections.emptyList());
        result.put("total", 0L);

        when(unitService.getUnitsByPage(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/units")
                        .param("keyword", "grammar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(unitService).getUnitsByPage(paramsCaptor.capture());
        Assertions.assertEquals("grammar", paramsCaptor.getValue().get("keyword"));
    }

    // ========== GET /api/units/course/{courseId} ==========

    @Test
    @DisplayName("GET /api/units/course/{courseId} - returns units for course")
    void getUnitsByCourseId_returnsUnits() throws Exception {
        List<Unit> units = List.of(
                createMockUnit(1L, "Unit 1", 1L),
                createMockUnit(2L, "Unit 2", 1L)
        );

        when(unitService.getUnitsByCourseId(1L)).thenReturn(units);

        mockMvc.perform(get("/api/units/course/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ========== GET /api/units/{id} ==========

    @Test
    @DisplayName("GET /api/units/{id} - existing unit returns details")
    void getUnitById_existingUnit_returnsDetails() throws Exception {
        Unit unit = createMockUnit(1L, "Unit 1", 1L);
        unit.setDescription("First unit");
        unit.setOrderNum(1);

        when(unitService.getUnitById(1L)).thenReturn(unit);

        mockMvc.perform(get("/api/units/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Unit 1"))
                .andExpect(jsonPath("$.data.courseId").value(1))
                .andExpect(jsonPath("$.data.orderNum").value(1));
    }

    @Test
    @DisplayName("GET /api/units/{id} - non-existent unit returns error")
    void getUnitById_nonExistent_returnsError() throws Exception {
        when(unitService.getUnitById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/units/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单元不存在"));
    }

    // ========== POST /api/units ==========

    @Test
    @DisplayName("POST /api/units - admin creates unit successfully")
    void createUnit_success() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");
        User adminUser = TestHelper.createMockUser(1L, "admin", "ADMIN");
        when(userService.getUserByUsername("admin")).thenReturn(adminUser);

        Unit unit = new Unit();
        unit.setName("New Unit");
        unit.setCourseId(1L);
        unit.setOrderNum(1);

        doNothing().when(unitService).createUnit(any(Unit.class));

        mockMvc.perform(post("/api/units")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(unitService).createUnit(any(Unit.class));
    }

    @Test
    @DisplayName("POST /api/units - empty name returns error")
    void createUnit_emptyName_returnsError() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");

        Unit unit = new Unit();
        unit.setName("");
        unit.setCourseId(1L);

        mockMvc.perform(post("/api/units")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单元名称不能为空"));
    }

    // ========== PUT /api/units/{id} ==========

    @Test
    @DisplayName("PUT /api/units/{id} - admin updates unit, id from path, courseId preserved from existing")
    void updateUnit_setsIdFromPath() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");
        User adminUser = TestHelper.createMockUser(1L, "admin", "ADMIN");
        when(userService.getUserByUsername("admin")).thenReturn(adminUser);

        // The controller checks existence first via getUnitById
        Unit existingUnit = createMockUnit(5L, "Old Unit", 2L);
        when(unitService.getUnitById(5L)).thenReturn(existingUnit);

        Unit unit = new Unit();
        unit.setName("Updated Unit");
        unit.setCourseId(1L); // Attempt to change courseId -- controller should override with existing
        unit.setDescription("Updated description");

        doNothing().when(unitService).updateUnit(any(Unit.class));

        mockMvc.perform(put("/api/units/5")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        ArgumentCaptor<Unit> unitCaptor = ArgumentCaptor.forClass(Unit.class);
        verify(unitService).updateUnit(unitCaptor.capture());
        Assertions.assertEquals(5L, unitCaptor.getValue().getId());
        // courseId should be preserved from existing unit, not from request body
        Assertions.assertEquals(2L, unitCaptor.getValue().getCourseId());
    }

    @Test
    @DisplayName("PUT /api/units/{id} - non-existent unit returns error")
    void updateUnit_nonExistent_returnsError() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");

        when(unitService.getUnitById(5L)).thenReturn(null);

        Unit unit = new Unit();
        unit.setName("Updated Unit");

        mockMvc.perform(put("/api/units/5")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单元不存在"));
    }

    @Test
    @DisplayName("PUT /api/units/{id} - exception returns error")
    void updateUnit_exception_returnsError() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");
        User adminUser = TestHelper.createMockUser(1L, "admin", "ADMIN");
        when(userService.getUserByUsername("admin")).thenReturn(adminUser);

        Unit existingUnit = createMockUnit(5L, "Old Unit", 2L);
        when(unitService.getUnitById(5L)).thenReturn(existingUnit);

        Unit unit = new Unit();
        unit.setName("Updated Unit");

        doThrow(new RuntimeException("Update failed")).when(unitService).updateUnit(any(Unit.class));

        mockMvc.perform(put("/api/units/5")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ========== DELETE /api/units/{id} ==========

    @Test
    @DisplayName("DELETE /api/units/{id} - admin deletes unit successfully")
    void deleteUnit_success() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");
        User adminUser = TestHelper.createMockUser(1L, "admin", "ADMIN");
        when(userService.getUserByUsername("admin")).thenReturn(adminUser);

        // deleteUnit checks existence first
        Unit existingUnit = createMockUnit(1L, "Unit 1", 1L);
        when(unitService.getUnitById(1L)).thenReturn(existingUnit);

        doNothing().when(unitService).deleteUnit(1L);

        mockMvc.perform(delete("/api/units/1")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(unitService).deleteUnit(1L);
    }

    @Test
    @DisplayName("DELETE /api/units/{id} - non-existent unit returns error")
    void deleteUnit_nonExistent_returnsError() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");

        when(unitService.getUnitById(1L)).thenReturn(null);

        mockMvc.perform(delete("/api/units/1")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单元不存在"));
    }

    @Test
    @DisplayName("DELETE /api/units/{id} - exception returns error")
    void deleteUnit_exception_returnsError() throws Exception {
        Authentication auth = TestHelper.createMockAuthentication("admin", "ADMIN");
        User adminUser = TestHelper.createMockUser(1L, "admin", "ADMIN");
        when(userService.getUserByUsername("admin")).thenReturn(adminUser);

        Unit existingUnit = createMockUnit(1L, "Unit 1", 1L);
        when(unitService.getUnitById(1L)).thenReturn(existingUnit);

        doThrow(new RuntimeException("Unit has texts")).when(unitService).deleteUnit(1L);

        mockMvc.perform(delete("/api/units/1")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ========== GET /api/units/count ==========

    @Test
    @DisplayName("GET /api/units/count - returns total unit count")
    void getUnitCount_returnsCount() throws Exception {
        when(unitService.count()).thenReturn(42L);

        mockMvc.perform(get("/api/units/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(42));
    }

    // ========== Helper Methods ==========

    private Unit createMockUnit(Long id, String name, Long courseId) {
        Unit unit = new Unit();
        unit.setId(id);
        unit.setName(name);
        unit.setCourseId(courseId);
        return unit;
    }
}
