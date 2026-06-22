package com.example.ingestion.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ingestionOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Transaction Ingestion & Categorisation API")
                .description("Bulk ingest transactions, categorise merchants via rules + LLM fallback, and serve analytics.")
                .version("v1"));
    }
}

