package com.example.ingestion.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ingestion.dto.response.BulkIngestResponse;
import com.example.ingestion.service.TransactionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void bulkIngestReturns200AndSummary() throws Exception {
        when(transactionService.ingestBulk(anyList()))
                .thenReturn(new BulkIngestResponse(1, 0, List.of()));

        String payload = """
                [{"merchantName":"ACME","amount":10.0,"currency":"USD",
                  "transactionDate":"2026-01-01","source":"MANUAL"}]""";

        mockMvc.perform(post("/api/v1/transactions/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(0));
    }

    @Test
    void malformedJsonReturns400StructuredBody() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/v1/transactions/bulk"));
    }
}

