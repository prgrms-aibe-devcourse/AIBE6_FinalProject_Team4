package com.kiwobollae.api.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.dto.response.OrderResponse;
import com.kiwobollae.api.commerce.entity.enums.OrderStatus;
import com.kiwobollae.api.commerce.service.OrderService;
import com.kiwobollae.api.content.dto.request.JournalImageRequest;
import com.kiwobollae.api.content.dto.request.PlantJournalRequest;
import com.kiwobollae.api.content.dto.response.PlantJournalCreateResponse;
import com.kiwobollae.api.content.entity.PlantJournal;
import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.repository.PlantJournalRepository;
import com.kiwobollae.api.content.repository.PlantProfileRepository;
import com.kiwobollae.api.content.service.PlantJournalService;
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
	@Autowired private PlantProfileRepository plantProfileRepository;
	@Autowired private PlantJournalService plantJournalService;
	@Autowired private PointTransactionService pointTransactionService;
	@Autowired private WalletService walletService;
	@Autowired private OrderService orderService;
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

		assertThat(seededJournals).isNotEmpty().allMatch(journal -> journal.getWrittenDate().isBefore(today));
		assertThat(plantProfiles)
				.extracting(PlantProfile::getJournalRewardGrantedAt)
				.containsOnlyNulls();

		assertThat(activities).hasSize(7);
		assertThat(activities)
				.extracting(PointActivityResponse::type)
				.contains(
						PointTxType.ADMIN_ADJUST,
						PointTxType.PURCHASE,
						PointTxType.RESTORE,
						PointTxType.JOURNAL_REWARD
				);

		PointActivityResponse journalReward = find(
				activities,
				PointTxType.JOURNAL_REWARD,
				PointRefType.JOURNAL_COMPLETION
		);
		assertThat(journalReward.createdAt()).isEqualTo(today.minusDays(1).atTime(9, 0));

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

		OrderResponse cancelledOrder = orderService.getOrders(user.getId(), PageRequest.of(0, 20)).stream()
				.filter(order -> order.status() == OrderStatus.CANCELLED)
				.findFirst()
				.orElseThrow();
		assertThat(cancelledOrder.usedFreePoint()).isEqualTo(300L);
		assertThat(cancelledOrder.usedPaidPoint()).isEqualTo(900L);

		WalletResponse wallet = walletService.getWallet(user.getId());
		assertThat(wallet.freePoint()).isEqualTo(100L);
		assertThat(wallet.paidPoint()).isEqualTo(7_840L);

		pointScenarioInitData.run(new DefaultApplicationArguments(new String[0]));

		assertThat(activities(user.getId())).hasSize(7);
		assertThat(walletService.getWallet(user.getId()).freePoint()).isEqualTo(100L);
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
		assertThat(activities(user.getId())).hasSize(8);
		assertThat(walletService.getWallet(user.getId()).freePoint()).isEqualTo(200L);
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
