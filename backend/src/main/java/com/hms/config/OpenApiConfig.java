package com.hms.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI hmsOpenApi() {
        return new OpenAPI().info(new Info()
            .title("HMS — Hospital Management System API")
            .description("Full-stack HMS REST API — PostgreSQL 16 / Spring Boot 3.3 / Java 21")
            .version("1.0.0"));
    }
}
