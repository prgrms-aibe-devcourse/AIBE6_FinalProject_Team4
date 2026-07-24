package com.kiwobollae.api.infra.dto.response;

import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.entity.enums.IdempotencyStatus;
import java.time.LocalDateTime;

public record IdempotencyKeyResponse(
		Long id,
		Long userId,
		String apiType,
		String clientKey,
		String requestHash,
		IdempotencyStatus status,
		Integer httpStatus,
		String responseSnapshot,
		LocalDateTime expiresAt,
		LocalDateTime createdAt
) {
	public static IdempotencyKeyResponse from(IdempotencyKey idempotencyKey) {
		return new IdempotencyKeyResponse(
				idempotencyKey.getId(),
				idempotencyKey.getUser().getId(),
				idempotencyKey.getApiType(),
				idempotencyKey.getClientKey(),
				idempotencyKey.getRequestHash(),
				idempotencyKey.getStatus(),
				idempotencyKey.getHttpStatus(),
				idempotencyKey.getResponseSnapshot(),
				idempotencyKey.getExpiresAt(),
				idempotencyKey.getCreatedAt()
		);
	}
}
