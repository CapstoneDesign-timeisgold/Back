package jiki.jiki.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Jiki API 명세서",
                version = "1.0",
                description = "Jiki 프로젝트의 Swagger API 문서"
        )
)
public class SwaggerConfig {
}
