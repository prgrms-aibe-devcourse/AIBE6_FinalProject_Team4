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
		validateRequest(adminUserId, idempotencyKey, request);
		IdempotencyExecution idempotency = idempotencyService.start(
				adminUserId,
				API_TYPE,
				idempotencyKey,
				sha256(normalizedRequest(request))
		);
		if (idempotency.replay()) {
			return readSnapshot(idempotency.key().getResponseSnapshot());
		}

		AdminPointAdjustmentResponse response = walletService.adjustByAdmin(
				adminUserId,
				request.userId(),
				request.currencyType(),
				request.amount()
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

	private void validateRequest(
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
	}

	private String normalizedRequest(AdminPointAdjustmentRequest request) {
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
