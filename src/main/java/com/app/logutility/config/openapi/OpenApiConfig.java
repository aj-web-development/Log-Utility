package com.app.logutility.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Powers the auto-generated {@code /v3/api-docs} and {@code /swagger-ui.html}. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI logutilityOpenApi() {
        String basicAuth = "basicAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Log Utility API")
                        .description("Search another application's logs and configure how it's searched — "
                                + "REST endpoints under /api/search/** (public) and /api/projects/** "
                                + "(HTTP Basic, the admin account).")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(basicAuth))
                .schemaRequirement(basicAuth, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic"));
    }
}
