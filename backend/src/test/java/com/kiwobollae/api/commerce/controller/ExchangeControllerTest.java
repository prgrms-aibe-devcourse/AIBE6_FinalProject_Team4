package com.kiwobollae.api.commerce.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.commerce.dto.request.ExchangeCancelRequest;
import com.kiwobollae.api.commerce.dto.request.ExchangeOrderRequest;
import com.kiwobollae.api.commerce.dto.response.ExchangeOrderResponse;
import com.kiwobollae.api.commerce.entity.enums.ExchangeStatus;
import com.kiwobollae.api.commerce.service.ExchangeService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ExchangeControllerTest {

	@Mock private ExchangeService exchangeService;

	private MockMvc mockMvc;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		ExchangeController exchangeController = new ExchangeController(exchangeService);
		mockMvc = MockMvcBuilders.standaloneSetup(exchangeController)
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(
						new AuthenticationPrincipalArgumentResolver(),
						new PageableHandlerMethodArgumentResolver()
				)
				.build();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private void authenticateAs(Long userId) {
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
	}

	private ExchangeOrderResponse sampleResponse(Long id, ExchangeStatus status) {
		return new ExchangeOrderResponse(
				id, 7L, 1L, "수박 카드", 10L, "텀블러", 3, status, null, null, null, null,
				"홍길동", "010-1234-5678", "06236", "서울시 강남구", "101동", LocalDateTime.of(2026, 7, 28, 10, 0)
		);
	}

	@Test
	void requestExchangeReturnsCreatedOrder() throws Exception {
		authenticateAs(7L);
		ExchangeOrderRequest request = new ExchangeOrderRequest(1L, "홍길동", "010-1234-5678", "06236", "서울시 강남구", "101동");
		given(exchangeService.requestExchange(eq(7L), any(ExchangeOrderRequest.class)))
				.willReturn(sampleResponse(100L, ExchangeStatus.PREPARING));

		mockMvc.perform(post("/api/v1/exchanges")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(100))
				.andExpect(jsonPath("$.data.status").value("PREPARING"));
	}

	@Test
	void requestExchangeRejectsBlankReceiverName() throws Exception {
		authenticateAs(7L);
		ExchangeOrderRequest request = new ExchangeOrderRequest(1L, "", "010-1234-5678", "06236", "서울시 강남구", "101동");

		mockMvc.perform(post("/api/v1/exchanges")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());

		verify(exchangeService, never()).requestExchange(any(), any());
	}

	@Test
	void getMyExchangesReturnsPagedList() throws Exception {
		authenticateAs(7L);
		given(exchangeService.getMyExchanges(eq(7L), any()))
				.willReturn(new PageImpl<>(List.of(sampleResponse(1L, ExchangeStatus.PREPARING)), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/exchanges"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].id").value(1));
	}

	@Test
	void getMyExchangeReturnsDetail() throws Exception {
		authenticateAs(7L);
		given(exchangeService.getMyExchange(7L, 1L)).willReturn(sampleResponse(1L, ExchangeStatus.PREPARING));

		mockMvc.perform(get("/api/v1/exchanges/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(1));
	}

	@Test
	void getMyExchangeReturnsNotFoundError() throws Exception {
		authenticateAs(7L);
		given(exchangeService.getMyExchange(7L, 404L))
				.willThrow(new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND));

		mockMvc.perform(get("/api/v1/exchanges/404"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EXCHANGE_NOT_FOUND"));
	}

	@Test
	void cancelExchangeReturnsNoContent() throws Exception {
		authenticateAs(7L);
		ExchangeCancelRequest request = new ExchangeCancelRequest("단순 변심");

		mockMvc.perform(patch("/api/v1/exchanges/1/cancel")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNoContent());

		verify(exchangeService).cancelExchange(7L, 1L, "단순 변심");
	}

	@Test
	void cancelExchangeRejectsReasonTooLong() throws Exception {
		authenticateAs(7L);
		ExchangeCancelRequest request = new ExchangeCancelRequest("가".repeat(201));

		mockMvc.perform(patch("/api/v1/exchanges/1/cancel")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());

		verify(exchangeService, never()).cancelExchange(any(), any(), any());
	}
}
