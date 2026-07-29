package com.kiwobollae.api.auth.service;

import com.kiwobollae.api.auth.dto.request.*;
import com.kiwobollae.api.auth.dto.response.AccessReissueResult;
import com.kiwobollae.api.auth.dto.response.NicknameAvailabilityResponse;
import com.kiwobollae.api.auth.dto.response.TokenIssueResult;
import com.kiwobollae.api.auth.dto.response.UserResponse;
import com.kiwobollae.api.auth.entity.RefreshToken;
import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.oauth.OAuthClient;
import com.kiwobollae.api.auth.oauth.OAuthUserInfo;
import com.kiwobollae.api.auth.repository.RefreshTokenRepository;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.security.JwtTokenProvider;
import com.kiwobollae.api.point.service.WalletService;
import com.kiwobollae.api.global.security.TokenHasher;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final TokenHasher tokenHasher;
	private final EmailVerificationService emailVerificationService;
	private final List<OAuthClient> oAuthClients;
	private final SecureRandom random = new SecureRandom();

	private static final int NICKNAME_MAX_LENGTH = 12;

	public NicknameAvailabilityResponse checkNicknameAvailability(String nickname) {
		return new NicknameAvailabilityResponse(!userRepository.existsByNickname(nickname));
	}
	private final WalletService walletService;

	@Transactional
	public UserResponse signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}
		if (userRepository.existsByNickname(request.nickname())) {
			throw new BusinessException(ErrorCode.AUTH_NICKNAME_ALREADY_EXISTS);
		}
		emailVerificationService.assertVerified(request.email());

		User user = User.builder()
				.email(request.email())
				.password(passwordEncoder.encode(request.password()))
				.nickname(request.nickname())
				.name(request.name())
				.phoneNumber(request.phoneNumber())
				.provider(AuthProvider.LOCAL)
				.role(UserRole.USER)
				.level(1)
				.status(UserStatus.ACTIVE)
				.build();

		User savedUser = userRepository.save(user);
		walletService.createWallet(savedUser); // POINT-10: 가입 트랜잭션에서 지갑 자동 생성
		return UserResponse.from(savedUser);
	}

	@Transactional
	public TokenIssueResult login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

		if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_ACTIVE);
		}

		return issueTokens(user);
	}

	@Transactional
	public TokenIssueResult oauthLogin(AuthProvider provider, String code, String state) {
		OAuthClient client = oAuthClients.stream()
				.filter(c -> c.getProvider() == provider)
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_OAUTH_PROVIDER_UNSUPPORTED));

		OAuthUserInfo userInfo = client.fetchUserInfo(code, state);

		User user = userRepository.findByProviderAndProviderId(provider, userInfo.providerId())
				.orElseGet(() -> registerOAuthUser(provider, userInfo));

		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_ACTIVE);
		}
		return issueTokens(user);
	}

	/**
	 * First-time social login for this (provider, providerId). If the email is
	 * already registered under a different account (LOCAL or another provider),
	 * we refuse rather than silently linking them — merging accounts is a
	 * product decision the team hasn't made, not something to guess here.
	 */
	private User registerOAuthUser(AuthProvider provider, OAuthUserInfo userInfo) {
		if (userRepository.existsByEmail(userInfo.email())) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}

		String displayName = userInfo.nickname() != null ? userInfo.nickname() : "회원";
		User user = User.builder()
				.email(userInfo.email())
				.password(null)
				.nickname(generateUniqueNickname(displayName))
				.name(displayName.length() > 10 ? displayName.substring(0, 10) : displayName)
				.provider(provider)
				.providerId(userInfo.providerId())
				.role(UserRole.USER)
				.level(1)
				.status(UserStatus.ACTIVE)
				.build();
		User savedUser = userRepository.save(user);
		walletService.createWallet(savedUser); // POINT-10: 소셜 자동가입 트랜잭션에서 지갑 생성
		return savedUser;
	}

	private String generateUniqueNickname(String base) {
		String trimmed = base.length() > NICKNAME_MAX_LENGTH ? base.substring(0, NICKNAME_MAX_LENGTH) : base;
		if (!userRepository.existsByNickname(trimmed)) {
			return trimmed;
		}

		String prefix = trimmed.length() > 8 ? trimmed.substring(0, 8) : trimmed;
		for (int attempt = 0; attempt < 20; attempt++) {
			String candidate = prefix + String.format("%04d", random.nextInt(10_000));
			if (!userRepository.existsByNickname(candidate)) {
				return candidate;
			}
		}
		throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR, "닉네임 생성에 실패했습니다. 다시 시도해 주세요.");
	}

	public AccessReissueResult reissue(String rawRefreshToken) {
		if (rawRefreshToken == null || !jwtTokenProvider.validateToken(rawRefreshToken)) {
			throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
		}

		String tokenHash = tokenHasher.hash(rawRefreshToken);
		RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));

		if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
		}

		// No rotation: the same refresh token (and its cookie) stays valid and gets
		// reused until it expires on its own or the user logs out.
		User user = stored.getUser();
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_ACTIVE);
		}

		String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole().name());
		return new AccessReissueResult(accessToken, "Bearer", UserResponse.from(user));
	}

	public UserResponse getMe(Long userId) {
		return UserResponse.from(findActiveUser(userId));
	}

	@Transactional
	public UserResponse updateProfile(Long userId, UserUpdateRequest request) {
		User user = findActiveUser(userId);

		if (request.nickname() != null && userRepository.existsByNicknameAndIdNot(request.nickname(), userId)) {
			throw new BusinessException(ErrorCode.AUTH_NICKNAME_ALREADY_EXISTS);
		}

		user.updateProfile(request.nickname(), request.name(), request.phoneNumber());
		return UserResponse.from(user);
	}

	public void verifyPassword(Long userId, PasswordVerifyRequest request) {
		User user = findActiveUser(userId);

		if (user.getPassword() == null) {
			throw new BusinessException(ErrorCode.AUTH_SOCIAL_ACCOUNT_HAS_NO_PASSWORD);
		}
		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new BusinessException(ErrorCode.AUTH_CURRENT_PASSWORD_MISMATCH);
		}
	}

	@Transactional
	public void changePassword(Long userId, PasswordChangeRequest request) {
		User user = findActiveUser(userId);

		if (user.getPassword() == null) {
			throw new BusinessException(ErrorCode.AUTH_SOCIAL_ACCOUNT_HAS_NO_PASSWORD);
		}
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			throw new BusinessException(ErrorCode.AUTH_CURRENT_PASSWORD_MISMATCH);
		}

		user.changePassword(passwordEncoder.encode(request.newPassword()));
	}

	@Transactional
	public void resetPassword(PasswordResetRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_EMAIL_NOT_FOUND));

		if (user.getPassword() == null) {
			throw new BusinessException(ErrorCode.AUTH_SOCIAL_ACCOUNT_HAS_NO_PASSWORD);
		}
		emailVerificationService.consumePasswordResetToken(request.email(), request.resetToken());

		user.changePassword(passwordEncoder.encode(request.newPassword()));

		// Reset implies the previous password may have been compromised, so every
		// existing session is force-logged-out the same way withdraw() does.
		LocalDateTime now = LocalDateTime.now();
		refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(user.getId())
				.forEach(token -> token.revoke(now));
	}

	@Transactional
	public void withdraw(Long userId, WithdrawRequest request) {
		// findActiveUser already rejects a second withdraw (status would be WITHDRAWN,
		// which is "not active") with the same AUTH_ACCOUNT_NOT_ACTIVE error.
		User user = findActiveUser(userId);

		if (user.getPassword() != null) {
			// LOCAL accounts must re-confirm with their password; social accounts have
			// none to check, so a valid access token alone is treated as confirmation.
			if (request.password() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
				throw new BusinessException(ErrorCode.AUTH_CURRENT_PASSWORD_MISMATCH);
			}
		}

		user.withdraw();

		LocalDateTime now = LocalDateTime.now();
		refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(userId)
				.forEach(token -> token.revoke(now));
	}

	private User findById(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
	}

	/**
	 * Same as findById, but also rejects SUSPENDED/RESTRICTED/WITHDRAWN accounts —
	 * an access token can outlive an admin action taken mid-session (up to its
	 * expiry), so every /auth/me/** action re-checks status instead of trusting
	 * "the token was valid at issue time" the way a stateless JWT alone would.
	 */
	private User findActiveUser(Long userId) {
		User user = findById(userId);
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_ACTIVE);
		}
		return user;
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		if (rawRefreshToken == null) {
			return;
		}
		String tokenHash = tokenHasher.hash(rawRefreshToken);
		refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
				.ifPresent(stored -> stored.revoke(LocalDateTime.now()));
	}

	private TokenIssueResult issueTokens(User user) {
		String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole().name());
		String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

		RefreshToken entity = RefreshToken.builder()
				.user(user)
				.tokenHash(tokenHasher.hash(refreshToken))
				.expiresAt(LocalDateTime.now().plusNanos(jwtTokenProvider.getRefreshExpirationMs() * 1_000_000L))
				.createdAt(LocalDateTime.now())
				.build();
		refreshTokenRepository.save(entity);

		return new TokenIssueResult(accessToken, "Bearer", UserResponse.from(user), refreshToken);
	}
}
