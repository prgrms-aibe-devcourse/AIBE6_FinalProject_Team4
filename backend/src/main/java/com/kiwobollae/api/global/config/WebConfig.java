package com.kiwobollae.api.global.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private static final List<String> ALLOWED_ORIGIN_PATTERNS = List.of(
			"http://localhost:*",
			"http://127.0.0.1:*"
	);

	private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS.toArray(new String[0]))
				.allowedMethods(ALLOWED_METHODS.toArray(new String[0]))
				.allowedHeaders("*")
				.allowCredentials(true);
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
		configuration.setAllowedMethods(ALLOWED_METHODS);
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
