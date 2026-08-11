package com.kiwobollae.api.plantProfile.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import com.kiwobollae.api.species.entity.PlantSpecies;
import com.kiwobollae.api.species.repository.PlantSpeciesRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_plant_profile_test"
						+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
				"spring.jpa.hibernate.ddl-auto=create-drop"
		}
)
class PlantProfileRepositorySortMySqlIntegrationTest {

	@Autowired
	private PlantProfileRepository plantProfileRepository;

	@Autowired
	private PlantSpeciesRepository plantSpeciesRepository;

	@Autowired
	private UserRepository userRepository;

	private Long userId;

	@BeforeEach
	void setUp() {
		clearData();

		User user = userRepository.saveAndFlush(User.builder()
				.email("plant-sort@example.test")
				.password("encoded-password")
				.nickname("plant-sort")
				.name("정렬테스트")
				.provider(AuthProvider.LOCAL)
				.role(UserRole.USER)
				.level(1)
				.status(UserStatus.ACTIVE)
				.build());
		userId = user.getId();
	}

	@AfterEach
	void tearDown() {
		clearData();
	}

	@Test
	void ordersByStatusPriorityThenByCreatedAtDescWithinEachStatus() {
		PlantSpecies species = plantSpeciesRepository.saveAndFlush(PlantSpecies.builder()
				.name("바질")
				.category("HERB")
				.build());

		LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

		// 생성 순서를 우선순위와 반대로 섞어서, 단순 createdAt 정렬로는 통과할 수 없게 만든다.
		PlantProfile failed = savePlant(species, "실패한 식물", PlantStatus.FAILED, base.plusMinutes(3));
		PlantProfile harvested = savePlant(species, "수확한 식물", PlantStatus.HARVESTED, base.plusMinutes(2));
		PlantProfile growingOld = savePlant(species, "먼저 심은 식물", PlantStatus.GROWING, base);
		PlantProfile growingNew = savePlant(species, "나중에 심은 식물", PlantStatus.GROWING, base.plusMinutes(1));

		List<PlantProfile> result = plantProfileRepository
				.findAllByUserIdAndStatus(userId, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
				.getContent();

		assertThat(result)
				.extracting(PlantProfile::getId)
				.containsExactly(
						growingNew.getId(), growingOld.getId(),
						harvested.getId(),
						failed.getId());
	}

	private PlantProfile savePlant(PlantSpecies species, String name, PlantStatus status, LocalDateTime createdAt) {
		PlantProfile profile = PlantProfile.builder()
				.user(userRepository.getReferenceById(userId))
				.species(species)
				.plantName(name)
				.startDate(LocalDate.now())
				.status(status)
				.createdAt(createdAt)
				.build();
		return plantProfileRepository.saveAndFlush(profile);
	}

	private void clearData() {
		plantProfileRepository.deleteAllInBatch();
		plantSpeciesRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}
}
