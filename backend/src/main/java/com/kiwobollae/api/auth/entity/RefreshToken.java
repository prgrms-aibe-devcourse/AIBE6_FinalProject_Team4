package com.kiwobollae.api.auth.entity;

import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "refresh_token", indexes = {
		@Index(name = "idx_refresh_token_user_id", columnList = "user_id"),
		@Index(name = "idx_refresh_token_expires_at", columnList = "expires_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshToken extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;
}
