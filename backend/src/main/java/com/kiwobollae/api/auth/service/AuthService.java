package com.kiwobollae.api.auth.service;

import com.kiwobollae.api.auth.dto.request.LoginRequest;
import com.kiwobollae.api.auth.dto.request.SignupRequest;
import com.kiwobollae.api.auth.dto.response.LoginResponse;
import com.kiwobollae.api.auth.dto.response.UserResponse;
import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	@Transactional
	public UserResponse signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}
		if (userRepository.existsByNickname(request.nickname())) {
			throw new BusinessException(ErrorCode.AUTH_NICKNAME_ALREADY_EXISTS);
		}

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

		return UserResponse.from(userRepository.save(user));
	}

	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

		if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_ACTIVE);
		}

		String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole().name());
		return new LoginResponse(accessToken, "Bearer", UserResponse.from(user));
	}
}
