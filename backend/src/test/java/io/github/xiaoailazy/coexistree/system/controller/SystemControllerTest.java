package io.github.xiaoailazy.coexistree.system.controller;

import io.github.xiaoailazy.coexistree.system.dto.CreateSystemRequest;
import io.github.xiaoailazy.coexistree.system.dto.SystemResponse;
import io.github.xiaoailazy.coexistree.system.dto.UpdateSystemRequest;
import io.github.xiaoailazy.coexistree.system.service.SystemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemService systemService;

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testListSystems() throws Exception {
        // Given
        SystemResponse sys1 = createSystemResponse(1L, "ops", "运维系统");
        SystemResponse sys2 = createSystemResponse(2L, "crm", "客户管理系统");

        when(systemService.list(any())).thenReturn(List.of(sys1, sys2));

        // When & Then
        mockMvc.perform(get("/api/v1/systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].systemCode").value("ops"))
                .andExpect(jsonPath("$.data[1].systemCode").value("crm"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testCreateSystem() throws Exception {
        // Given
    
        SystemResponse response = createSystemResponse(1L, "newsys", "新系统");

        when(systemService.create(any(CreateSystemRequest.class), any())).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "systemCode": "newsys",
                                    "systemName": "新系统",
                                    "description": "系统描述"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.systemCode").value("newsys"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testCreateSystemValidationFailure() throws Exception {
        // When & Then - systemCode is blank
        mockMvc.perform(post("/api/v1/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "systemCode": "",
                                    "systemName": "新系统"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testGetSystemById() throws Exception {
        // Given
        Long systemId = 1L;
        SystemResponse response = createSystemResponse(systemId, "ops", "运维系统");

        when(systemService.get(systemId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/systems/{id}", systemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.systemCode").value("ops"))
                .andExpect(jsonPath("$.data.systemName").value("运维系统"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testUpdateSystem() throws Exception {
        // Given
        Long systemId = 1L;
        SystemResponse response = createSystemResponse(systemId, "ops", "更新的运维系统");

        when(systemService.update(eq(systemId), any(UpdateSystemRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(put("/api/v1/systems/{id}", systemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "systemName": "更新的运维系统",
                                    "description": "更新后的描述"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.systemName").value("更新的运维系统"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testDeleteSystem() throws Exception {
        // Given
        Long systemId = 1L;
        doNothing().when(systemService).delete(systemId);

        // When & Then
        mockMvc.perform(delete("/api/v1/systems/{id}", systemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testListEmptySystems() throws Exception {
        // Given
        when(systemService.list(any())).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/v1/systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testCreateSystemWithSpecialCharacters() throws Exception {
        // Given
        SystemResponse response = createSystemResponse(1L, "sys-001", "系统【测试】");
        when(systemService.create(any(CreateSystemRequest.class), any())).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "systemCode": "sys-001",
                                    "systemName": "系统【测试】",
                                    "description": "描述（带括号）"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemName").value("系统【测试】"));
    }

    private SystemResponse createSystemResponse(Long id, String code, String name) {
        return new SystemResponse(
                id,
                code,
                name,
                "描述",
                "ACTIVE"
        );
    }
}
