package com.kiwobollae.api.auth.dto.response;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import java.time.LocalDateTime;

public record UserResponse(
		Long id,
		String email,
		String nickname,
		String name,
		String phoneNumber,
		AuthProvider provider,
		UserRole role,
		Integer level,
		UserStatus status,
		String suspendedReason,
		LocalDateTime withdrawnAt,
		LocalDateTime createdAt
) {
	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getEmail(),
				user.getNickname(),
				user.getName(),
				user.getPhoneNumber(),
				user.getProvider(),
				user.getRole(),
				user.getLevel(),
				user.getStatus(),
				user.getSuspendedReason(),
				user.getWithdrawnAt(),
				user.getCreatedAt()
		);
	}
}
