package com.kiwobollae.api.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME_NAME = "bearerAuth";

	@Bean
	public OpenAPI openAPI() {
		SecurityScheme bearerScheme = new SecurityScheme()
				.type(SecurityScheme.Type.HTTP)
				.scheme("bearer")
				.bearerFormat("JWT")
				.in(SecurityScheme.In.HEADER)
				.name("Authorization");

		return new OpenAPI()
				.info(new Info()
						.title("키워볼래 API")
						.description("키워볼래 백엔드 API 문서")
						.version("v1"))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME, bearerScheme))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
	}
}
