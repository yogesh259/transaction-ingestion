package com.example.ingestion.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ingestion.service.CategorisationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategorisationController.class)
class CategorisationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategorisationService categorisationService;

    @Test
    void triggerReturns202AndAcknowledgement() throws Exception {
        mockMvc.perform(post("/api/v1/categorise/trigger"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.message").exists());

        verify(categorisationService).triggerCategorisation();
    }
}

