package com.kiwobollae.api.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentRequest;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentResponse;
import com.kiwobollae.api.point.entity.enums.AdminPointAdjustmentReason;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(
				7L, CurrencyType.FREE, 300L, AdminPointAdjustmentReason.SPECIAL_EVENT);
		IdempotencyKey key = IdempotencyKey.builder().build();
		AdminPointAdjustmentResponse response = response(91L, 7L, CurrencyType.FREE, 300L, 800L);
		given(idempotencyService.start(eq(1L), eq("POINT_ADMIN_ADJUST"), eq("adjust-key"), anyString()))
				.willReturn(new IdempotencyExecution(key, false));
		given(walletService.adjustByAdmin(
				1L, 7L, CurrencyType.FREE, 300L, AdminPointAdjustmentReason.SPECIAL_EVENT))
				.willReturn(response);

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
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(
				7L, CurrencyType.PAID, -200L, AdminPointAdjustmentReason.FRAUD_PENALTY);
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
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(
				7L, CurrencyType.FREE, 0L, AdminPointAdjustmentReason.SPECIAL_EVENT);

		assertThatThrownBy(() -> adminPointAdjustmentService.adjust(1L, "adjust-key", request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));

		verifyNoInteractions(idempotencyService, walletService);
	}

	@Test
	void selfAdjustmentIsRejectedBeforeIdempotencyStarts() {
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(
				1L, CurrencyType.FREE, 100L, AdminPointAdjustmentReason.SPECIAL_EVENT);

		assertThatThrownBy(() -> adminPointAdjustmentService.adjust(1L, "adjust-key", request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.POINT_SELF_ADJUSTMENT_FORBIDDEN));

		verifyNoInteractions(idempotencyService, walletService);
	}

	@Test
	void missingReasonWithoutLegacyKeyIsRejectedWithoutStartingNewExecution() {
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(
				7L, CurrencyType.FREE, 100L, null);
		given(idempotencyService.replayIfPresent(
				eq(1L), eq("POINT_ADMIN_ADJUST"), eq("missing"), anyString()))
				.willReturn(Optional.empty());

		assertThatThrownBy(() -> adminPointAdjustmentService.adjust(1L, "missing", request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));

		verify(idempotencyService).replayIfPresent(
				eq(1L), eq("POINT_ADMIN_ADJUST"), eq("missing"), anyString());
		verify(idempotencyService, never()).start(
				org.mockito.ArgumentMatchers.anyLong(),
				anyString(),
				anyString(),
				anyString()
		);
		verifyNoInteractions(walletService);
	}

	@Test
	void directionMismatchedReasonIsRejectedBeforeIdempotencyStarts() {
		AdminPointAdjustmentRequest mismatchedReason = new AdminPointAdjustmentRequest(
				7L, CurrencyType.FREE, -100L, AdminPointAdjustmentReason.OUTSTANDING_MEMBER);

		assertThatThrownBy(() -> adminPointAdjustmentService.adjust(1L, "mismatch", mismatchedReason))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));

		verifyNoInteractions(idempotencyService, walletService);
	}

	@Test
	void missingReasonReplaysLegacySucceededResponse() {
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(
				7L, CurrencyType.FREE, 300L, null);
		IdempotencyKey legacyKey = IdempotencyKey.builder()
				.responseSnapshot("""
						{"transactionId":91,"userId":7,"currencyType":"FREE","amount":300,
						 "balanceAfter":800,"paidPoint":500,"freePoint":800,"balance":1300}
						""")
				.build();
		given(idempotencyService.replayIfPresent(
				eq(1L),
				eq("POINT_ADMIN_ADJUST"),
				eq("legacy-key"),
				eq("4cb0959c762bfdcbac59be8e3d5ad5722244fba531c6f720ccd13fcc88ce391e")
		))
				.willReturn(Optional.of(new IdempotencyExecution(legacyKey, true)));

		AdminPointAdjustmentResponse result =
				adminPointAdjustmentService.adjust(1L, "legacy-key", request);

		assertThat(result.transactionId()).isEqualTo(91L);
		assertThat(result.adjustmentReason()).isNull();
		verify(idempotencyService, never()).start(
				org.mockito.ArgumentMatchers.anyLong(),
				anyString(),
				anyString(),
				anyString()
		);
		verifyNoInteractions(walletService);
	}

	@Test
	void missingReasonKeepsLegacyInProgressResponseAsConflict() {
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(
				7L, CurrencyType.FREE, 300L, null);
		given(idempotencyService.replayIfPresent(
				eq(1L), eq("POINT_ADMIN_ADJUST"), eq("legacy-key"), anyString()))
				.willThrow(new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_IN_PROGRESS));

		assertThatThrownBy(() -> adminPointAdjustmentService.adjust(1L, "legacy-key", request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.COMMON_IDEMPOTENCY_IN_PROGRESS));

		verify(idempotencyService, never()).start(
				org.mockito.ArgumentMatchers.anyLong(),
				anyString(),
				anyString(),
				anyString()
		);
		verifyNoInteractions(walletService);
	}

	@Test
	void adjustmentReasonParticipatesInIdempotencyRequestHash() {
		IdempotencyKey key = IdempotencyKey.builder().build();
		given(idempotencyService.start(eq(1L), eq("POINT_ADMIN_ADJUST"), eq("adjust-key"), anyString()))
				.willReturn(new IdempotencyExecution(key, false));
		given(walletService.adjustByAdmin(
				1L, 7L, CurrencyType.FREE, 300L, AdminPointAdjustmentReason.SPECIAL_EVENT))
				.willReturn(response(91L, 7L, CurrencyType.FREE, 300L, 800L));
		given(walletService.adjustByAdmin(
				1L, 7L, CurrencyType.FREE, 300L, AdminPointAdjustmentReason.OUTSTANDING_MEMBER))
				.willReturn(response(92L, 7L, CurrencyType.FREE, 300L, 1_100L));

		adminPointAdjustmentService.adjust(1L, "adjust-key", new AdminPointAdjustmentRequest(
				7L, CurrencyType.FREE, 300L, AdminPointAdjustmentReason.SPECIAL_EVENT));
		adminPointAdjustmentService.adjust(1L, "adjust-key", new AdminPointAdjustmentRequest(
				7L, CurrencyType.FREE, 300L, AdminPointAdjustmentReason.OUTSTANDING_MEMBER));

		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		verify(idempotencyService, times(2)).start(
				eq(1L), eq("POINT_ADMIN_ADJUST"), eq("adjust-key"), hashCaptor.capture());
		assertThat(hashCaptor.getAllValues()).hasSize(2);
		assertThat(hashCaptor.getAllValues().get(0)).isNotEqualTo(hashCaptor.getAllValues().get(1));
	}

	@Test
	void legacyIdempotencyHashReplaysResponseWithoutAdjustmentReason() {
		AdminPointAdjustmentRequest request = new AdminPointAdjustmentRequest(
				7L, CurrencyType.FREE, 300L, AdminPointAdjustmentReason.SPECIAL_EVENT);
		IdempotencyKey legacyKey = IdempotencyKey.builder()
				.responseSnapshot("""
						{"transactionId":91,"userId":7,"currencyType":"FREE","amount":300,
						 "balanceAfter":800,"paidPoint":500,"freePoint":800,"balance":1300}
						""")
				.build();
		given(idempotencyService.start(
				eq(1L),
				eq("POINT_ADMIN_ADJUST"),
				eq("legacy-key"),
				eq("c1931ae8e7dd097a8e1dd32fe5ff1fc5d312b01a66aca15ec3118a0d4ab0525a")
		))
				.willThrow(new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_CONFLICT));
		given(idempotencyService.replayIfPresent(
				eq(1L),
				eq("POINT_ADMIN_ADJUST"),
				eq("legacy-key"),
				eq("4cb0959c762bfdcbac59be8e3d5ad5722244fba531c6f720ccd13fcc88ce391e")
		))
				.willReturn(Optional.of(new IdempotencyExecution(legacyKey, true)));

		AdminPointAdjustmentResponse result =
				adminPointAdjustmentService.adjust(1L, "legacy-key", request);

		assertThat(result.transactionId()).isEqualTo(91L);
		assertThat(result.adjustmentReason()).isNull();
		verifyNoInteractions(walletService);
		verify(idempotencyService).replayIfPresent(
				eq(1L),
				eq("POINT_ADMIN_ADJUST"),
				eq("legacy-key"),
				eq("4cb0959c762bfdcbac59be8e3d5ad5722244fba531c6f720ccd13fcc88ce391e")
		);
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
				amount > 0
						? AdminPointAdjustmentReason.SPECIAL_EVENT
						: AdminPointAdjustmentReason.FRAUD_PENALTY,
				balanceAfter,
				currencyType == CurrencyType.PAID ? balanceAfter : 500L,
				currencyType == CurrencyType.FREE ? balanceAfter : 300L,
				currencyType == CurrencyType.PAID ? balanceAfter + 300L : balanceAfter + 500L
		);
	}
}
