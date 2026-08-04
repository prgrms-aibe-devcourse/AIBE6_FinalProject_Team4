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
import com.kiwobollae.api.content.entity.PlantJournal;
import com.kiwobollae.api.content.repository.PlantJournalRepository;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentRequest;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.service.AdminPointAdjustmentService;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDate;
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

/**
 * {@code test@test.com} 사용자의 포인트 내역 화면을 로컬에서 바로 확인하기 위한 시나리오 시드다.
 *
 * <p>각 쓰기는 해당 도메인의 application service를 통해 실행한다. 고정 멱등키와 데이터 표식으로
 * 애플리케이션 재시작 시 같은 거래가 중복 생성되지 않는다.
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
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final CardRepository cardRepository;
	private final PlantJournalRepository plantJournalRepository;
	private final PointTransactionRepository pointTransactionRepository;
	private final AdminPointAdjustmentService adminPointAdjustmentService;
	private final CartService cartService;
	private final OrderService orderService;
	private final CardPurchaseService cardPurchaseService;
	private final WalletService walletService;

	@Override
	public void run(ApplicationArguments args) {
		User testUser = userRepository.findByEmail(TEST_EMAIL).orElse(null);
		User admin = userRepository.findByEmail(ADMIN_EMAIL).orElse(null);
		if (testUser == null || admin == null) {
			log.warn("포인트 시나리오 시드를 건너뜁니다: test/admin 로컬 계정이 없습니다.");
			return;
		}

		seedSafely("운영팀 포인트 지급·차감", () -> seedAdminAdjustments(admin.getId(), testUser.getId()));
		seedSafely("상품 주문 혼합 결제·취소", () -> seedOrderAndCancellation(testUser.getId()));
		seedSafely("카드 혼합 결제", () -> seedCardPurchase(testUser.getId()));
		seedSafely("성장일지 작성 보상", () -> seedJournalReward(testUser.getId()));
	}

	private void seedAdminAdjustments(Long adminUserId, Long userId) {
		adminPointAdjustmentService.adjust(
				adminUserId,
				"seed-point-free-grant-v1",
				new AdminPointAdjustmentRequest(userId, CurrencyType.FREE, 800L)
		);
		adminPointAdjustmentService.adjust(
				adminUserId,
				"seed-point-free-deduct-v1",
				new AdminPointAdjustmentRequest(userId, CurrencyType.FREE, -100L)
		);
		adminPointAdjustmentService.adjust(
				adminUserId,
				"seed-point-paid-grant-v1",
				new AdminPointAdjustmentRequest(userId, CurrencyType.PAID, 5_000L)
		);
	}

	private void seedOrderAndCancellation(Long userId) {
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
			order = orderService.createOrder(
					userId,
					"seed-point-order-v1",
					new OrderCreateRequest(
							List.of(cartItem.id()),
							300L,
							"김초록",
							"01022223333",
							"04524",
							"서울특별시 중구 세종대로 110",
							ORDER_MARKER
					)
			).order();
		}

		if (order.status() == OrderStatus.PAID
				&& order.deliveryStatus() == DeliveryStatus.PREPARING) {
			orderService.cancelOrder(userId, order.id());
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

	private void seedCardPurchase(Long userId) {
		Card card = cardRepository.findAllByStatusOrderByCreatedAtDesc(ActiveStatus.ON_SALE).stream()
				.filter(candidate -> "수박 카드".equals(candidate.getName()))
				.findFirst()
				.orElse(null);
		if (card == null) {
			log.warn("카드 구매 시나리오를 건너뜁니다: 수박 카드가 없습니다.");
			return;
		}
		cardPurchaseService.purchase(
				userId,
				"seed-point-card-purchase-v1",
				new CardPurchaseRequest(card.getId(), 7)
		);
	}

	private void seedJournalReward(Long userId) {
		LocalDate yesterday = LocalDate.now(KST).minusDays(1);
		PlantJournal journal = plantJournalRepository.search(
				userId,
				null,
				null,
				yesterday,
				PageRequest.of(
						0,
						1,
						Sort.by(Sort.Order.desc("writtenDate"), Sort.Order.desc("id"))
				)
		).stream()
				.findFirst()
				.orElse(null);
		if (journal == null) {
			log.warn("성장일지 보상 시나리오를 건너뜁니다: 과거 식물일지가 없습니다.");
			return;
		}
		boolean rewardAlreadyExists = pointTransactionRepository.existsByTypeAndRefTypeAndRefId(
				PointTxType.JOURNAL_REWARD,
				PointRefType.JOURNAL_COMPLETION,
				journal.getId()
		);
		if (!rewardAlreadyExists) {
			walletService.rewardJournal(userId, journal.getId());
		}

		int updated = pointTransactionRepository.backdateLocalSeedJournalReward(
				journal.getId(),
				journal.getWrittenDate().atTime(9, 0)
		);
		if (updated != 1) {
			log.warn("성장일지 보상 시각을 과거 일자로 조정하지 못했습니다: journalId={}", journal.getId());
		}
	}

	private void seedSafely(String scenario, Runnable seed) {
		try {
			seed.run();
			log.info("로컬 포인트 시나리오 준비 완료: {}", scenario);
		} catch (RuntimeException exception) {
			log.warn("로컬 포인트 시나리오 준비 실패: {}", scenario, exception);
		}
	}
}
