package com.example.ingestion.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ingestion.dto.response.RuleResponse;
import com.example.ingestion.service.RuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RuleController.class)
class RuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuleService ruleService;

    @Test
    void validRuleReturns200() throws Exception {
        when(ruleService.upsert(any()))
                .thenReturn(new RuleResponse(1L, "TESCO", "Shopping", "Groceries", 25, true));

        String payload = """
                {"keyword":"TESCO","category":"Shopping","subCategory":"Groceries",
                 "priority":25,"isActive":true}""";

        mockMvc.perform(post("/api/v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyword").value("TESCO"))
                .andExpect(jsonPath("$.category").value("Shopping"));
    }

    @Test
    void blankKeywordReturns400() throws Exception {
        String payload = """
                {"keyword":"","category":"Shopping","priority":25,"isActive":true}""";

        mockMvc.perform(post("/api/v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("keyword")));
    }
}

