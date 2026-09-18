package com.nebula.rolemanager.infrastructure.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.nebula.rolemanager.domain.RoleAssignment;
import com.nebula.rolemanager.domain.port.out.RoleAssignmentPort;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleAssignmentPort roleAssignmentPort;

    @BeforeEach
    void seedAssignment() {
        roleAssignmentPort.save(RoleAssignment.defaultFor("uuid-1"));
    }

    @Test
    void getReturnsAssignmentByPlayerId() throws Exception {
        mockMvc.perform(get("/api/roles/uuid-1").with(httpBasic("ali", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value("uuid-1"))
                .andExpect(jsonPath("$.role").value("PLAYER"));
    }

    @Test
    void getReturns404ForUnknownPlayer() throws Exception {
        mockMvc.perform(get("/api/roles/inconnu").with(httpBasic("ali", "password123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void putAsAdminChangesRole() throws Exception {
        mockMvc.perform(put("/api/roles/uuid-1").with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MODERATOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"));

        mockMvc.perform(get("/api/roles/uuid-1").with(httpBasic("ali", "password123")))
                .andExpect(jsonPath("$.role").value("MODERATOR"));
    }

    @Test
    void putAsSimpleUserIsForbidden() throws Exception {
        mockMvc.perform(put("/api/roles/uuid-1").with(httpBasic("ali", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void putUnknownRoleIsBadRequest() throws Exception {
        mockMvc.perform(put("/api/roles/uuid-1").with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"KING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putUnknownPlayerIsNotFound() throws Exception {
        mockMvc.perform(put("/api/roles/inconnu").with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MODERATOR\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postCreateIsGone() throws Exception {
        // POST sur /api/roles/{playerId} : le chemin matche (GET/PUT y sont
        // mappés), donc Spring répond 405 — une attribution naît uniquement par
        // players.registered (spec §3.3).
        mockMvc.perform(post("/api/roles/uuid-1").with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PLAYER\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void isProtectedByAuthentication() throws Exception {
        mockMvc.perform(get("/api/roles/uuid-1"))
                .andExpect(status().isUnauthorized());
    }
}
