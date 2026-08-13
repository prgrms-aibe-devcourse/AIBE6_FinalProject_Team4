package com.kiwobollae.api.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.ExchangeOrder;
import com.kiwobollae.api.commerce.entity.ExchangeProduct;
import com.kiwobollae.api.commerce.entity.enums.CancelledBy;
import com.kiwobollae.api.commerce.entity.enums.ExchangeStatus;
import com.kiwobollae.api.commerce.repository.ExchangeOrderRepository;
import com.kiwobollae.api.commerce.repository.ExchangeProductRepository;
import com.kiwobollae.api.commerce.repository.UserCardRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeRefundServiceTest {

	@Mock private ExchangeOrderRepository exchangeOrderRepository;
	@Mock private UserCardRepository userCardRepository;
	@Mock private ExchangeProductRepository exchangeProductRepository;
	@InjectMocks private ExchangeRefundService exchangeRefundService;

	private ExchangeOrder mockOrder() {
		ExchangeOrder order = mock(ExchangeOrder.class);
		User user = mock(User.class);
		Card card = mock(Card.class);
		ExchangeProduct product = mock(ExchangeProduct.class);
		when(user.getId()).thenReturn(7L);
		when(card.getId()).thenReturn(1L);
		when(product.getId()).thenReturn(10L);
		when(order.getUser()).thenReturn(user);
		when(order.getCard()).thenReturn(card);
		when(order.getExchangeProduct()).thenReturn(product);
		when(order.getUsedCardCount()).thenReturn(3);
		return order;
	}

	@Test
	void cancelAndRefundRestoresCardCountAndProductStockOnSuccess() {
		ExchangeOrder order = mockOrder();
		given(exchangeOrderRepository.cancelIfMatches(
				eq(50L), eq(CancelledBy.USER), eq("단순 변심"), any(LocalDateTime.class), eq(ExchangeStatus.REQUESTED)
		)).willReturn(1);
		given(exchangeOrderRepository.findById(50L)).willReturn(Optional.of(order));

		ExchangeOrder result = exchangeRefundService.cancelAndRefund(50L, CancelledBy.USER, "단순 변심");

		assertThat(result).isSameAs(order);
		verify(userCardRepository).incrementCount(7L, 1L, 3);
		verify(exchangeProductRepository).incrementStock(10L);
	}

	@Test
	void cancelAndRefundFailsWithInvalidStateWhenAlreadyCancelled() {
		given(exchangeOrderRepository.cancelIfMatches(
				eq(50L), eq(CancelledBy.USER), eq("단순 변심"), any(LocalDateTime.class), eq(ExchangeStatus.REQUESTED)
		)).willReturn(0);
		given(exchangeOrderRepository.existsById(50L)).willReturn(true);

		assertThatThrownBy(() -> exchangeRefundService.cancelAndRefund(50L, CancelledBy.USER, "단순 변심"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_INVALID_STATE));
		verify(userCardRepository, never()).incrementCount(anyLong(), anyLong(), any());
		verify(exchangeProductRepository, never()).incrementStock(anyLong());
	}

	@Test
	void cancelAndRefundFailsWithNotFoundWhenOrderDoesNotExist() {
		given(exchangeOrderRepository.cancelIfMatches(
				eq(50L), eq(CancelledBy.ADMIN), eq("품절"), any(LocalDateTime.class), eq(ExchangeStatus.REQUESTED)
		)).willReturn(0);
		given(exchangeOrderRepository.existsById(50L)).willReturn(false);

		assertThatThrownBy(() -> exchangeRefundService.cancelAndRefund(50L, CancelledBy.ADMIN, "품절"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_NOT_FOUND));
		verify(userCardRepository, never()).incrementCount(anyLong(), anyLong(), any());
	}
}
