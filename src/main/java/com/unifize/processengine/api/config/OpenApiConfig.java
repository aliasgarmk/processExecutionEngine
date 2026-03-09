package com.unifize.processengine.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Process Execution Engine API")
                        .version("0.1.0")
                        .description("""
                                REST API for driving structured, multi-step business processes.

                                **Typical flow:**
                                1. `POST /api/users` — register actors
                                2. `POST /api/definitions` — define the workflow blueprint
                                3. `POST /api/instances` — start a run; save the returned `instanceId`
                                4. `POST /api/instances/{instanceId}/steps/{stepId}/open` — open the active step
                                5. `POST /api/instances/{instanceId}/steps/{stepId}/advance` — submit/approve/reject
                                6. Repeat 4-5 for each subsequent step until `processCompleted: true`
                                """));
    }
}
