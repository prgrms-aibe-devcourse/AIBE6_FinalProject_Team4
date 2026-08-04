package com.kiwobollae.api.global.config;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.mypage.entity.UserAddress;
import com.kiwobollae.api.mypage.repository.UserAddressRepository;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a handful of test users on startup so there's something to log in with
 * without hitting /auth/signup by hand. Local dev only — never runs in prod.
 *
 * <p>Ordered first (see @Order) so InitData runners that seed data owned by these
 * users (e.g. PlantProfileInitData) can rely on them already existing.
 */
@Component
@Profile({"local", "prod"})
@Order(1)
@RequiredArgsConstructor
public class InitData implements ApplicationRunner {

	private final UserRepository userRepository;
	private final WalletRepository walletRepository;
	private final UserAddressRepository userAddressRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (userRepository.count() > 0) {
			return;
		}

		createUser("admin@test.com", "1234", "관리자", "관리자", "01011112222", UserRole.ADMIN, 1240L, 3000L,
				"06236", "서울특별시 강남구 테헤란로 123", "101동 202호");
		createUser("test@test.com", "1234", "초록", "김초록", "01022223333", UserRole.USER, 1240L, 3000L,
				"04524", "서울특별시 중구 세종대로 110", "1층");
		createUser("user@test.com", "1234", "바질이", "박바질", "01033334444", UserRole.USER, 500L, 0L,
				"48058", "부산광역시 해운대구 센텀중앙로 90", "302호");
	}

	private void createUser(
			String email, String rawPassword, String nickname, String name, String phoneNumber,
			UserRole role, Long freePoint, Long paidPoint,
			String zipCode, String address, String addressDetail
	) {
		User user = User.builder()
				.email(email)
				.password(passwordEncoder.encode(rawPassword))
				.nickname(nickname)
				.name(name)
				.phoneNumber(phoneNumber)
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
				.build();
		walletRepository.save(wallet);

		UserAddress userAddress = UserAddress.create(
				user, name, phoneNumber, zipCode, address, addressDetail, true
		);
		userAddressRepository.save(userAddress);
	}
}
