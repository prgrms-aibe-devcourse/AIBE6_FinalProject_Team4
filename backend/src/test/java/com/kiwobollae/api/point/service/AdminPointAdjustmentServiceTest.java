package com.kiwobollae.api.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentRequest;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentResponse;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminPointAdjustmentServiceTest {

	@Mock private WalletService walletService;
	@Mock private IdempotencyService idempotencyService;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private AdminPointAdjustmentService adminPointAdjustmentService;

	@BeforeEach
	void setUp() {
		adminPointAdjustmentService = new AdminPointAdjustmentService(
				walletService,
				idempotencyService,
				objectMapper
		);
	}

	@Test
	void adjustmentCompletesIdempotencyWithTransactionResource() {
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(7L, CurrencyType.FREE, 300L);
		IdempotencyKey key = IdempotencyKey.builder().build();
		AdminPointAdjustmentResponse response = response(91L, 7L, CurrencyType.FREE, 300L, 800L);
		given(idempotencyService.start(eq(1L), eq("POINT_ADMIN_ADJUST"), eq("adjust-key"), anyString()))
				.willReturn(new IdempotencyExecution(key, false));
		given(walletService.adjustByAdmin(1L, 7L, CurrencyType.FREE, 300L)).willReturn(response);

		AdminPointAdjustmentResponse result = adminPointAdjustmentService.adjust(1L, "adjust-key", request);

		assertThat(result).isEqualTo(response);
		verify(idempotencyService).succeed(
				org.mockito.ArgumentMatchers.eq(key),
				org.mockito.ArgumentMatchers.eq(200),
				anyString(),
				org.mockito.ArgumentMatchers.eq("POINT_TRANSACTION"),
				org.mockito.ArgumentMatchers.eq(91L)
		);
	}

	@Test
	void sameIdempotencyKeyAndRequestReplaysFirstResponse() throws Exception {
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(7L, CurrencyType.PAID, -200L);
		AdminPointAdjustmentResponse response = response(92L, 7L, CurrencyType.PAID, -200L, 300L);
		IdempotencyKey key = IdempotencyKey.builder()
				.responseSnapshot(objectMapper.writeValueAsString(response))
				.build();
		given(idempotencyService.start(eq(1L), eq("POINT_ADMIN_ADJUST"), eq("adjust-key"), anyString()))
				.willReturn(new IdempotencyExecution(key, true));

		AdminPointAdjustmentResponse result = adminPointAdjustmentService.adjust(1L, "adjust-key", request);

		assertThat(result).isEqualTo(response);
		verifyNoInteractions(walletService);
		verify(idempotencyService, never()).succeed(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void zeroAdjustmentIsRejectedBeforeIdempotencyStarts() {
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(7L, CurrencyType.FREE, 0L);

		assertThatThrownBy(() -> adminPointAdjustmentService.adjust(1L, "adjust-key", request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));

		verifyNoInteractions(idempotencyService, walletService);
	}

	private AdminPointAdjustmentResponse response(
			Long transactionId,
			Long userId,
			CurrencyType currencyType,
			Long amount,
			Long balanceAfter
	) {
		return new AdminPointAdjustmentResponse(
				transactionId,
				userId,
				currencyType,
				amount,
				balanceAfter,
				currencyType == CurrencyType.PAID ? balanceAfter : 500L,
				currencyType == CurrencyType.FREE ? balanceAfter : 300L,
				currencyType == CurrencyType.PAID ? balanceAfter + 300L : balanceAfter + 500L
		);
	}
}
