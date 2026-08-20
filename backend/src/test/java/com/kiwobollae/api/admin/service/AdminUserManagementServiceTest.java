package com.kiwobollae.api.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.cache.UserStatusCache;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementServiceTest {

	@Mock private UserRepository userRepository;
	@Mock private UserStatusCache userStatusCache;

	@InjectMocks
	private AdminUserManagementService adminUserManagementService;

	private User mockUser(UserRole role, UserStatus status) {
		return User.builder()
				.email("green@example.com")
				.nickname("초록")
				.name("김초록")
				.provider(AuthProvider.LOCAL)
				.role(role)
				.level(1)
				.status(status)
				.build();
	}

	@Test
	void suspendUserSetsStatusAndReason() {
		User user = mockUser(UserRole.USER, UserStatus.ACTIVE);
		given(userRepository.findById(1L)).willReturn(Optional.of(user));

		adminUserManagementService.suspendUser(1L, "부적절한 게시글 반복 작성");

		assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
		assertThat(user.getSuspendedReason()).isEqualTo("부적절한 게시글 반복 작성");
		verify(userStatusCache).evict(1L);
	}

	@Test
	void suspendUserFailsWhenTargetIsAdmin() {
		User admin = mockUser(UserRole.ADMIN, UserStatus.ACTIVE);
		given(userRepository.findById(1L)).willReturn(Optional.of(admin));

		assertThatThrownBy(() -> adminUserManagementService.suspendUser(1L, "사유"))
				.isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
		assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void suspendUserFailsWhenTargetIsWithdrawn() {
		User withdrawn = mockUser(UserRole.USER, UserStatus.WITHDRAWN);
		given(userRepository.findById(1L)).willReturn(Optional.of(withdrawn));

		assertThatThrownBy(() -> adminUserManagementService.suspendUser(1L, "사유"))
				.isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
	}

	@Test
	void suspendUserFailsWhenUserNotFound() {
		given(userRepository.findById(404L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> adminUserManagementService.suspendUser(404L, "사유"))
				.isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.AUTH_USER_NOT_FOUND);
	}

	@Test
	void reactivateUserRestoresActiveStatusAndClearsReason() {
		User user = mockUser(UserRole.USER, UserStatus.SUSPENDED);
		given(userRepository.findById(1L)).willReturn(Optional.of(user));

		adminUserManagementService.reactivateUser(1L);

		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getSuspendedReason()).isNull();
		verify(userStatusCache).evict(1L);
	}

	@Test
	void reactivateUserFailsWhenNotSuspended() {
		User user = mockUser(UserRole.USER, UserStatus.ACTIVE);
		given(userRepository.findById(1L)).willReturn(Optional.of(user));

		assertThatThrownBy(() -> adminUserManagementService.reactivateUser(1L))
				.isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
	}
}
