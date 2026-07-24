package com.kiwobollae.api.global.config;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a handful of test users on startup so there's something to log in with
 * without hitting /auth/signup by hand. Local dev only — never runs in prod.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
public class InitData implements ApplicationRunner {

	private final UserRepository userRepository;
	private final WalletRepository walletRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (userRepository.count() > 0) {
			return;
		}

		createUser("admin@test.com", "1234", "관리자", "관리자", UserRole.ADMIN, 1240L, 3000L);
		createUser("test@test.com", "1234", "초록", "김초록", UserRole.USER, 1240L, 3000L);
		createUser("user@test.com", "1234", "바질이", "박바질", UserRole.USER, 500L, 0L);
	}

	private void createUser(String email, String rawPassword, String nickname, String name, UserRole role, Long freePoint, Long paidPoint) {
		User user = User.builder()
				.email(email)
				.password(passwordEncoder.encode(rawPassword))
				.nickname(nickname)
				.name(name)
				.provider(AuthProvider.LOCAL)
				.role(role)
				.level(1)
				.status(UserStatus.ACTIVE)
				.build();
		userRepository.save(user);

		Wallet wallet = Wallet.builder()
				.user(user)
				.freePoint(freePoint)
				.paidPoint(paidPoint)
				.version(0L)
				.build();
		walletRepository.save(wallet);
	}
}
