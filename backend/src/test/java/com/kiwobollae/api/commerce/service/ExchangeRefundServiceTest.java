package com.kiwobollae.api.commerce.service;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.ExchangeOrder;
import com.kiwobollae.api.commerce.entity.ExchangeProduct;
import com.kiwobollae.api.commerce.repository.ExchangeProductRepository;
import com.kiwobollae.api.commerce.repository.UserCardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeRefundServiceTest {

	@Mock private UserCardRepository userCardRepository;
	@Mock private ExchangeProductRepository exchangeProductRepository;
	@InjectMocks private ExchangeRefundService exchangeRefundService;

	@Test
	void refundRestoresCardCountAndProductStock() {
		ExchangeOrder order = mock(ExchangeOrder.class);
		User user = mock(User.class);
		Card card = mock(Card.class);
		ExchangeProduct product = mock(ExchangeProduct.class);
		lenient().when(user.getId()).thenReturn(7L);
		lenient().when(card.getId()).thenReturn(1L);
		lenient().when(product.getId()).thenReturn(10L);
		lenient().when(order.getUser()).thenReturn(user);
		lenient().when(order.getCard()).thenReturn(card);
		lenient().when(order.getExchangeProduct()).thenReturn(product);
		lenient().when(order.getUsedCardCount()).thenReturn(3);

		exchangeRefundService.refund(order);

		verify(userCardRepository).incrementCount(7L, 1L, 3);
		verify(exchangeProductRepository).incrementStock(10L);
	}
}
