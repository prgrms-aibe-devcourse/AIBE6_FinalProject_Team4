package com.kiwobollae.api.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.journal.dto.request.JournalImageRequest;
import com.kiwobollae.api.journal.dto.request.PlantJournalRequest;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.species.entity.PlantSpecies;
import com.kiwobollae.api.journal.repository.JournalImageRepository;
import com.kiwobollae.api.journal.repository.DailyJournalRewardRepository;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import com.kiwobollae.api.species.repository.PlantSpeciesRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_point_test"
						+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
				"spring.jpa.hibernate.ddl-auto=create-drop"
		}
)
class PlantJournalServiceMySqlIntegrationTest {

	@Autowired
	private PlantJournalService plantJournalService;

	@Autowired
	private PlantJournalRepository plantJournalRepository;

	@Autowired
	private JournalImageRepository journalImageRepository;

	@Autowired
	private DailyJournalRewardRepository dailyJournalRewardRepository;

	@Autowired
	private PlantProfileRepository plantProfileRepository;

	@Autowired
	private PlantSpeciesRepository plantSpeciesRepository;

	@Autowired
	private PointTransactionRepository pointTransactionRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	private Long userId;
	private Long plantProfileId;

	@BeforeEach
	void setUp() {
		clearData();

		User user = userRepository.saveAndFlush(User.builder()
				.email("journal-rollback@example.test")
				.password("encoded-password")
				.nickname("journal-rollback")
				.name("일지롤백테스트")
				.provider(AuthProvider.LOCAL)
				.role(UserRole.USER)
				.level(1)
				.status(UserStatus.ACTIVE)
				.build());
		userId = user.getId();

		PlantSpecies species = plantSpeciesRepository.saveAndFlush(PlantSpecies.builder()
				.name("바질")
				.category("HERB")
				.build());
		PlantProfile profile = plantProfileRepository.saveAndFlush(
				PlantProfile.create(
						user,
						species,
						"롤백 바질",
						LocalDate.now(),
						null
				)
		);
		plantProfileId = profile.getId();
	}

	@AfterEach
	void tearDown() {
		clearData();
	}

	@Test
	void missingWalletRollsBackJournalImagesAndRewardClaim() {
		PlantJournalRequest request = new PlantJournalRequest(
				plantProfileId,
				"지갑이 없으면 저장되지 않아야 합니다.",
				List.of(new JournalImageRequest(
						"/api/v1/journals/images/rollback-test.webp",
						"rollback-image-hash",
						true
				))
		);

		assertThatThrownBy(() -> plantJournalService.createJournal(userId, request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.POINT_WALLET_NOT_FOUND));

		entityManager.clear();
		assertThat(plantJournalRepository.count()).isZero();
		assertThat(journalImageRepository.count()).isZero();
		assertThat(pointTransactionRepository.count()).isZero();
		assertThat(walletRepository.count()).isZero();
		assertThat(dailyJournalRewardRepository.count()).isZero();
	}

	private void clearData() {
		pointTransactionRepository.deleteAllInBatch();
		dailyJournalRewardRepository.deleteAllInBatch();
		journalImageRepository.deleteAllInBatch();
		plantJournalRepository.deleteAllInBatch();
		plantProfileRepository.deleteAllInBatch();
		plantSpeciesRepository.deleteAllInBatch();
		walletRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}
}
