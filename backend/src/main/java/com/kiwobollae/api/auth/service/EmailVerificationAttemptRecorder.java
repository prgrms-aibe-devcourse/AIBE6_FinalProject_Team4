package com.kiwobollae.api.auth.service;

import com.kiwobollae.api.auth.repository.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a failed verification attempt in its own transaction. Must live on a
 * separate Spring bean: a self-invoked {@code @Transactional} method on the
 * same class would bypass the proxy, and without REQUIRES_NEW the increment
 * would roll back along with the BusinessException thrown right after it in
 * EmailVerificationService.confirm().
 */
@Component
@RequiredArgsConstructor
public class EmailVerificationAttemptRecorder {

	private final EmailVerificationRepository emailVerificationRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailedAttempt(Long verificationId) {
		emailVerificationRepository.findById(verificationId)
				.ifPresent(verification -> verification.recordFailedAttempt());
	}
}
