package com.kiwobollae.api.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Common auditing fields for entities that have simple created_at / updated_at columns.
 * Entities with richer timestamp semantics (e.g. orders.ordered_at/delivered_at) keep those
 * as plain fields instead of extending this class.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity extends BaseEntity {

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}
