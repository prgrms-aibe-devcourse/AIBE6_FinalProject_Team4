package com.kiwobollae.api.infra.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.entity.enums.IdempotencyStatus;
import com.kiwobollae.api.infra.repository.IdempotencyKeyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

	private final IdempotencyKeyRepository idempotencyKeyRepository;

	@Transactional
	public StartResult start(User user, String apiType, String clientKey, String normalizedRequest) {
		validateClientKey(clientKey);
		String requestHash = sha256(normalizedRequest);

		return idempotencyKeyRepository
				.findByUser_IdAndApiTypeAndClientKey(user.getId(), apiType, clientKey)
				.map(existing -> handleExisting(existing, requestHash))
				.orElseGet(() -> create(user, apiType, clientKey, requestHash));
	}

	@Transactional
	public void complete(IdempotencyKey key, int httpStatus, String responseSnapshot,
			String resourceType, Long resourceId) {
		key.complete(
				httpStatus,
				responseSnapshot,
				resourceType,
				resourceId,
				LocalDateTime.now().plusDays(7)
		);
		idempotencyKeyRepository.save(key);
	}

	private StartResult handleExisting(IdempotencyKey existing, String requestHash) {
		if (!existing.getRequestHash().equals(requestHash)) {
			throw new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_CONFLICT);
		}
		if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
			throw new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_IN_PROGRESS);
		}
		if (existing.getStatus() == IdempotencyStatus.FAILED) {
			throw new BusinessException(
					ErrorCode.COMMON_IDEMPOTENCY_CONFLICT,
					"실패한 요청입니다. 새로운 멱등성 키로 다시 요청해 주세요."
			);
		}
		return new StartResult(existing, true);
	}

	private StartResult create(User user, String apiType, String clientKey, String requestHash) {
		IdempotencyKey key = IdempotencyKey.builder()
				.user(user)
				.apiType(apiType)
				.clientKey(clientKey)
				.requestHash(requestHash)
				.status(IdempotencyStatus.PROCESSING)
				.build();
		return new StartResult(idempotencyKeyRepository.save(key), false);
	}

	private void validateClientKey(String clientKey) {
		if (clientKey == null || clientKey.isBlank() || clientKey.length() > 100) {
			throw new BusinessException(
					ErrorCode.COMMON_VALIDATION_FAILED,
					"Idempotency-Key는 1자 이상 100자 이하로 입력해 주세요."
			);
		}
	}

	private String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
		}
	}

	public record StartResult(IdempotencyKey key, boolean replay) {

		public String responseSnapshot() {
			return key.getResponseSnapshot();
		}
	}
}
