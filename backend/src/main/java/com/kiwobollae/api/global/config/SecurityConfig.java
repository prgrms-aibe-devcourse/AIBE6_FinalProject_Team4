package com.kiwobollae.api.global.config;

import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.security.JwtAccessDeniedHandler;
import com.kiwobollae.api.global.security.JwtAuthenticationEntryPoint;
import com.kiwobollae.api.global.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
						// More specific than the /auth/** permitAll below: viewing/editing "my"
						// profile (and sub-resources like changing my password) requires a valid
						// access token, unlike signup/login/reissue/logout.
						.requestMatchers(ApiVersion.V1 + "/auth/me/**").authenticated()
						.requestMatchers(ApiVersion.V1 + "/auth/me").authenticated()
						.requestMatchers(ApiVersion.V1 + "/admin/**").hasRole("ADMIN")
						.requestMatchers(ApiVersion.V1 + "/auth/**").permitAll()
						.requestMatchers(HttpMethod.GET, ApiVersion.V1 + "/product").permitAll()
						.requestMatchers(HttpMethod.GET, ApiVersion.V1 + "/product/**").permitAll()
						.requestMatchers(HttpMethod.GET, ApiVersion.V1 + "/card").permitAll()
						.requestMatchers(HttpMethod.GET, ApiVersion.V1 + "/card/**").permitAll()
						// <img> 태그는 Authorization 헤더를 못 보내므로, private S3 버킷을 대신 서빙하는
						// 이 엔드포인트만 GET에 한해 공개한다(파일명이 UUID라 URL 추측은 사실상 불가능).
						.requestMatchers(HttpMethod.GET, ApiVersion.V1 + "/journals/images/**").permitAll()
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
