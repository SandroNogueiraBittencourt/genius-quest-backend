package br.com.geniusquest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI geniusQuestOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Genius Quest API")
                .description("Documentação da API REST do Genius Quest")
                .version("v1"));
    }
}
