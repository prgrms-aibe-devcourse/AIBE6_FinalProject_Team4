package com.kiwobollae.api.point.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.global.security.JwtTokenProvider;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentRequest;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentResponse;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.service.AdminPointAdjustmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
				91L, 7L, CurrencyType.FREE, 100L, 400L, 500L, 400L, 900L
		);
		given(adminPointAdjustmentService.adjust(1L, "adjust-key", request)).willReturn(response);

		mockMvc.perform(request().header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.transactionId").value(91));

		verify(adminPointAdjustmentService).adjust(1L, "adjust-key", request);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() throws Exception {
		return post("/api/v1/admin/point/adjust")
				.header("Idempotency-Key", "adjust-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(adjustmentRequest()));
	}

	private AdminPointAdjustmentRequest adjustmentRequest() {
		return new AdminPointAdjustmentRequest(7L, CurrencyType.FREE, 100L);
	}
}
