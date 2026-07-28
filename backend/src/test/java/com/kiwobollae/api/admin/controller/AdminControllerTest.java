package com.kiwobollae.api.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.admin.service.ExchangeManagementService;
import com.kiwobollae.api.commerce.dto.request.ExchangeCancelRequest;
import com.kiwobollae.api.commerce.dto.response.ExchangeOrderResponse;
import com.kiwobollae.api.commerce.entity.enums.ExchangeStatus;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

	@Mock private ExchangeManagementService exchangeManagementService;

	private MockMvc mockMvc;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		AdminController adminController = new AdminController(exchangeManagementService);
		mockMvc = MockMvcBuilders.standaloneSetup(adminController)
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
				.build();
	}

	private ExchangeOrderResponse sampleResponse(Long id, ExchangeStatus status) {
		return new ExchangeOrderResponse(
				id, 7L, 1L, "수박 카드", 10L, "텀블러", 3, status, null, null, null, null,
				"홍길동", "010-1234-5678", "서울시 강남구", "101동", LocalDateTime.of(2026, 7, 28, 10, 0)
		);
	}

	@Test
	void getExchangesReturnsPagedList() throws Exception {
		given(exchangeManagementService.getExchangesForAdmin(eq(ExchangeStatus.REQUESTED), any()))
				.willReturn(new PageImpl<>(List.of(sampleResponse(1L, ExchangeStatus.REQUESTED)), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/admin/exchanges").param("status", "REQUESTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].id").value(1));
	}

	@Test
	void prepareExchangeReturnsUpdatedOrder() throws Exception {
		given(exchangeManagementService.prepareExchange(1L)).willReturn(sampleResponse(1L, ExchangeStatus.PREPARING));

		mockMvc.perform(patch("/api/v1/admin/exchanges/1/prepare"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PREPARING"));
	}

	@Test
	void prepareExchangeReturnsInvalidStateError() throws Exception {
		given(exchangeManagementService.prepareExchange(1L))
				.willThrow(new BusinessException(ErrorCode.EXCHANGE_INVALID_STATE));

		mockMvc.perform(patch("/api/v1/admin/exchanges/1/prepare"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EXCHANGE_INVALID_STATE"));
	}

	@Test
	void shipExchangeReturnsUpdatedOrder() throws Exception {
		given(exchangeManagementService.shipExchange(1L)).willReturn(sampleResponse(1L, ExchangeStatus.SHIPPING));

		mockMvc.perform(patch("/api/v1/admin/exchanges/1/ship"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SHIPPING"));
	}

	@Test
	void deliverExchangeReturnsUpdatedOrder() throws Exception {
		given(exchangeManagementService.deliverExchange(1L)).willReturn(sampleResponse(1L, ExchangeStatus.DELIVERED));

		mockMvc.perform(patch("/api/v1/admin/exchanges/1/deliver"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DELIVERED"));
	}

	@Test
	void cancelExchangeReturnsCancelledOrder() throws Exception {
		ExchangeCancelRequest request = new ExchangeCancelRequest("품절");
		given(exchangeManagementService.adminCancelExchange(1L, "품절"))
				.willReturn(sampleResponse(1L, ExchangeStatus.CANCELLED));

		mockMvc.perform(patch("/api/v1/admin/exchanges/1/cancel")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CANCELLED"));
	}

	@Test
	void cancelExchangeRejectsReasonTooLong() throws Exception {
		ExchangeCancelRequest request = new ExchangeCancelRequest("가".repeat(201));

		mockMvc.perform(patch("/api/v1/admin/exchanges/1/cancel")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
	}
}
