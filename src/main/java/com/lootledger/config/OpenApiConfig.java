package com.lootledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI lootLedgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LootLedger API")
                .version("0.1.0")
                .description("A dupe-proof, double-entry game-economy engine. Every mutating endpoint "
                        + "requires an Idempotency-Key header; duplicates are applied at most once.")
                .license(new License().name("MIT")));
    }
}
