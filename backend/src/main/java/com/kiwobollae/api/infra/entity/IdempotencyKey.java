package com.kiwobollae.api.infra.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseEntity;
import com.kiwobollae.api.infra.entity.enums.IdempotencyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "idempotency_keys", indexes = {
		@Index(name = "idx_idempotency_key_user_api_client", columnList = "user_id, api_type, client_key", unique = true),
		@Index(name = "idx_idempotency_key_expires_at", columnList = "expires_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class IdempotencyKey extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "api_type", nullable = false, length = 30)
	private String apiType;

	@Column(name = "client_key", nullable = false, length = 64)
	private String clientKey;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private IdempotencyStatus status;

	@Column(name = "http_status")
	private Integer httpStatus;

	@Column(name = "response_snapshot", length = 4000)
	private String responseSnapshot;

	@Column(name = "resource_type", length = 30)
	private String resourceType;

	@Column(name = "resource_id")
	private Long resourceId;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "response_expires_at")
	private LocalDateTime responseExpiresAt;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;
}
