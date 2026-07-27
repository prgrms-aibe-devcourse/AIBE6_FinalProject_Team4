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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "idempotency_keys", indexes = {
		@Index(name = "idx_idempotency_key_user_api_client", columnList = "user_id, api_type, client_key", unique = true),
		@Index(name = "idx_idempotency_key_response_expires_at", columnList = "response_expires_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class IdempotencyKey extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "api_type", nullable = false, length = 40)
	private String apiType;

	@Column(name = "client_key", nullable = false, length = 100)
	private String clientKey;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private IdempotencyStatus status;

	@Column(name = "http_status")
	private Integer httpStatus;

	@Column(name = "response_snapshot", columnDefinition = "text")
	private String responseSnapshot;

	@Column(name = "resource_type", length = 30)
	private String resourceType;

	@Column(name = "resource_id")
	private Long resourceId;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "response_expires_at")
	private LocalDateTime responseExpiresAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public void complete(int httpStatus, String responseSnapshot, String resourceType,
			Long resourceId, LocalDateTime responseExpiresAt) {
		this.status = IdempotencyStatus.SUCCEEDED;
		this.httpStatus = httpStatus;
		this.responseSnapshot = responseSnapshot;
		this.resourceType = resourceType;
		this.resourceId = resourceId;
		this.completedAt = LocalDateTime.now();
		this.responseExpiresAt = responseExpiresAt;
	}

	public void fail() {
		this.status = IdempotencyStatus.FAILED;
		this.completedAt = LocalDateTime.now();
	}
}
