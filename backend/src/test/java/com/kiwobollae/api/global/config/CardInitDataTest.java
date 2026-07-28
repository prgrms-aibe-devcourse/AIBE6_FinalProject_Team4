package com.kiwobollae.api.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.ExchangeProduct;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.repository.CardRepository;
import com.kiwobollae.api.commerce.repository.ExchangeProductRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardInitDataTest {

	@Mock
	private CardRepository cardRepository;

	@Mock
	private ExchangeProductRepository exchangeProductRepository;

	@InjectMocks
	private CardInitData cardInitData;

	@Test
	void seedsTenCardsAndExchangeProductsWhenBothTablesAreEmpty() {
		given(cardRepository.count()).willReturn(0L);
		given(exchangeProductRepository.count()).willReturn(0L);
		given(exchangeProductRepository.saveAll(org.mockito.ArgumentMatchers.<List<ExchangeProduct>>any()))
				.willAnswer(invocation -> invocation.getArgument(0));

		cardInitData.run(null);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ExchangeProduct>> exchangeProductsCaptor = ArgumentCaptor.forClass(List.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Card>> cardsCaptor = ArgumentCaptor.forClass(List.class);
		verify(exchangeProductRepository).saveAll(exchangeProductsCaptor.capture());
		verify(cardRepository).saveAll(cardsCaptor.capture());

		List<ExchangeProduct> exchangeProducts = exchangeProductsCaptor.getValue();
		List<Card> cards = cardsCaptor.getValue();
		assertThat(exchangeProducts).hasSize(10);
		assertThat(cards).hasSize(10);
		assertThat(exchangeProducts).allMatch(product -> product.getStatus() == ActiveStatus.ON_SALE);
		assertThat(exchangeProducts).allMatch(product -> product.getStock() >= 0);
		assertThat(cards).allMatch(card -> card.getStatus() == ActiveStatus.ON_SALE);
		assertThat(cards).allMatch(card -> card.getPointPrice() > 0);
		assertThat(cards).allMatch(card -> card.getRequiredCountForExchange() > 0);
		assertThat(cards).allMatch(card -> card.getExchangeProduct() != null);
		assertThat(cards).extracting(Card::getExchangeProduct).containsExactlyElementsOf(exchangeProducts);
	}

	@Test
	void skipsSeedingWhenAnyCardAlreadyExists() {
		given(cardRepository.count()).willReturn(1L);

		cardInitData.run(null);

		verify(exchangeProductRepository, never()).count();
		verify(exchangeProductRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
		verify(cardRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
	}

	@Test
	void skipsSeedingWhenAnyExchangeProductAlreadyExists() {
		given(cardRepository.count()).willReturn(0L);
		given(exchangeProductRepository.count()).willReturn(1L);

		cardInitData.run(null);

		verify(exchangeProductRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
		verify(cardRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
	}
}
