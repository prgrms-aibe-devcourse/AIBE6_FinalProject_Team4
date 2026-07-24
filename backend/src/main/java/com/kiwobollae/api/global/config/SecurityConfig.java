package com.kiwobollae.api.global.config;

import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.security.JwtAccessDeniedHandler;
import com.kiwobollae.api.global.security.JwtAuthenticationEntryPoint;
import com.kiwobollae.api.global.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
	private final CorsConfigurationSource corsConfigurationSource;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(ApiVersion.V1 + "/auth/**").permitAll()
						.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
						.anyRequest().authenticated()
				)
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(jwtAuthenticationEntryPoint)
						.accessDeniedHandler(jwtAccessDeniedHandler)
				)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
