package com.example.MaDemo.controller;

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
import org.springframework.transaction.annotation.Transactional;

import com.example.dto.ProfilDto;
import com.example.repository.ProfilRepository;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfilControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfilRepository profilRepository;

    @BeforeEach
    void seedProfil() {
        ProfilDto dto = new ProfilDto();
        dto.setPlayerId("uuid-1");
        dto.setUsername("alice");
        dto.setRegion("EU");
        dto.setLevel(1);
        profilRepository.save(dto);
    }

    @Test
    void getReturnsProfilByPlayerId() throws Exception {
        mockMvc.perform(get("/api/profils/uuid-1").with(httpBasic("ali", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.level").value(1));
    }

    @Test
    void getReturns404ForUnknownPlayer() throws Exception {
        mockMvc.perform(get("/api/profils/inconnu").with(httpBasic("ali", "password123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void putUpdatesRegionOnly() throws Exception {
        mockMvc.perform(put("/api/profils/uuid-1").with(httpBasic("ali", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"NA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("NA"))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void postCreateIsGone() throws Exception {
        mockMvc.perform(post("/api/profils").with(httpBasic("ali", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"direct\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void isProtectedByAuthentication() throws Exception {
        mockMvc.perform(get("/api/profils/uuid-1"))
                .andExpect(status().isUnauthorized());
    }
}
