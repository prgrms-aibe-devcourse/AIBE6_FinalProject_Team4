package com.kiwobollae.api.global.config;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import java.time.LocalDate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local-only sample plant profiles so /plants isn't empty for the InitData test
 * users on a fresh DB — useful while journal image upload (and thus real profile
 * registration end-to-end) is still blocked.
 *
 * <p>Depends on InitData (users, @Order(1)) having already run; skips silently if missing.
 * 종은 더 이상 별도 카탈로그를 참조하지 않고 사용자가 직접 입력하는 것과 동일하게 이름 문자열을 그대로 넣는다.
 *
 * <p>Disable without changing code by setting {@code app.seed.plant-profile.enabled=false}.
 */
@Component
@Profile({"local", "prod"})
@ConditionalOnProperty(prefix = "app.seed.plant-profile", name = "enabled", havingValue = "true")
@Order(3)
@RequiredArgsConstructor
public class PlantProfileInitData implements ApplicationRunner {

	private final UserRepository userRepository;
	private final PlantProfileRepository plantProfileRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (plantProfileRepository.count() > 0) {
			return;
		}

		User testUser = userRepository.findByEmail("test@test.com").orElse(null);
		if (testUser == null) {
			return;
		}

		plantProfileRepository.saveAll(Stream.of(
				profile(testUser, "방울토마토", "토실이", LocalDate.now().minusDays(42), PlantStatus.GROWING),
				profile(testUser, "스위트 바질", "바질이", LocalDate.now().minusDays(15), PlantStatus.GROWING),
				profile(testUser, "청상추", "쌈싸리", LocalDate.now().minusDays(8), PlantStatus.GROWING),
				profile(testUser, "설향 딸기", "딸기공주", LocalDate.now().minusDays(120), PlantStatus.HARVESTED),
				profile(testUser, "로즈마리", "로즈랑이", LocalDate.now().minusDays(30), PlantStatus.FAILED)
		).toList());
	}

	private PlantProfile profile(User user, String speciesName, String nickname, LocalDate startDate, PlantStatus status) {
		PlantProfile plantProfile = PlantProfile.create(user, speciesName, nickname, startDate, null);
		if (status != PlantStatus.GROWING) {
			plantProfile.updateProfile(null, null, status);
		}
		return plantProfile;
	}
}
