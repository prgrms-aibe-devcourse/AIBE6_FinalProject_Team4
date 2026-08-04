package com.kiwobollae.api.point.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.global.security.JwtTokenProvider;
import com.kiwobollae.api.point.service.PointTransactionService;
import com.kiwobollae.api.point.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_point_activity_auth_test"
				+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class PointControllerAuthorizationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider jwtTokenProvider;

	@MockitoBean private WalletService walletService;
	@MockitoBean private PointTransactionService pointTransactionService;

	@Test
	void anonymousUserCannotViewPointActivities() throws Exception {
		mockMvc.perform(get("/api/v1/points/activities"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"));
	}

	@Test
	void authenticatedUserCanViewOwnPointActivities() throws Exception {
		String token = jwtTokenProvider.generateAccessToken(7L, "USER");
		given(pointTransactionService.getActivities(
				org.mockito.ArgumentMatchers.eq(7L),
				isNull(),
				isNull(),
				isNull(),
				isNull(),
				any()
		)).willReturn(Page.empty());

		mockMvc.perform(get("/api/v1/points/activities")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}
}
