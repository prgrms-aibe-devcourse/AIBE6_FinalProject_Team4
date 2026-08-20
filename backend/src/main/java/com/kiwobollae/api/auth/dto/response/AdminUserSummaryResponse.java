package com.kiwobollae.api.auth.dto.response;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import java.time.LocalDateTime;

/** 관리자 회원 목록에 필요한 정보 — 상태(정지/탈퇴 등)와 누적 피신고 건수를 함께 노출한다. */
public record AdminUserSummaryResponse(
		Long id,
		String email,
		String nickname,
		String name,
		UserRole role,
		UserStatus status,
		String suspendedReason,
		LocalDateTime withdrawnAt,
		LocalDateTime createdAt,
		long reportCount
) {
	public static AdminUserSummaryResponse from(User user, long reportCount) {
		return new AdminUserSummaryResponse(
				user.getId(),
				user.getEmail(),
				user.getNickname(),
				user.getName(),
				user.getRole(),
				user.getStatus(),
				user.getSuspendedReason(),
				user.getWithdrawnAt(),
				user.getCreatedAt(),
				reportCount
		);
	}
}
