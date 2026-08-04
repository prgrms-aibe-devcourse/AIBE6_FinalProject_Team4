package com.kiwobollae.api.global.config;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.entity.PlantSpecies;
import com.kiwobollae.api.content.entity.enums.PlantStatus;
import com.kiwobollae.api.content.repository.PlantProfileRepository;
import com.kiwobollae.api.content.repository.PlantSpeciesRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * <p>Depends on InitData (users, @Order(1)) and ProductInitData (plant species,
 * @Order(2)) having already run; skips silently if either is missing.
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
	private final PlantSpeciesRepository plantSpeciesRepository;
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

		Map<String, PlantSpecies> species = plantSpeciesRepository.findAll().stream()
				.collect(Collectors.toMap(PlantSpecies::getName, Function.identity(), (a, b) -> a));
		if (species.isEmpty()) {
			return;
		}

		plantProfileRepository.saveAll(Stream.of(
				profile(testUser, species.get("방울토마토"), "토실이", LocalDate.now().minusDays(42), PlantStatus.GROWING),
				profile(testUser, species.get("스위트 바질"), "바질이", LocalDate.now().minusDays(15), PlantStatus.GROWING),
				profile(testUser, species.get("청상추"), "쌈싸리", LocalDate.now().minusDays(8), PlantStatus.GROWING),
				profile(testUser, species.get("설향 딸기"), "딸기공주", LocalDate.now().minusDays(120), PlantStatus.HARVESTED),
				profile(testUser, species.get("로즈마리"), "로즈랑이", LocalDate.now().minusDays(30), PlantStatus.FAILED)
		).filter(p -> p != null).toList());
	}

	private PlantProfile profile(User user, PlantSpecies species, String nickname, LocalDate startDate, PlantStatus status) {
		if (species == null) {
			return null;
		}
		PlantProfile plantProfile = PlantProfile.create(user, species, nickname, startDate, null);
		if (status != PlantStatus.GROWING) {
			plantProfile.updateProfile(null, null, status);
		}
		return plantProfile;
	}
}
