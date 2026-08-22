package com.kiwobollae.api.point.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.security.JwtTokenProvider;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentRequest;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentResponse;
import com.kiwobollae.api.point.dto.response.WalletResponse;
import com.kiwobollae.api.point.entity.enums.AdminPointAdjustmentReason;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.service.AdminPointAdjustmentService;
import com.kiwobollae.api.point.service.AdminPointAdjustmentHistoryService;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_point_auth_test"
				+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AdminPointControllerAuthorizationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider jwtTokenProvider;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean
	private AdminPointAdjustmentService adminPointAdjustmentService;

	@MockitoBean
	private WalletService walletService;

	@MockitoBean
	private AdminPointAdjustmentHistoryService adminPointAdjustmentHistoryService;

	@MockitoBean
	private UserRepository userRepository;

	@BeforeEach
	void setUpActiveUsers() {
		given(userRepository.findStatusById(1L)).willReturn(Optional.of(UserStatus.ACTIVE));
		given(userRepository.findStatusById(2L)).willReturn(Optional.of(UserStatus.ACTIVE));
	}

	@Test
	void anonymousUserCannotAdjustPoint() throws Exception {
		mockMvc.perform(request())
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"));
	}

	@Test
	void regularUserCannotAdjustPoint() throws Exception {
		String token = jwtTokenProvider.generateAccessToken(2L, "USER");

		mockMvc.perform(request().header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
	}

	@Test
	void adminCanAdjustPoint() throws Exception {
		String token = jwtTokenProvider.generateAccessToken(1L, "ADMIN");
		AdminPointAdjustmentRequest request = adjustmentRequest();
		AdminPointAdjustmentResponse response = new AdminPointAdjustmentResponse(
				91L, 7L, CurrencyType.FREE, 100L, AdminPointAdjustmentReason.SPECIAL_EVENT,
				400L, 500L, 400L, 900L
		);
		given(adminPointAdjustmentService.adjust(1L, "adjust-key", request)).willReturn(response);

		mockMvc.perform(request().header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.transactionId").value(91));

		verify(adminPointAdjustmentService).adjust(1L, "adjust-key", request);
	}

	@Test
	void regularUserCannotViewAnotherUsersWallet() throws Exception {
		String token = jwtTokenProvider.generateAccessToken(2L, "USER");

		mockMvc.perform(get("/api/v1/admin/points/user/7/wallet")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
	}

	@Test
	void adminCanViewSelectedUsersWallet() throws Exception {
		String token = jwtTokenProvider.generateAccessToken(1L, "ADMIN");
		given(walletService.getWallet(7L)).willReturn(new WalletResponse(
				7L, 900L, 500L, 400L, LocalDateTime.of(2026, 8, 3, 10, 0)
		));

		mockMvc.perform(get("/api/v1/admin/points/user/7/wallet")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(900));
	}

	@Test
	void regularUserCannotViewAdminAdjustmentHistory() throws Exception {
		String token = jwtTokenProvider.generateAccessToken(2L, "USER");

		mockMvc.perform(get("/api/v1/admin/points/adjustments")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
	}

	@Test
	void adminCanViewAdjustmentHistory() throws Exception {
		String token = jwtTokenProvider.generateAccessToken(1L, "ADMIN");
		given(adminPointAdjustmentHistoryService.getAdjustments(
				org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.any()
		)).willReturn(Page.empty());

		mockMvc.perform(get("/api/v1/admin/points/adjustments")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() throws Exception {
		return post("/api/v1/admin/points/adjust")
				.header("Idempotency-Key", "adjust-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(adjustmentRequest()));
	}

	private AdminPointAdjustmentRequest adjustmentRequest() {
		return new AdminPointAdjustmentRequest(
				7L, CurrencyType.FREE, 100L, AdminPointAdjustmentReason.SPECIAL_EVENT);
	}
}
