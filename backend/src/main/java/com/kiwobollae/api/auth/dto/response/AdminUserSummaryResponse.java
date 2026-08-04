package com.kiwobollae.api.auth.dto.response;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import java.time.LocalDateTime;

/** 관리자 회원 선택 목록에 필요한 최소 정보만 노출한다. */
public record AdminUserSummaryResponse(
		Long id,
		String email,
		String nickname,
		String name,
		UserRole role,
		UserStatus status,
		LocalDateTime createdAt
) {
	public static AdminUserSummaryResponse from(User user) {
		return new AdminUserSummaryResponse(
				user.getId(),
				user.getEmail(),
				user.getNickname(),
				user.getName(),
				user.getRole(),
				user.getStatus(),
				user.getCreatedAt()
		);
	}
}
