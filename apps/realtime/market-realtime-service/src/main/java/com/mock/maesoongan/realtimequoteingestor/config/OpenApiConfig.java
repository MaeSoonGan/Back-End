package com.mock.maesoongan.realtimequoteingestor.config;

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
                        .title("MaeSoonGan Market Realtime Service API")
                        .description("MaeSoonGan market realtime quote ingestor API specification")
                        .version("v1"));
    }
}
