package com.kiwobollae.api.payment.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import com.kiwobollae.api.payment.dto.request.PaymentRefundRequest;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundStatus;
import com.kiwobollae.api.payment.service.PaymentRefundService;
import com.kiwobollae.api.payment.service.PaymentService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

	@Mock
	private PaymentService paymentService;

	@Mock
	private PaymentRefundService paymentRefundService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		PaymentController paymentController = new PaymentController(
				paymentService,
				paymentRefundService
		);
		mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new AuthenticatedUserArgumentResolver())
				.build();
	}

	@Test
	void refundReturnsCompletedFullRefund() throws Exception {
		PaymentRefundResponse response = new PaymentRefundResponse(
				31L,
				21L,
				5_000L,
				5_000L,
				PaymentRefundStatus.COMPLETED,
				"사용자 요청",
				"provider-refund-key",
				LocalDateTime.of(2026, 7, 31, 10, 0),
				LocalDateTime.of(2026, 7, 31, 10, 0)
		);
		given(paymentRefundService.refund(
				7L,
				"refund-key",
				21L,
				new PaymentRefundRequest("사용자 요청")
		)).willReturn(response);

		mockMvc.perform(post("/api/v1/payments/21/refund")
						.header("Idempotency-Key", "refund-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason":"사용자 요청"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(31))
				.andExpect(jsonPath("$.data.paymentId").value(21))
				.andExpect(jsonPath("$.data.cashAmount").value(5000))
				.andExpect(jsonPath("$.data.pointAmount").value(5000))
				.andExpect(jsonPath("$.data.status").value("COMPLETED"));

		verify(paymentRefundService).refund(
				7L,
				"refund-key",
				21L,
				new PaymentRefundRequest("사용자 요청")
		);
	}

	@Test
	void refundRejectsInvalidPaymentId() throws Exception {
		mockMvc.perform(post("/api/v1/payments/not-a-number/refund")
						.header("Idempotency-Key", "refund-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason":"사용자 요청"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void refundRejectsBlankReason() throws Exception {
		mockMvc.perform(post("/api/v1/payments/21/refund")
						.header("Idempotency-Key", "refund-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason":" "}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
	}

	private static final class AuthenticatedUserArgumentResolver
			implements HandlerMethodArgumentResolver {

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
			return 7L;
		}
	}
}
