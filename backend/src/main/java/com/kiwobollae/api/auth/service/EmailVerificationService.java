package com.kiwobollae.api.auth.service;

import com.kiwobollae.api.auth.entity.EmailVerification;
import com.kiwobollae.api.auth.entity.enums.EmailVerificationPurpose;
import com.kiwobollae.api.auth.repository.EmailVerificationRepository;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.security.TokenHasher;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {

	private static final int CODE_LENGTH = 6;
	private static final int CODE_EXPIRATION_MINUTES = 5;
	private static final int MAX_ATTEMPTS = 5;
	// How long a verified-but-not-yet-signed-up email stays good for finishing signup.
	private static final int VERIFIED_VALIDITY_MINUTES = 30;
	private static final int RESET_TOKEN_BYTES = 32;

	private final EmailVerificationRepository emailVerificationRepository;
	private final UserRepository userRepository;
	private final EmailSender emailSender;
	private final TokenHasher tokenHasher;
	private final EmailVerificationAttemptRecorder attemptRecorder;
	private final SecureRandom random = new SecureRandom();

	@Transactional
	public void requestCode(String email) {
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}
		issueCode(email, EmailVerificationPurpose.SIGNUP);
	}

	@Transactional
	public void requestPasswordResetCode(String email) {
		if (!userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_FOUND);
		}
		issueCode(email, EmailVerificationPurpose.PASSWORD_RESET);
	}

	@Transactional
	public void confirmCode(String email, String code) {
		confirm(email, code, EmailVerificationPurpose.SIGNUP);
	}

	/**
	 * Confirms the code and issues a single-use reset token bound to this
	 * verification row. The raw token is returned to the caller (and from there,
	 * to the client) exactly once — only its hash is persisted — so that
	 * resetPassword() can later prove the caller is the same actor who completed
	 * this confirmation, not merely someone who knows the email address.
	 */
	@Transactional
	public String confirmPasswordResetCode(String email, String code) {
		EmailVerification verification = confirm(email, code, EmailVerificationPurpose.PASSWORD_RESET);
		String rawToken = generateResetToken();
		verification.issueResetTokenHash(tokenHasher.hash(rawToken));
		return rawToken;
	}

	/**
	 * Called from signup — throws if this email hasn't been verified recently enough.
	 */
	public void assertVerified(String email) {
		EmailVerification verification = emailVerificationRepository
				.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, EmailVerificationPurpose.SIGNUP)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED));

		boolean verifiedRecently = verification.isVerified()
				&& verification.getVerifiedAt().isAfter(LocalDateTime.now().minusMinutes(VERIFIED_VALIDITY_MINUTES));
		if (!verifiedRecently) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
		}
	}

	/**
	 * Called from password reset — validates the single-use token issued by
	 * confirmPasswordResetCode() and consumes it, so it can never be replayed.
	 */
	@Transactional
	public void consumePasswordResetToken(String email, String rawResetToken) {
		EmailVerification verification = emailVerificationRepository
				.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, EmailVerificationPurpose.PASSWORD_RESET)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID));

		boolean verifiedRecently = verification.isVerified()
				&& verification.getVerifiedAt().isAfter(LocalDateTime.now().minusMinutes(VERIFIED_VALIDITY_MINUTES));
		if (!verifiedRecently || !verification.matchesResetTokenHash(tokenHasher.hash(rawResetToken))) {
			throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID);
		}

		verification.consumeResetToken();
	}

	private void issueCode(String email, EmailVerificationPurpose purpose) {
		String code = generateCode();
		EmailVerification verification = EmailVerification.builder()
				.email(email)
				.purpose(purpose)
				.codeHash(tokenHasher.hash(code))
				.expiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES))
				.createdAt(LocalDateTime.now())
				.build();
		emailVerificationRepository.save(verification);

		if (purpose == EmailVerificationPurpose.SIGNUP) {
			emailSender.send(
					email,
					"[키워볼래] 이메일 인증코드",
					VerificationEmailTemplate.renderSignup(code, CODE_EXPIRATION_MINUTES));
		} else {
			emailSender.send(
					email,
					"[키워볼래] 비밀번호 재설정 인증코드",
					VerificationEmailTemplate.renderPasswordReset(code, CODE_EXPIRATION_MINUTES));
		}
	}

	private EmailVerification confirm(String email, String code, EmailVerificationPurpose purpose) {
		EmailVerification verification = emailVerificationRepository
				.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_VERIFICATION_CODE_INVALID));

		if (verification.isVerified()) {
			return verification;
		}
		if (verification.isExpired()) {
			throw new BusinessException(ErrorCode.AUTH_VERIFICATION_CODE_EXPIRED);
		}
		if (verification.getAttempts() >= MAX_ATTEMPTS) {
			throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOO_MANY_ATTEMPTS);
		}
		if (!verification.getCodeHash().equals(tokenHasher.hash(code))) {
			// Recorded via a separate REQUIRES_NEW transaction: this method's own
			// @Transactional would otherwise roll the increment back together with
			// the BusinessException thrown right below, silently defeating the
			// attempt cap (self-invoking an @Transactional method on this same
			// class wouldn't work either — that bypasses the Spring AOP proxy).
			attemptRecorder.recordFailedAttempt(verification.getId());
			throw new BusinessException(ErrorCode.AUTH_VERIFICATION_CODE_INVALID);
		}

		verification.markVerified();
		return verification;
	}

	private String generateCode() {
		int bound = (int) Math.pow(10, CODE_LENGTH);
		int value = random.nextInt(bound);
		return String.format("%0" + CODE_LENGTH + "d", value);
	}

	private String generateResetToken() {
		byte[] bytes = new byte[RESET_TOKEN_BYTES];
		random.nextBytes(bytes);
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}
}
