package com.kiwobollae.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.kiwobollae.api.global.concurrency.UniqueInsertGuard;
import com.kiwobollae.api.global.security.JwtTokenProvider;
import com.kiwobollae.api.global.security.TokenHasher;
import com.kiwobollae.api.journal.repository.DailyJournalRewardRepository;
import com.kiwobollae.api.notification.repository.JournalReminderLogRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.kiwobollae.api.notification.entity.enums.NotificationType;

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
	@Mock private JournalReminderLogRepository journalReminderLogRepository;
	@Mock private UniqueInsertGuard uniqueInsertGuard;
	@Mock private DailyJournalRewardRepository dailyJournalRewardRepository;

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
				notificationService,
				journalReminderLogRepository,
				uniqueInsertGuard,
				dailyJournalRewardRepository
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

	@Test
	void loginSendsJournalReminderWhenTodayRewardNotClaimed() {
		User existingUser = existingKakaoUser();
		stubExistingKakaoLogin(existingUser);
		given(dailyJournalRewardRepository.existsForUserAndRewardDate(eq(5L), any())).willReturn(false);
		given(journalReminderLogRepository.existsByUser_IdAndReminderDate(eq(5L), any())).willReturn(false);
		given(uniqueInsertGuard.tryInsert(any())).willReturn(true);

		authService.oauthLogin(AuthProvider.KAKAO, "authorization-code", "state");

		verify(notificationService).notify(
				eq(5L), eq(NotificationType.JOURNAL_REMINDER), any(), any(), any(), eq("JOURNAL_REMINDER_DATE"), any());
	}

	@Test
	void loginSkipsJournalReminderWhenConcurrentRequestAlreadyClaimedIt() {
		User existingUser = existingKakaoUser();
		stubExistingKakaoLogin(existingUser);
		given(dailyJournalRewardRepository.existsForUserAndRewardDate(eq(5L), any())).willReturn(false);
		given(journalReminderLogRepository.existsByUser_IdAndReminderDate(eq(5L), any())).willReturn(false);
		given(uniqueInsertGuard.tryInsert(any())).willReturn(false);

		authService.oauthLogin(AuthProvider.KAKAO, "authorization-code", "state");

		verify(notificationService, never()).notify(
				any(), eq(NotificationType.JOURNAL_REMINDER), any(), any(), any(), eq("JOURNAL_REMINDER_DATE"), any());
	}

	@Test
	void loginSkipsJournalReminderWhenTodayRewardAlreadyClaimed() {
		User existingUser = existingKakaoUser();
		stubExistingKakaoLogin(existingUser);
		given(dailyJournalRewardRepository.existsForUserAndRewardDate(eq(5L), any())).willReturn(true);

		authService.oauthLogin(AuthProvider.KAKAO, "authorization-code", "state");

		verify(notificationService, never()).notify(
				any(), eq(NotificationType.JOURNAL_REMINDER), any(), any(), any(), eq("JOURNAL_REMINDER_DATE"), any());
	}

	@Test
	void loginSkipsJournalReminderWhenAlreadySentToday() {
		User existingUser = existingKakaoUser();
		stubExistingKakaoLogin(existingUser);
		given(dailyJournalRewardRepository.existsForUserAndRewardDate(eq(5L), any())).willReturn(false);
		given(journalReminderLogRepository.existsByUser_IdAndReminderDate(eq(5L), any())).willReturn(true);

		authService.oauthLogin(AuthProvider.KAKAO, "authorization-code", "state");

		verify(notificationService, never()).notify(
				any(), eq(NotificationType.JOURNAL_REMINDER), any(), any(), any(), eq("JOURNAL_REMINDER_DATE"), any());
	}

	@Test
	void reissueSendsJournalReminderWhenTodayRewardNotClaimed() {
		User existingUser = existingKakaoUser();
		com.kiwobollae.api.auth.entity.RefreshToken storedToken =
				com.kiwobollae.api.auth.entity.RefreshToken.builder()
						.user(existingUser)
						.tokenHash("refresh-token-hash")
						.expiresAt(java.time.LocalDateTime.now().plusDays(1))
						.createdAt(java.time.LocalDateTime.now())
						.build();
		given(jwtTokenProvider.validateToken("raw-refresh-token")).willReturn(true);
		given(tokenHasher.hash("raw-refresh-token")).willReturn("refresh-token-hash");
		given(refreshTokenRepository.findByTokenHash("refresh-token-hash"))
				.willReturn(Optional.of(storedToken));
		given(jwtTokenProvider.generateAccessToken(5L, UserRole.USER.name())).willReturn("access-token");
		given(jwtTokenProvider.generateRefreshToken(5L)).willReturn("new-raw-refresh-token");
		given(tokenHasher.hash("new-raw-refresh-token")).willReturn("new-refresh-token-hash");
		given(dailyJournalRewardRepository.existsForUserAndRewardDate(eq(5L), any())).willReturn(false);
		given(journalReminderLogRepository.existsByUser_IdAndReminderDate(eq(5L), any())).willReturn(false);
		given(uniqueInsertGuard.tryInsert(any())).willReturn(true);

		authService.reissue("raw-refresh-token");

		verify(notificationService).notify(
				eq(5L), eq(NotificationType.JOURNAL_REMINDER), any(), any(), any(), eq("JOURNAL_REMINDER_DATE"), any());
	}

	@Test
	void reissueRotatesRefreshTokenAndRevokesTheOldOne() {
		User existingUser = existingKakaoUser();
		com.kiwobollae.api.auth.entity.RefreshToken storedToken =
				com.kiwobollae.api.auth.entity.RefreshToken.builder()
						.user(existingUser)
						.tokenHash("refresh-token-hash")
						.expiresAt(java.time.LocalDateTime.now().plusDays(1))
						.createdAt(java.time.LocalDateTime.now())
						.build();
		given(jwtTokenProvider.validateToken("raw-refresh-token")).willReturn(true);
		given(tokenHasher.hash("raw-refresh-token")).willReturn("refresh-token-hash");
		given(refreshTokenRepository.findByTokenHash("refresh-token-hash"))
				.willReturn(Optional.of(storedToken));
		stubTokenIssuance();

		authService.reissue("raw-refresh-token");

		assertThat(storedToken.getRevokedAt()).isNotNull();
		verify(refreshTokenRepository, never()).findAllByUser_IdAndRevokedAtIsNull(any());
	}

	@Test
	void reissueOfAlreadyRotatedTokenRevokesAllSessionsForThatUser() {
		User existingUser = existingKakaoUser();
		com.kiwobollae.api.auth.entity.RefreshToken alreadyRevoked =
				com.kiwobollae.api.auth.entity.RefreshToken.builder()
						.user(existingUser)
						.tokenHash("refresh-token-hash")
						.expiresAt(java.time.LocalDateTime.now().plusDays(1))
						.createdAt(java.time.LocalDateTime.now())
						.revokedAt(java.time.LocalDateTime.now().minusMinutes(1))
						.build();
		given(jwtTokenProvider.validateToken("raw-refresh-token")).willReturn(true);
		given(tokenHasher.hash("raw-refresh-token")).willReturn("refresh-token-hash");
		given(refreshTokenRepository.findByTokenHash("refresh-token-hash"))
				.willReturn(Optional.of(alreadyRevoked));
		given(refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(5L)).willReturn(List.of());

		assertThatThrownBy(() -> authService.reissue("raw-refresh-token"))
				.isInstanceOfSatisfying(com.kiwobollae.api.global.exception.BusinessException.class, exception ->
						assertThat(exception.getErrorCode())
								.isEqualTo(com.kiwobollae.api.global.exception.ErrorCode.AUTH_TOKEN_INVALID));

		verify(refreshTokenRepository).findAllByUser_IdAndRevokedAtIsNull(5L);
	}

	private User existingKakaoUser() {
		User existingUser = User.builder()
				.email("existing-social@example.test")
				.password(null)
				.nickname("기존회원")
				.name("기존회원")
				.provider(AuthProvider.KAKAO)
				.providerId("kakao-provider-id")
				.role(UserRole.USER)
				.status(UserStatus.ACTIVE)
				.build();
		ReflectionTestUtils.setField(existingUser, "id", 5L);
		return existingUser;
	}

	private void stubExistingKakaoLogin(User existingUser) {
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
