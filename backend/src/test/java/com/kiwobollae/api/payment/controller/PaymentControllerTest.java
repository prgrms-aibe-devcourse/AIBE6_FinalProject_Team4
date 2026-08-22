package com.kiwobollae.api.payment.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import com.kiwobollae.api.payment.dto.request.PaymentFailureRequest;
import com.kiwobollae.api.payment.dto.request.PaymentRequest;
import com.kiwobollae.api.payment.dto.request.PaymentRefundRequest;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundStatus;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
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
	void chargeAcceptsDirectPointAmount() throws Exception {
		PaymentResponse response = new PaymentResponse(
				21L,
				7L,
				12_340L,
				12_340L,
				PaymentStatus.PENDING,
				PaymentProviderType.TOSS,
				"KWB-order-21",
				null,
				null,
				LocalDateTime.of(2026, 8, 21, 10, 0),
				"결제 요청이 생성되었습니다."
		);
		given(paymentService.requestCharge(7L, "charge-key", new PaymentRequest(12_340L)))
				.willReturn(response);

		mockMvc.perform(post("/api/v1/payments/charge")
						.header("Idempotency-Key", "charge-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pointAmount":12340}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.cashAmount").value(12340))
				.andExpect(jsonPath("$.data.pointAmount").value(12340));

		verify(paymentService).requestCharge(7L, "charge-key", new PaymentRequest(12_340L));
	}

	@Test
	void chargeRejectsAmountBelowMinimum() throws Exception {
		mockMvc.perform(post("/api/v1/payments/charge")
						.header("Idempotency-Key", "charge-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pointAmount":999}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("pointAmount"));
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

	@Test
	void canceledCallbackMarksPendingPaymentFailed() throws Exception {
		PaymentResponse response = new PaymentResponse(
				21L,
				7L,
				1_000L,
				1_000L,
				PaymentStatus.FAILED,
				PaymentProviderType.TOSS,
				"KWB-order-21",
				null,
				null,
				LocalDateTime.of(2026, 8, 3, 14, 0),
				"결제를 취소했어요."
		);
		given(paymentService.failPayment(
				7L,
				"failure-KWB-order-21",
				new PaymentFailureRequest("KWB-order-21", "PAY_PROCESS_CANCELED")
		)).willReturn(response);

		mockMvc.perform(post("/api/v1/payments/fail")
						.header("Idempotency-Key", "failure-KWB-order-21")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"providerOrderId":"KWB-order-21","code":"PAY_PROCESS_CANCELED"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("FAILED"));

		verify(paymentService).failPayment(
				7L,
				"failure-KWB-order-21",
				new PaymentFailureRequest("KWB-order-21", "PAY_PROCESS_CANCELED")
		);
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
