package com.kiwobollae.api.global.config;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.dto.request.CardPurchaseRequest;
import com.kiwobollae.api.commerce.dto.request.CartItemRequest;
import com.kiwobollae.api.commerce.dto.request.OrderCreateRequest;
import com.kiwobollae.api.commerce.dto.response.CartItemResponse;
import com.kiwobollae.api.commerce.dto.response.OrderResponse;
import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.entity.enums.DeliveryStatus;
import com.kiwobollae.api.commerce.entity.enums.OrderStatus;
import com.kiwobollae.api.commerce.repository.CardRepository;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import com.kiwobollae.api.commerce.service.CardPurchaseService;
import com.kiwobollae.api.commerce.service.CartService;
import com.kiwobollae.api.commerce.service.OrderService;
import com.kiwobollae.api.journal.entity.PlantJournal;
import com.kiwobollae.api.journal.entity.DailyJournalReward;
import com.kiwobollae.api.journal.repository.DailyJournalRewardRepository;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentRequest;
import com.kiwobollae.api.point.entity.enums.AdminPointAdjustmentReason;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.service.AdminPointAdjustmentService;
import com.kiwobollae.api.point.service.PointTransactionTimeProvider;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code test@test.com} 사용자의 포인트 내역 화면을 로컬에서 바로 확인하기 위한 시나리오 시드다.
 *
 * <p>각 쓰기는 해당 도메인의 application service를 통해 실행한다. 고정 멱등키와 데이터 표식으로
 * 애플리케이션 재시작 시 같은 거래가 중복 생성되지 않는다. 화면에서 시간 순서와 잔액 스냅샷도
 * 자연스럽게 이어지도록 로컬 시드 원장의 발생 시각을 일지 작성일 이전부터 순서대로 정렬한다.
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.seed.point-scenario", name = "enabled", havingValue = "true")
@Order(5)
@RequiredArgsConstructor
public class PointScenarioInitData implements ApplicationRunner {

	private static final String TEST_EMAIL = "test@test.com";
	private static final String ADMIN_EMAIL = "admin@test.com";
	private static final String ORDER_MARKER = "POINT_SCENARIO_V1";
	private static final long JOURNAL_REWARD_AMOUNT = 100L;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final CardRepository cardRepository;
	private final PlantJournalRepository plantJournalRepository;
	private final DailyJournalRewardRepository dailyJournalRewardRepository;
	private final PointTransactionRepository pointTransactionRepository;
	private final AdminPointAdjustmentService adminPointAdjustmentService;
	private final CartService cartService;
	private final OrderService orderService;
	private final CardPurchaseService cardPurchaseService;
	private final WalletService walletService;
	private final PointTransactionTimeProvider pointTransactionTimeProvider;
	private final PlatformTransactionManager transactionManager;

	@Override
	public void run(ApplicationArguments args) {
		User testUser = userRepository.findByEmail(TEST_EMAIL).orElse(null);
		User admin = userRepository.findByEmail(ADMIN_EMAIL).orElse(null);
		if (testUser == null || admin == null) {
			log.warn("포인트 시나리오 시드를 건너뜁니다: test/admin 로컬 계정이 없습니다.");
			return;
		}

		LocalDate yesterday = LocalDate.now(KST).minusDays(1);
		SeedTimeline timeline = SeedTimeline.from(yesterday);
		seedSafely("운영팀 포인트 지급·차감",
				() -> seedAdminAdjustments(admin.getId(), testUser.getId(), timeline));
		seedSafely("상품 주문 혼합 결제·취소",
				() -> seedOrderAndCancellation(testUser.getId(), timeline));
		seedSafely("쿠폰 혼합 결제", () -> seedCardPurchase(testUser.getId(), timeline));
		seedSafely("보너스 포인트 잔액 준비",
				() -> seedFinalBonusBalance(admin.getId(), testUser.getId(), timeline));
		seedSafely("성장일지 작성 보상", () -> seedJournalRewards(testUser, yesterday));
	}

	private void seedAdminAdjustments(Long adminUserId, Long userId, SeedTimeline timeline) {
		pointTransactionTimeProvider.runAt(timeline.freeGrantAt(), () ->
				adminPointAdjustmentService.adjust(
						adminUserId,
						"seed-point-free-grant-v1",
						new AdminPointAdjustmentRequest(
								userId, CurrencyType.FREE, 800L, AdminPointAdjustmentReason.SPECIAL_EVENT)
				));
		pointTransactionTimeProvider.runAt(timeline.freeDeductAt(), () ->
				adminPointAdjustmentService.adjust(
						adminUserId,
						"seed-point-free-deduct-v1",
						new AdminPointAdjustmentRequest(
								userId, CurrencyType.FREE, -100L, AdminPointAdjustmentReason.FRAUD_PENALTY)
				));
		pointTransactionTimeProvider.runAt(timeline.paidGrantAt(), () ->
				adminPointAdjustmentService.adjust(
						adminUserId,
						"seed-point-paid-grant-v1",
						new AdminPointAdjustmentRequest(
								userId, CurrencyType.PAID, 5_000L, AdminPointAdjustmentReason.OUTSTANDING_MEMBER)
				));
	}

	private void seedOrderAndCancellation(Long userId, SeedTimeline timeline) {
		OrderResponse order = findSeedOrder(userId);
		if (order == null) {
			Product product = productRepository.findAll().stream()
					.filter(candidate -> "방울토마토 모종".equals(candidate.getName()))
					.findFirst()
					.orElse(null);
			if (product == null) {
				log.warn("상품 주문 시나리오를 건너뜁니다: 방울토마토 모종이 없습니다.");
				return;
			}

			CartItemResponse cartItem = cartService.addItem(userId, new CartItemRequest(product.getId(), 1));
			if (cartItem.quantity() != 1) {
				cartItem = cartService.updateQuantity(userId, cartItem.id(), 1);
			}
			CartItemResponse seedCartItem = cartItem;
			order = pointTransactionTimeProvider.callAt(timeline.orderPurchaseAt(), () ->
					orderService.createOrder(
							userId,
							"seed-point-order-v1",
							new OrderCreateRequest(
									List.of(seedCartItem.id()),
									300L,
									"김초록",
									"01022223333",
									"04524",
									"서울특별시 중구 세종대로 110",
									ORDER_MARKER
							)
					).order());
		}

		if (order.status() == OrderStatus.PAID
				&& order.deliveryStatus() == DeliveryStatus.PREPARING) {
			OrderResponse seedOrder = order;
			pointTransactionTimeProvider.runAt(
					timeline.orderRestoreAt(), () -> orderService.cancelOrder(userId, seedOrder.id()));
		}
	}

	private OrderResponse findSeedOrder(Long userId) {
		return orderService.getOrders(
				userId,
				PageRequest.of(0, 100, Sort.by(Sort.Order.desc("id")))
		).stream()
				.filter(order -> ORDER_MARKER.equals(order.addressDetail()))
				.findFirst()
				.orElse(null);
	}

	private void seedCardPurchase(Long userId, SeedTimeline timeline) {
		Card card = cardRepository.findAllByStatusOrderByCreatedAtDesc(ActiveStatus.ON_SALE).stream()
				.filter(candidate -> "수박 쿠폰".equals(candidate.getName()))
				.findFirst()
				.orElse(null);
		if (card == null) {
			log.warn("쿠폰 구매 시나리오를 건너뜁니다: 수박 쿠폰이 없습니다.");
			return;
		}
		pointTransactionTimeProvider.runAt(timeline.cardPurchaseAt(), () ->
				cardPurchaseService.purchase(
						userId,
						"seed-point-card-purchase-v1",
						new CardPurchaseRequest(card.getId(), 7)
				));
	}

	private void seedFinalBonusBalance(Long adminUserId, Long userId, SeedTimeline timeline) {
		pointTransactionTimeProvider.runAt(timeline.finalBonusAt(), () ->
				adminPointAdjustmentService.adjust(
						adminUserId,
						"seed-point-final-free-balance-v1",
						new AdminPointAdjustmentRequest(
								userId, CurrencyType.FREE, 200L, AdminPointAdjustmentReason.OUTSTANDING_MEMBER)
				));
	}

	private void seedJournalRewards(User user, LocalDate yesterday) {
		List.of(yesterday.minusDays(1), yesterday)
				.forEach(rewardDate -> seedJournalReward(user, rewardDate));
	}

	private void seedJournalReward(User user, LocalDate rewardDate) {
		PlantJournal journal = plantJournalRepository.search(
				user.getId(),
				null,
				rewardDate,
				rewardDate,
				PageRequest.of(
						0,
						1,
						Sort.by(Sort.Order.desc("writtenDate"), Sort.Order.desc("id"))
				)
		).stream()
				.findFirst()
				.orElse(null);
		if (journal == null) {
			log.warn("성장일지 보상 시나리오를 건너뜁니다: {} 식물일지가 없습니다.", rewardDate);
			return;
		}
		LocalDateTime rewardedAt = journal.getCreatedAt();
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			boolean rewardAlreadyExists = pointTransactionRepository.existsByTypeAndRefTypeAndRefId(
					PointTxType.JOURNAL_REWARD,
					PointRefType.JOURNAL_COMPLETION,
					journal.getId()
			);
			if (!rewardAlreadyExists) {
				pointTransactionTimeProvider.runAt(
						rewardedAt, () -> walletService.rewardJournal(user.getId(), journal.getId()));
			}
			if (!dailyJournalRewardRepository.existsForUserAndRewardDate(user.getId(), rewardDate)) {
				dailyJournalRewardRepository.save(DailyJournalReward.builder()
						.user(user)
						.rewardDate(rewardDate)
						.journalId(journal.getId())
						.rewardAmount(JOURNAL_REWARD_AMOUNT)
						.rewardedAt(rewardedAt)
						.build());
			}
		});
	}

	private void seedSafely(String scenario, Runnable seed) {
		try {
			seed.run();
			log.info("로컬 포인트 시나리오 준비 완료: {}", scenario);
		} catch (RuntimeException exception) {
			log.warn("로컬 포인트 시나리오 준비 실패: {}", scenario, exception);
		}
	}

	private record SeedTimeline(
			LocalDateTime freeGrantAt,
			LocalDateTime freeDeductAt,
			LocalDateTime paidGrantAt,
			LocalDateTime orderPurchaseAt,
			LocalDateTime orderRestoreAt,
			LocalDateTime cardPurchaseAt,
			LocalDateTime finalBonusAt
	) {
		private static SeedTimeline from(LocalDate yesterday) {
			LocalDate firstRewardDate = yesterday.minusDays(1);
			return new SeedTimeline(
					firstRewardDate.minusDays(5).atTime(9, 0),
					firstRewardDate.minusDays(5).atTime(10, 0),
					firstRewardDate.minusDays(5).atTime(11, 0),
					firstRewardDate.minusDays(4).atTime(10, 0),
					firstRewardDate.minusDays(4).atTime(10, 5),
					firstRewardDate.minusDays(2).atTime(14, 0),
					firstRewardDate.minusDays(1).atTime(18, 0)
			);
		}
	}
}
