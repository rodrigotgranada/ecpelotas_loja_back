package com.esporteclubepelotas.loja;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LojaApiApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Health check deve retornar 200 UP e stage LOCAL nos testes locais")
    void shouldReturnHealthUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.healthy").value(true))
                .andExpect(jsonPath("$.stage").value("LOCAL"));
    }

    @Test
    @DisplayName("Endpoint /auth/env-test deve retornar stage LOCAL em ambiente local")
    void shouldReturnEnvTestLocal() throws Exception {
        mockMvc.perform(get("/auth/env-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("LOCAL"))
                .andExpect(jsonPath("$.environment").value("local"));
    }
}
