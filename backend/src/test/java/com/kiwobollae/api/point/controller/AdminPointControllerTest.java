package com.kiwobollae.api.point.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentRequest;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentDirection;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentHistoryResponse;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentResponse;
import com.kiwobollae.api.point.dto.response.WalletResponse;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.service.AdminPointAdjustmentService;
import com.kiwobollae.api.point.service.AdminPointAdjustmentHistoryService;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import java.util.List;
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
	@Mock private AdminPointAdjustmentHistoryService adminPointAdjustmentHistoryService;
	@Mock private WalletService walletService;

	private MockMvc mockMvc;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		AdminPointController controller = new AdminPointController(
				adminPointAdjustmentService,
				adminPointAdjustmentHistoryService,
				walletService
		);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(
						new AdminUserArgumentResolver(),
						new PageableHandlerMethodArgumentResolver()
				)
				.build();
	}

	@Test
	void getAdjustmentsReturnsPagedAdminLedger() throws Exception {
		AdminPointAdjustmentHistoryResponse history = new AdminPointAdjustmentHistoryResponse(
				91L, 7L, "green@example.com", "초록", CurrencyType.FREE,
				-200L, 100L, 1L, LocalDateTime.of(2026, 8, 3, 10, 0)
		);
		given(adminPointAdjustmentHistoryService.getAdjustments(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq(CurrencyType.FREE),
				org.mockito.ArgumentMatchers.eq(AdminPointAdjustmentDirection.DEDUCT),
				org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.any()
		)).willReturn(new PageImpl<>(List.of(history), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/admin/point/adjustments")
						.param("userId", "7")
						.param("currencyType", "FREE")
						.param("direction", "DEDUCT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].transactionId").value(91))
				.andExpect(jsonPath("$.data.content[0].targetNickname").value("초록"))
				.andExpect(jsonPath("$.data.content[0].adminUserId").value(1));
	}

	@Test
	void getWalletReturnsSelectedUsersBalances() throws Exception {
		given(walletService.getWallet(7L)).willReturn(new WalletResponse(
				7L, 900L, 500L, 400L, LocalDateTime.of(2026, 8, 3, 10, 0)
		));

		mockMvc.perform(get("/api/v1/admin/point/user/7/wallet"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(7))
				.andExpect(jsonPath("$.data.paidPoint").value(500))
				.andExpect(jsonPath("$.data.freePoint").value(400))
				.andExpect(jsonPath("$.data.balance").value(900));

		verify(walletService).getWallet(7L);
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
