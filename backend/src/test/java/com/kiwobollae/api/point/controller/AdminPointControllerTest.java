package com.kiwobollae.api.point.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentRequest;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentResponse;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.service.AdminPointAdjustmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminPointControllerTest {

	@Mock private AdminPointAdjustmentService adminPointAdjustmentService;

	private MockMvc mockMvc;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		AdminPointController controller = new AdminPointController(adminPointAdjustmentService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new AdminUserArgumentResolver())
				.build();
	}

	@Test
	void adjustPointReturnsUpdatedBalancesAndLedgerId() throws Exception {
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(7L, CurrencyType.FREE, -200L);
		AdminPointAdjustmentResponse response = new AdminPointAdjustmentResponse(
				91L, 7L, CurrencyType.FREE, -200L, 100L, 500L, 100L, 600L
		);
		given(adminPointAdjustmentService.adjust(1L, "adjust-key", request)).willReturn(response);

		mockMvc.perform(post("/api/v1/admin/point/adjust")
						.header("Idempotency-Key", "adjust-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.transactionId").value(91))
				.andExpect(jsonPath("$.data.userId").value(7))
				.andExpect(jsonPath("$.data.currencyType").value("FREE"))
				.andExpect(jsonPath("$.data.amount").value(-200))
				.andExpect(jsonPath("$.data.balanceAfter").value(100))
				.andExpect(jsonPath("$.data.balance").value(600));

		verify(adminPointAdjustmentService).adjust(1L, "adjust-key", request);
	}

	@Test
	void adjustPointRejectsMissingCurrencyType() throws Exception {
		mockMvc.perform(post("/api/v1/admin/point/adjust")
						.header("Idempotency-Key", "adjust-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"userId":7,"amount":100}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("currencyType"));
	}

	@Test
	void adjustPointReturnsInsufficientBalance() throws Exception {
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(7L, CurrencyType.PAID, -501L);
		given(adminPointAdjustmentService.adjust(1L, "adjust-key", request))
				.willThrow(new BusinessException(ErrorCode.POINT_INSUFFICIENT_BALANCE));

		mockMvc.perform(post("/api/v1/admin/point/adjust")
						.header("Idempotency-Key", "adjust-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("POINT_INSUFFICIENT_BALANCE"));
	}

	private static final class AdminUserArgumentResolver implements HandlerMethodArgumentResolver {

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
		}

		@Override
		public Object resolveArgument(
				MethodParameter parameter,
				ModelAndViewContainer mavContainer,
				NativeWebRequest webRequest,
				org.springframework.web.bind.support.WebDataBinderFactory binderFactory
		) {
			return 1L;
		}
	}
}
