package com.kiwobollae.api.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.dto.response.OrderResponse;
import com.kiwobollae.api.commerce.entity.enums.OrderStatus;
import com.kiwobollae.api.commerce.service.OrderService;
import com.kiwobollae.api.journal.dto.request.JournalImageRequest;
import com.kiwobollae.api.journal.dto.request.PlantJournalRequest;
import com.kiwobollae.api.journal.dto.response.PlantJournalCreateResponse;
import com.kiwobollae.api.journal.entity.PlantJournal;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.journal.repository.DailyJournalRewardRepository;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import com.kiwobollae.api.journal.service.PlantJournalService;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.repository.NotificationRepository;
import com.kiwobollae.api.point.dto.response.PointActivityResponse;
import com.kiwobollae.api.point.dto.response.WalletResponse;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.service.PointTransactionService;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("local")
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_point_scenario_seed_test"
						+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
				"spring.jpa.hibernate.ddl-auto=create-drop",
				"spring.jpa.show-sql=false",
				"payment.toss.secret-key=test_sk_point_scenario_context",
				"app.seed.point-scenario.enabled=true"
		}
)
class PointScenarioInitDataMySqlIntegrationTest {

	@Autowired private UserRepository userRepository;
	@Autowired private PlantJournalRepository plantJournalRepository;
	@Autowired private DailyJournalRewardRepository dailyJournalRewardRepository;
	@Autowired private PlantProfileRepository plantProfileRepository;
	@Autowired private PlantJournalService plantJournalService;
	@Autowired private PointTransactionService pointTransactionService;
	@Autowired private WalletService walletService;
	@Autowired private OrderService orderService;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private PointScenarioInitData pointScenarioInitData;

	@Test
	void seedsAllPointHistoryScenariosOnce() throws Exception {
		User user = userRepository.findByEmail("test@test.com").orElseThrow();
		List<PointActivityResponse> activities = activities(user.getId());
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		List<PlantJournal> seededJournals = plantJournalRepository.search(
				user.getId(),
				null,
				null,
				null,
				PageRequest.of(0, 100)
		).getContent();
		List<PlantProfile> plantProfiles = plantProfileRepository.findAllByUserId(user.getId());
		assertThat(seededJournals).isNotEmpty().allSatisfy(journal -> {
			assertThat(journal.getWrittenDate()).isBefore(today);
			assertThat(journal.getCreatedAt().toLocalDate()).isEqualTo(journal.getWrittenDate());
		});

		assertThat(activities).hasSize(9);
		assertThat(activities)
				.extracting(PointActivityResponse::type)
				.contains(
						PointTxType.ADMIN_ADJUST,
						PointTxType.PURCHASE,
						PointTxType.RESTORE,
						PointTxType.JOURNAL_REWARD
				);

		List<PointActivityResponse> journalRewards = activities.stream()
				.filter(activity -> activity.type() == PointTxType.JOURNAL_REWARD)
				.toList();
		assertThat(journalRewards).hasSize(2).allSatisfy(reward -> {
			assertThat(reward.amount()).isEqualTo(100L);
			PlantJournal rewardedJournal = plantJournalRepository.findById(reward.refId()).orElseThrow();
			assertThat(reward.createdAt()).isEqualTo(rewardedJournal.getCreatedAt());
		});
		assertThat(journalRewards)
				.extracting(reward -> reward.createdAt().toLocalDate())
				.containsExactlyInAnyOrder(today.minusDays(2), today.minusDays(1));
		assertThat(dailyJournalRewardRepository.existsForUserAndRewardDate(user.getId(), today.minusDays(2)))
				.isTrue();
		assertThat(dailyJournalRewardRepository.existsForUserAndRewardDate(user.getId(), today.minusDays(1)))
				.isTrue();
		assertThat(notificationRepository.findAll())
				.noneMatch(notification -> notification.getUser().getId().equals(user.getId())
						&& notification.getType() == NotificationType.POINT);

		PointActivityResponse orderPurchase = find(
				activities,
				PointTxType.PURCHASE,
				PointRefType.ORDER
		);
		assertThat(orderPurchase.amount()).isEqualTo(-1_200L);
		assertThat(orderPurchase.freeAmount()).isEqualTo(-300L);
		assertThat(orderPurchase.paidAmount()).isEqualTo(-900L);

		PointActivityResponse cardPurchase = find(
				activities,
				PointTxType.PURCHASE,
				PointRefType.CARD_PURCHASE
		);
		assertThat(cardPurchase.amount()).isEqualTo(-2_100L);
		assertThat(cardPurchase.freeAmount()).isEqualTo(-1_940L);
		assertThat(cardPurchase.paidAmount()).isEqualTo(-160L);
		assertThat(journalRewards).allSatisfy(reward ->
				assertThat(reward.createdAt()).isAfter(cardPurchase.createdAt()));
		PointActivityResponse finalBonusGrant = activities.stream()
				.filter(activity -> activity.type() == PointTxType.ADMIN_ADJUST
						&& activity.amount() == 200L
						&& activity.freeAmount() == 200L)
				.findFirst()
				.orElseThrow();
		assertThat(journalRewards).allSatisfy(reward ->
				assertThat(reward.createdAt()).isAfter(finalBonusGrant.createdAt()));

		OrderResponse cancelledOrder = orderService.getOrders(user.getId(), PageRequest.of(0, 20)).stream()
				.filter(order -> order.status() == OrderStatus.CANCELLED)
				.findFirst()
				.orElseThrow();
		assertThat(cancelledOrder.usedFreePoint()).isEqualTo(300L);
		assertThat(cancelledOrder.usedPaidPoint()).isEqualTo(900L);

		WalletResponse wallet = walletService.getWallet(user.getId());
		assertThat(wallet.freePoint()).isEqualTo(400L);
		assertThat(wallet.paidPoint()).isEqualTo(7_840L);
		assertThat(activities.getFirst().freeBalanceAfter()).isEqualTo(wallet.freePoint());

		pointScenarioInitData.run(new DefaultApplicationArguments(new String[0]));

		assertThat(activities(user.getId())).hasSize(9);
		assertThat(walletService.getWallet(user.getId()).freePoint()).isEqualTo(400L);
		assertThat(walletService.getWallet(user.getId()).paidPoint()).isEqualTo(7_840L);

		PlantJournalCreateResponse todayJournal = plantJournalService.createJournal(
				user.getId(),
				new PlantJournalRequest(
						plantProfiles.getFirst().getId(),
						"오늘 성장일지 보상 테스트",
						List.of(new JournalImageRequest(
								"https://placehold.co/800x800/E8F3D8/4B7A1E?text=Today+Reward",
								"point-scenario-today-reward-test",
								true
						))
				)
		);

		assertThat(todayJournal.journal().writtenDate()).isEqualTo(today);
		assertThat(todayJournal.rewardGranted()).isTrue();
		assertThat(todayJournal.rewardAmount()).isEqualTo(100L);
		assertThat(activities(user.getId())).hasSize(10);
		assertThat(walletService.getWallet(user.getId()).freePoint()).isEqualTo(500L);
	}

	private List<PointActivityResponse> activities(Long userId) {
		return pointTransactionService.getActivities(
				userId,
				null,
				null,
				null,
				null,
				PageRequest.of(0, 100)
		).getContent();
	}

	private PointActivityResponse find(
			List<PointActivityResponse> activities,
			PointTxType type,
			PointRefType refType
	) {
		return activities.stream()
				.filter(activity -> activity.type() == type && activity.refType() == refType)
				.findFirst()
				.orElseThrow();
	}
}
