package com.kiwobollae.api.point.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentRequest;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPointAdjustmentService {

	private static final String API_TYPE = "POINT_ADMIN_ADJUST";

	private final WalletService walletService;
	private final IdempotencyService idempotencyService;
	private final ObjectMapper objectMapper;

	@Transactional
	public AdminPointAdjustmentResponse adjust(
			Long adminUserId,
			String idempotencyKey,
			AdminPointAdjustmentRequest request
	) {
		return adjust(adminUserId, idempotencyKey, request, true);
	}

	/**
	 * 알림 발송 여부를 선택할 수 있는 버전. 로컬 시드(PointScenarioInitData)처럼 실제 관리자
	 * 액션이 아닌 호출에서 알림이 나가지 않도록 {@code notify=false}로 쓴다.
	 */
	@Transactional
	public AdminPointAdjustmentResponse adjust(
			Long adminUserId,
			String idempotencyKey,
			AdminPointAdjustmentRequest request,
			boolean notify
	) {
		validateBaseRequest(adminUserId, idempotencyKey, request);
		if (request.adjustmentReason() == null) {
			return replayLegacyRequest(adminUserId, idempotencyKey, request);
		}
		validateAdjustmentReason(request);
		IdempotencyExecution idempotency = startWithLegacyReplay(
				adminUserId, idempotencyKey, request);
		if (idempotency.replay()) {
			return readSnapshot(idempotency.key().getResponseSnapshot());
		}

		AdminPointAdjustmentResponse response = walletService.adjustByAdmin(
				adminUserId,
				request.userId(),
				request.currencyType(),
				request.amount(),
				request.adjustmentReason(),
				notify
		);
		idempotencyService.succeed(
				idempotency.key(),
				200,
				writeSnapshot(response),
				"POINT_TRANSACTION",
				response.transactionId()
		);
		return response;
	}

	private void validateBaseRequest(
			Long adminUserId,
			String idempotencyKey,
			AdminPointAdjustmentRequest request
	) {
		if (adminUserId == null || adminUserId < 1
				|| idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64
				|| request == null || request.userId() == null || request.userId() < 1
				|| request.currencyType() == null || request.amount() == null || request.amount() == 0) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		if (adminUserId.equals(request.userId())) {
			throw new BusinessException(ErrorCode.POINT_SELF_ADJUSTMENT_FORBIDDEN);
		}
	}

	private void validateAdjustmentReason(AdminPointAdjustmentRequest request) {
		if (!request.adjustmentReason().supports(request.amount())) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
	}

	private AdminPointAdjustmentResponse replayLegacyRequest(
			Long adminUserId,
			String idempotencyKey,
			AdminPointAdjustmentRequest request
	) {
		Optional<IdempotencyExecution> legacyReplay = idempotencyService.replayIfPresent(
				adminUserId,
				API_TYPE,
				idempotencyKey,
				sha256(legacyNormalizedRequest(request))
		);
		if (legacyReplay.isEmpty()) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		return readSnapshot(legacyReplay.get().key().getResponseSnapshot());
	}

	private String normalizedRequest(AdminPointAdjustmentRequest request) {
		return "userId=" + request.userId()
				+ "&currencyType=" + request.currencyType()
				+ "&amount=" + request.amount()
				+ "&adjustmentReason=" + request.adjustmentReason();
	}

	private IdempotencyExecution startWithLegacyReplay(
			Long adminUserId,
			String idempotencyKey,
			AdminPointAdjustmentRequest request
	) {
		try {
			return idempotencyService.start(
					adminUserId,
					API_TYPE,
					idempotencyKey,
					sha256(normalizedRequest(request))
			);
		} catch (BusinessException exception) {
			if (exception.getErrorCode() != ErrorCode.COMMON_IDEMPOTENCY_CONFLICT) {
				throw exception;
			}

			// adjustmentReason 도입 전에 생성된 키는 기존 정규화 해시로 재생만 허용한다.
			// 신규 실행은 항상 adjustmentReason을 포함한 해시로 시작한다.
			Optional<IdempotencyExecution> legacyReplay = idempotencyService.replayIfPresent(
					adminUserId,
					API_TYPE,
					idempotencyKey,
					sha256(legacyNormalizedRequest(request))
			);
			return legacyReplay.orElseThrow(() -> exception);
		}
	}

	private String legacyNormalizedRequest(AdminPointAdjustmentRequest request) {
		return "userId=" + request.userId()
				+ "&currencyType=" + request.currencyType()
				+ "&amount=" + request.amount();
	}

	private String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(value.getBytes(StandardCharsets.UTF_8))
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
	}

	private String writeSnapshot(AdminPointAdjustmentResponse response) {
		try {
			return objectMapper.writeValueAsString(response);
		} catch (JacksonException exception) {
			throw new IllegalStateException("관리자 포인트 조정 응답 저장에 실패했습니다.", exception);
		}
	}

	private AdminPointAdjustmentResponse readSnapshot(String snapshot) {
		try {
			return objectMapper.readValue(snapshot, AdminPointAdjustmentResponse.class);
		} catch (JacksonException exception) {
			throw new IllegalStateException("관리자 포인트 조정 응답 복원에 실패했습니다.", exception);
		}
	}
}
