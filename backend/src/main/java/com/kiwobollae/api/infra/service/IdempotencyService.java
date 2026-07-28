package com.kiwobollae.api.infra.service;

import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.entity.enums.IdempotencyStatus;
import com.kiwobollae.api.infra.repository.IdempotencyKeyRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

	private final IdempotencyKeyRepository idempotencyKeyRepository;
	private final UserRepository userRepository;

	public IdempotencyExecution start(
			Long userId,
			String apiType,
			String clientKey,
			String requestHash
	) {
		return idempotencyKeyRepository.findForUpdate(userId, apiType, clientKey)
				.map(existing -> validateExisting(existing, requestHash))
				.orElseGet(() -> create(userId, apiType, clientKey, requestHash));
	}

	public void succeed(
			IdempotencyKey key,
			int httpStatus,
			String responseSnapshot,
			String resourceType,
			Long resourceId
	) {
		key.succeed(
				httpStatus,
				responseSnapshot,
				resourceType,
				resourceId,
				LocalDateTime.now(ZoneOffset.UTC)
		);
		idempotencyKeyRepository.save(key);
	}

	private IdempotencyExecution validateExisting(IdempotencyKey existing, String requestHash) {
		if (!existing.getRequestHash().equals(requestHash)) {
			throw new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_CONFLICT);
		}
		if (existing.getStatus() == IdempotencyStatus.SUCCEEDED) {
			return new IdempotencyExecution(existing, true);
		}
		throw new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_IN_PROGRESS);
	}

	private IdempotencyExecution create(
			Long userId,
			String apiType,
			String clientKey,
			String requestHash
	) {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		IdempotencyKey key = IdempotencyKey.builder()
				.user(userRepository.getReferenceById(userId))
				.apiType(apiType)
				.clientKey(clientKey)
				.requestHash(requestHash)
				.status(IdempotencyStatus.IN_PROGRESS)
				.expiresAt(now.plusHours(24))
				.createdAt(now)
				.build();
		return new IdempotencyExecution(idempotencyKeyRepository.saveAndFlush(key), false);
	}
}
