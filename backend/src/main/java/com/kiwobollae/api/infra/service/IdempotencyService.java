package com.kiwobollae.api.infra.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.entity.enums.IdempotencyStatus;
import com.kiwobollae.api.infra.repository.IdempotencyKeyRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

	private static final long DEFAULT_RETENTION_HOURS = 24L;
	private static final long PAYMENT_RETENTION_HOURS = 24L * 7L;

	private final IdempotencyKeyRepository idempotencyKeyRepository;
	private final Clock seoulClock;

	public Optional<IdempotencyExecution> replayIfPresent(
			Long userId,
			String apiType,
			String clientKey,
			String requestHash
	) {
		return idempotencyKeyRepository
				.findByUser_IdAndApiTypeAndClientKey(userId, apiType, clientKey)
				.map(existing -> validateExisting(existing, requestHash));
	}

	/**
	 * 요청 해시 형식 변경의 전환 기간에만 사용한다. 호출자는 재생 응답으로 원래 요청의 의미를
	 * 직접 대조해야 하며, 새 요청을 실행하는 용도로 사용하면 안 된다.
	 */
	public IdempotencyExecution replaySucceededIgnoringHash(
			Long userId,
			String apiType,
			String clientKey
	) {
		IdempotencyKey existing = idempotencyKeyRepository
				.findByUser_IdAndApiTypeAndClientKey(userId, apiType, clientKey)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_CONFLICT));
		if (existing.getStatus() == IdempotencyStatus.SUCCEEDED) {
			return new IdempotencyExecution(existing, true);
		}
		throw new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_IN_PROGRESS);
	}

	public IdempotencyExecution start(
			Long userId,
			String apiType,
			String clientKey,
			String requestHash
	) {
		return start(userId, apiType, clientKey, requestHash, null);
	}

	public IdempotencyExecution startWithCompatibleHash(
			Long userId,
			String apiType,
			String clientKey,
			String requestHash,
			String compatibleRequestHash
	) {
		// Migration-only compatibility: a newly-created row always stores requestHash.
		// compatibleRequestHash is accepted only when the same client key already exists.
		return start(userId, apiType, clientKey, requestHash, compatibleRequestHash);
	}

	private IdempotencyExecution start(
			Long userId,
			String apiType,
			String clientKey,
			String requestHash,
			String compatibleRequestHash
	) {
		LocalDateTime now = LocalDateTime.now(seoulClock);
		String claimToken = UUID.randomUUID().toString();
		idempotencyKeyRepository.claim(
				userId,
				apiType,
				clientKey,
				requestHash,
				claimToken,
				now.plusHours(retentionHours(apiType)),
				now
		);
		IdempotencyKey key = idempotencyKeyRepository.findForUpdate(userId, apiType, clientKey)
				.orElseThrow(() -> new IllegalStateException("선점한 멱등키를 조회할 수 없습니다."));
		return claimToken.equals(key.getClaimToken())
				? new IdempotencyExecution(key, false)
				: validateExisting(key, requestHash, compatibleRequestHash);
	}

	public void succeed(
			IdempotencyKey key,
			int httpStatus,
			String responseSnapshot,
			String resourceType,
			Long resourceId
	) {
		LocalDateTime now = LocalDateTime.now(seoulClock);
		key.succeed(
				httpStatus,
				responseSnapshot,
				resourceType,
				resourceId,
				now,
				now.plusHours(retentionHours(key.getApiType()))
		);
		idempotencyKeyRepository.save(key);
	}

	private IdempotencyExecution validateExisting(IdempotencyKey existing, String requestHash) {
		return validateExisting(existing, requestHash, null);
	}

	private IdempotencyExecution validateExisting(
			IdempotencyKey existing,
			String requestHash,
			String compatibleRequestHash
	) {
		if (!existing.getRequestHash().equals(requestHash)
				&& !existing.getRequestHash().equals(compatibleRequestHash)) {
			throw new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_CONFLICT);
		}
		if (existing.getStatus() == IdempotencyStatus.SUCCEEDED) {
			return new IdempotencyExecution(existing, true);
		}
		throw new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_IN_PROGRESS);
	}

	private long retentionHours(String apiType) {
		return apiType != null && apiType.startsWith("PAYMENT_")
				? PAYMENT_RETENTION_HOURS
				: DEFAULT_RETENTION_HOURS;
	}
}
