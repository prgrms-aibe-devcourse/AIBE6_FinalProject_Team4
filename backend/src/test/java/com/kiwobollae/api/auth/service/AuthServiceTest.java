package com.kiwobollae.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.dto.response.TokenIssueResult;
import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.oauth.OAuthClient;
import com.kiwobollae.api.auth.oauth.OAuthUserInfo;
import com.kiwobollae.api.auth.repository.RefreshTokenRepository;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.security.JwtTokenProvider;
import com.kiwobollae.api.global.security.TokenHasher;
import com.kiwobollae.api.notification.service.NotificationService;
import com.kiwobollae.api.point.service.WalletService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock private UserRepository userRepository;
	@Mock private RefreshTokenRepository refreshTokenRepository;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private JwtTokenProvider jwtTokenProvider;
	@Mock private TokenHasher tokenHasher;
	@Mock private EmailVerificationService emailVerificationService;
	@Mock private OAuthClient oAuthClient;
	@Mock private WalletService walletService;
	@Mock private NotificationService notificationService;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(
				userRepository,
				refreshTokenRepository,
				passwordEncoder,
				jwtTokenProvider,
				tokenHasher,
				emailVerificationService,
				List.of(oAuthClient),
				walletService,
				notificationService
		);
	}

	@Test
	void firstSocialLoginCreatesWalletForSavedUser() {
		OAuthUserInfo userInfo = new OAuthUserInfo(
				"google-provider-id",
				"social@example.test",
				"소셜회원"
		);
		given(oAuthClient.getProvider()).willReturn(AuthProvider.GOOGLE);
		given(oAuthClient.fetchUserInfo("authorization-code", null)).willReturn(userInfo);
		given(userRepository.findByProviderAndProviderId(
				AuthProvider.GOOGLE,
				"google-provider-id"
		)).willReturn(Optional.empty());
		given(userRepository.existsByEmail("social@example.test")).willReturn(false);
		given(userRepository.existsByNickname("소셜회원")).willReturn(false);
		given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
		stubTokenIssuance();

		TokenIssueResult result = authService.oauthLogin(
				AuthProvider.GOOGLE,
				"authorization-code",
				null
		);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		User savedUser = userCaptor.getValue();
		verify(walletService).createWallet(savedUser);
		assertThat(savedUser.getProvider()).isEqualTo(AuthProvider.GOOGLE);
		assertThat(savedUser.getProviderId()).isEqualTo("google-provider-id");
		assertThat(result.user().email()).isEqualTo("social@example.test");
	}

	@Test
	void returningSocialLoginDoesNotCreateAnotherWallet() {
		User existingUser = User.builder()
				.email("existing-social@example.test")
				.password(null)
				.nickname("기존회원")
				.name("기존회원")
				.provider(AuthProvider.KAKAO)
				.providerId("kakao-provider-id")
				.role(UserRole.USER)
				.level(1)
				.status(UserStatus.ACTIVE)
				.build();
		OAuthUserInfo userInfo = new OAuthUserInfo(
				"kakao-provider-id",
				"existing-social@example.test",
				"기존회원"
		);
		given(oAuthClient.getProvider()).willReturn(AuthProvider.KAKAO);
		given(oAuthClient.fetchUserInfo("authorization-code", "state")).willReturn(userInfo);
		given(userRepository.findByProviderAndProviderId(
				AuthProvider.KAKAO,
				"kakao-provider-id"
		)).willReturn(Optional.of(existingUser));
		stubTokenIssuance();

		authService.oauthLogin(AuthProvider.KAKAO, "authorization-code", "state");

		verify(userRepository, never()).save(any(User.class));
		verify(walletService, never()).createWallet(any(User.class));
	}

	private void stubTokenIssuance() {
		given(jwtTokenProvider.generateAccessToken(
				nullable(Long.class),
				eq(UserRole.USER.name())
		)).willReturn("access-token");
		given(jwtTokenProvider.generateRefreshToken(nullable(Long.class)))
				.willReturn("refresh-token");
		given(jwtTokenProvider.getRefreshExpirationMs()).willReturn(60_000L);
		given(tokenHasher.hash("refresh-token")).willReturn("refresh-token-hash");
	}
}
