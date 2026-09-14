package br.com.qsoft.governance.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do OpenAPI/Swagger UI (dashboard da API).
 * Define título/versão e o esquema de segurança Bearer (JWT, conforme ADR-30)
 * para os endpoints protegidos. A UI fica em /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI qsoftOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("QSOFT AI Governance API")
                .description("API REST do Monólito Modular de Governança de IA — prefixo /api/v1")
                .version("v1"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Cole o JWT retornado por POST /api/v1/auth/login")));
    }
}
