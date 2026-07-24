package com.kiwobollae.api.auth.entity;

import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.global.common.BaseTimeEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
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

@Getter
@Entity
@Table(name = "users", indexes = {
		@Index(name = "idx_users_provider_provider_id", columnList = "provider, provider_id", unique = true),
		@Index(name = "idx_users_status", columnList = "status"),
		@Index(name = "idx_users_role_created_at", columnList = "role, create_at")
})
@AttributeOverrides({
		@AttributeOverride(name = "createdAt", column = @Column(name = "create_at", nullable = false, updatable = false)),
		@AttributeOverride(name = "updatedAt", column = @Column(name = "update_at", nullable = false))
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseTimeEntity {

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(length = 255)
	private String password;

	@Column(nullable = false, unique = true, length = 50)
	private String nickname;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(name = "phone_number", length = 20)
	private String phoneNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuthProvider provider;

	@Column(name = "provider_id", length = 100)
	private String providerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@Column(nullable = false)
	private Integer level;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private UserStatus status;

	@Column(name = "suspended_reason", length = 200)
	private String suspendedReason;

	@Column(name = "withdrawn_at")
	private LocalDateTime withdrawnAt;
}
