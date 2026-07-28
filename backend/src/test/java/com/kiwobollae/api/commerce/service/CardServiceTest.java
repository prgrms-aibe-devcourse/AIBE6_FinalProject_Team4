package com.kiwobollae.api.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.commerce.dto.response.CardResponse;
import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.ExchangeProduct;
import com.kiwobollae.api.commerce.entity.UserCard;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.repository.CardRepository;
import com.kiwobollae.api.commerce.repository.UserCardRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

	@Mock
	private CardRepository cardRepository;

	@Mock
	private UserCardRepository userCardRepository;

	@InjectMocks
	private CardService cardService;

	@Test
	void anonymousCardListDoesNotExposeOwnedCount() {
		Card card = card(1L);
		given(cardRepository.findAllByStatusOrderByCreatedAtDesc(ActiveStatus.ON_SALE))
				.willReturn(List.of(card));

		List<CardResponse> response = cardService.getCards(null);

		assertThat(response).hasSize(1);
		assertThat(response.getFirst().ownedCount()).isNull();
		verify(userCardRepository, never()).findAllByUser_IdAndCard_IdIn(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyCollection()
		);
	}

	@Test
	void authenticatedCardListIncludesOwnedAndZeroCounts() {
		Card ownedCard = card(1L);
		Card unownedCard = card(2L);
		UserCard userCard = org.mockito.Mockito.mock(UserCard.class);
		given(userCard.getCard()).willReturn(ownedCard);
		given(userCard.getCount()).willReturn(3);
		given(cardRepository.findAllByStatusOrderByCreatedAtDesc(ActiveStatus.ON_SALE))
				.willReturn(List.of(ownedCard, unownedCard));
		given(userCardRepository.findAllByUser_IdAndCard_IdIn(7L, List.of(1L, 2L)))
				.willReturn(List.of(userCard));

		List<CardResponse> response = cardService.getCards(7L);

		assertThat(response).extracting(CardResponse::ownedCount).containsExactly(3, 0);
	}

	@Test
	void emptyCardListDoesNotQueryOwnedCards() {
		given(cardRepository.findAllByStatusOrderByCreatedAtDesc(ActiveStatus.ON_SALE))
				.willReturn(List.of());

		List<CardResponse> response = cardService.getCards(7L);

		assertThat(response).isEmpty();
		verify(userCardRepository, never()).findAllByUser_IdAndCard_IdIn(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyCollection()
		);
	}

	@Test
	void anonymousCardDetailDoesNotExposeOwnedCount() {
		Card card = card(1L);
		given(cardRepository.findByIdAndStatus(1L, ActiveStatus.ON_SALE))
				.willReturn(Optional.of(card));

		CardResponse response = cardService.getCard(1L, null);

		assertThat(response.ownedCount()).isNull();
		verify(userCardRepository, never()).findByUser_IdAndCard_Id(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong()
		);
	}

	@Test
	void authenticatedCardDetailUsesZeroWhenCardIsNotOwned() {
		Card card = card(1L);
		given(cardRepository.findByIdAndStatus(1L, ActiveStatus.ON_SALE))
				.willReturn(Optional.of(card));
		given(userCardRepository.findByUser_IdAndCard_Id(7L, 1L))
				.willReturn(Optional.empty());

		CardResponse response = cardService.getCard(1L, 7L);

		assertThat(response.ownedCount()).isZero();
	}

	@Test
	void hiddenOrMissingCardIsReturnedAsNotFound() {
		given(cardRepository.findByIdAndStatus(99L, ActiveStatus.ON_SALE))
				.willReturn(Optional.empty());

		assertThatThrownBy(() -> cardService.getCard(99L, null))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CARD_NOT_FOUND));
	}

	@Test
	void myCardsIncludesOwnedHiddenCards() {
		Card hiddenCard = card(9L);
		given(hiddenCard.getStatus()).willReturn(ActiveStatus.HIDDEN);
		UserCard userCard = org.mockito.Mockito.mock(UserCard.class);
		given(userCard.getCard()).willReturn(hiddenCard);
		given(userCard.getCount()).willReturn(2);
		given(userCardRepository.findAllByUser_IdAndCountGreaterThanOrderByIdDesc(7L, 0))
				.willReturn(List.of(userCard));

		List<CardResponse> response = cardService.getMyCards(7L);

		assertThat(response).hasSize(1);
		assertThat(response.getFirst().ownedCount()).isEqualTo(2);
		assertThat(response.getFirst().status()).isEqualTo(ActiveStatus.HIDDEN);
	}

	@Test
	void myCardsRequiresAuthentication() {
		assertThatThrownBy(() -> cardService.getMyCards(null))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
	}

	private Card card(Long id) {
		ExchangeProduct exchangeProduct = org.mockito.Mockito.mock(ExchangeProduct.class);
		given(exchangeProduct.getId()).willReturn(10L + id);
		given(exchangeProduct.getName()).willReturn("교환 상품 " + id);
		given(exchangeProduct.getStock()).willReturn(10);

		Card card = org.mockito.Mockito.mock(Card.class);
		given(card.getId()).willReturn(id);
		given(card.getName()).willReturn("테스트 카드 " + id);
		given(card.getPointPrice()).willReturn(300L);
		given(card.getExchangeProduct()).willReturn(exchangeProduct);
		given(card.getRequiredCountForExchange()).willReturn(5);
		given(card.getStatus()).willReturn(ActiveStatus.ON_SALE);
		return card;
	}
}
