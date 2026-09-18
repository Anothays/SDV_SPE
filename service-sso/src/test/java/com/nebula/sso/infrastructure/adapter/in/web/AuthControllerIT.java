package com.nebula.sso.infrastructure.adapter.in.web;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
// AFTER_EACH_TEST_METHOD : la base H2 (create-drop) est recréée à chaque test,
// sinon les comptes créés par un test polluent les suivants (username unique).
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    // Plus de mock Kafka nécessaire : l'inscription écrit dans la table outbox
    // (H2 la gère comme n'importe quelle autre table), Debezium publie en dehors
    // de ce test.

    private static final String ALICE = """
            {"username":"alice","email":"alice@example.com","password":"password123","region":"EU"}
            """;

    @Test
    void registerReturns201WithPlayerIdAndToken() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(ALICE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").value(not(emptyString())))
                .andExpect(jsonPath("$.token").value(not(emptyString())));
    }

    @Test
    void registerReturns409OnDuplicate() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(ALICE))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(ALICE))
                .andExpect(status().isConflict());
    }

    @Test
    void registerReturns400OnInvalidBody() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"email\":\"pas-un-email\",\"password\":\"court\",\"region\":\"MARS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsTokenThenRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(ALICE))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(not(emptyString())));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"mauvais\"}"))
                .andExpect(status().isUnauthorized());
    }
}
