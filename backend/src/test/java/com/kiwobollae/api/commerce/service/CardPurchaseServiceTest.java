package com.kiwobollae.api.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.dto.request.CardPurchaseRequest;
import com.kiwobollae.api.commerce.dto.response.CardPurchaseResponse;
import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.CardPurchaseLog;
import com.kiwobollae.api.commerce.entity.UserCard;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.repository.CardPurchaseLogRepository;
import com.kiwobollae.api.commerce.repository.CardRepository;
import com.kiwobollae.api.commerce.repository.UserCardRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.point.dto.response.PointDeductionResult;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CardPurchaseServiceTest {

	@Mock private CardRepository cardRepository;
	@Mock private UserCardRepository userCardRepository;
	@Mock private CardPurchaseLogRepository cardPurchaseLogRepository;
	@Mock private UserRepository userRepository;
	@Mock private WalletService walletService;
	@Mock private IdempotencyService idempotencyService;
	@Mock private ObjectMapper objectMapper;
	@InjectMocks private CardPurchaseService cardPurchaseService;

	@Test
	void purchaseDeductsPointsIncrementsOwnedCountAndWritesLog() throws Exception {
		CardPurchaseRequest request = new CardPurchaseRequest(1L, 2);
		Card card = org.mockito.Mockito.mock(Card.class);
		CardPurchaseLog log = org.mockito.Mockito.mock(CardPurchaseLog.class);
		UserCard userCard = org.mockito.Mockito.mock(UserCard.class);
		IdempotencyKey key = org.mockito.Mockito.mock(IdempotencyKey.class);
		PointDeductionResult pointUsage = new PointDeductionResult(500L, 100L, 700L);

		given(idempotencyService.start(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("CARD_PURCHASE"),
				org.mockito.ArgumentMatchers.eq("purchase-key"),
				org.mockito.ArgumentMatchers.anyString()
		)).willReturn(new IdempotencyExecution(key, false));
		given(cardRepository.findByIdAndStatus(1L, ActiveStatus.ON_SALE))
				.willReturn(Optional.of(card));
		given(card.getId()).willReturn(1L);
		given(card.getName()).willReturn("수박 카드");
		given(card.getPointPrice()).willReturn(300L);
		given(cardPurchaseLogRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
				.willReturn(log);
		given(log.getId()).willReturn(11L);
		given(log.getCard()).willReturn(card);
		given(log.getCardName()).willReturn("수박 카드");
		given(log.getUnitPoint()).willReturn(300L);
		given(log.getQuantity()).willReturn(2);
		given(log.getUsedPoint()).willReturn(600L);
		given(log.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 27, 0, 0));
		given(walletService.deductForPurchase(7L, 600L, PointRefType.CARD_PURCHASE, 11L))
				.willReturn(pointUsage);
		given(userCardRepository.findByUser_IdAndCard_Id(7L, 1L))
				.willReturn(Optional.of(userCard));
		given(userCard.getCount()).willReturn(4);
		given(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
				.willReturn("{\"purchaseId\":11}");

		CardPurchaseResponse response = cardPurchaseService.purchase(7L, "purchase-key", request);

		assertThat(response.usedPoint()).isEqualTo(600L);
		assertThat(response.ownedCount()).isEqualTo(4);
		assertThat(response.remainingBalance()).isEqualTo(700L);
		verify(userCardRepository).incrementCount(7L, 1L, 2);
		verify(log).applyPointUsage(pointUsage);
		verify(idempotencyService).succeed(key, 200, "{\"purchaseId\":11}", "CARD_PURCHASE", 11L);
	}

	@Test
	void hiddenCardCannotBePurchased() {
		CardPurchaseRequest request = new CardPurchaseRequest(99L, 1);
		IdempotencyKey key = org.mockito.Mockito.mock(IdempotencyKey.class);
		given(idempotencyService.start(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("CARD_PURCHASE"),
				org.mockito.ArgumentMatchers.eq("purchase-key"),
				org.mockito.ArgumentMatchers.anyString()
		)).willReturn(new IdempotencyExecution(key, false));
		given(cardRepository.findByIdAndStatus(99L, ActiveStatus.ON_SALE))
				.willReturn(Optional.empty());

		assertThatThrownBy(() -> cardPurchaseService.purchase(7L, "purchase-key", request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CARD_NOT_FOUND));

		verify(walletService, never()).deductForPurchase(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.anyLong()
		);
	}

	@Test
	void successfulIdempotentRequestReplaysStoredResponse() throws Exception {
		CardPurchaseRequest request = new CardPurchaseRequest(1L, 2);
		IdempotencyKey key = org.mockito.Mockito.mock(IdempotencyKey.class);
		CardPurchaseResponse stored = new CardPurchaseResponse(
				11L, 1L, "수박 카드", 300L, 2, 600L,
				500L, 100L, 4, 700L, LocalDateTime.of(2026, 7, 27, 0, 0)
		);
		given(idempotencyService.start(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("CARD_PURCHASE"),
				org.mockito.ArgumentMatchers.eq("purchase-key"),
				org.mockito.ArgumentMatchers.anyString()
		)).willReturn(new IdempotencyExecution(key, true));
		given(key.getResponseSnapshot()).willReturn("{\"purchaseId\":11}");
		given(objectMapper.readValue("{\"purchaseId\":11}", CardPurchaseResponse.class))
				.willReturn(stored);

		CardPurchaseResponse response = cardPurchaseService.purchase(7L, "purchase-key", request);

		assertThat(response).isEqualTo(stored);
		verify(cardRepository, never()).findByIdAndStatus(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any()
		);
	}
}
