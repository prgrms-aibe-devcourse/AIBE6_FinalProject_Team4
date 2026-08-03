package com.kiwobollae.api.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.auth.service.AdminUserQueryService;
import com.kiwobollae.api.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_admin_user_auth_test"
				+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AdminUserControllerAuthorizationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider jwtTokenProvider;

	@MockitoBean private AdminUserQueryService adminUserQueryService;

	@Test
	void anonymousUserCannotSearchUsers() throws Exception {
		mockMvc.perform(get("/api/v1/admin/user"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"));
	}

	@Test
	void regularUserCannotSearchUsers() throws Exception {
		String token = jwtTokenProvider.generateAccessToken(2L, "USER");

		mockMvc.perform(get("/api/v1/admin/user").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
	}

	@Test
	void adminCanSearchUsers() throws Exception {
		String token = jwtTokenProvider.generateAccessToken(1L, "ADMIN");
		given(adminUserQueryService.search(isNull(), isNull(), any(Pageable.class)))
				.willReturn(Page.empty());

		mockMvc.perform(get("/api/v1/admin/user").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}
}
