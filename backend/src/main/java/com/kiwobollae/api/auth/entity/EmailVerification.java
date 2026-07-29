package com.kiwobollae.api.auth.entity;

import com.kiwobollae.api.auth.entity.enums.EmailVerificationPurpose;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A single "we sent this code to this email" attempt. Only the hash of the raw
 * 6-digit code is stored (mirrors RefreshToken's token_hash approach) so a DB
 * leak alone can't be used to complete someone else's signup.
 */
@Getter
@Entity
@Table(name = "email_verification", indexes = {
		@Index(name = "idx_email_verification_email_purpose_created_at", columnList = "email, purpose, created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EmailVerification extends BaseEntity {

	@Column(nullable = false, length = 255)
	private String email;

	// Separates a signup code from a password-reset code for the same email so the
	// "latest row for this email" lookup can't let one purpose's code confirm the other.
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EmailVerificationPurpose purpose;

	@Column(name = "code_hash", nullable = false, length = 64)
	private String codeHash;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "verified_at")
	private LocalDateTime verifiedAt;

	@Builder.Default
	@Column(nullable = false)
	private Integer attempts = 0;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	// Single-use ticket issued only for PASSWORD_RESET purpose once the code is
	// confirmed, so resetPassword() can prove the caller is the same actor who
	// completed the email verification step — not just "someone verified this
	// email within the validity window".
	@Column(name = "reset_token_hash", length = 64)
	private String resetTokenHash;

	public boolean isExpired() {
		return expiresAt.isBefore(LocalDateTime.now());
	}

	public boolean isVerified() {
		return verifiedAt != null;
	}

	public void markVerified() {
		this.verifiedAt = LocalDateTime.now();
	}

	public void recordFailedAttempt() {
		this.attempts += 1;
	}

	public void issueResetTokenHash(String hash) {
		this.resetTokenHash = hash;
	}

	public boolean matchesResetTokenHash(String hash) {
		return resetTokenHash != null && resetTokenHash.equals(hash);
	}

	public void consumeResetToken() {
		this.resetTokenHash = null;
	}
}
