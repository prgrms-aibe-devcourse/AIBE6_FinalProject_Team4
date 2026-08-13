package com.kiwobollae.api.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.dto.request.ExchangeOrderRequest;
import com.kiwobollae.api.commerce.dto.response.ExchangeOrderResponse;
import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.ExchangeOrder;
import com.kiwobollae.api.commerce.entity.ExchangeProduct;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.entity.enums.CancelledBy;
import com.kiwobollae.api.commerce.entity.enums.ExchangeStatus;
import com.kiwobollae.api.commerce.repository.CardRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceTest {

	@Mock private ExchangeProductRepository exchangeProductRepository;
	@Mock private ExchangeOrderRepository exchangeOrderRepository;
	@Mock private CardRepository cardRepository;
	@Mock private UserCardRepository userCardRepository;
	@Mock private UserRepository userRepository;
	@Mock private ExchangeRefundService exchangeRefundService;
	@InjectMocks private ExchangeService exchangeService;

	private static final ExchangeOrderRequest REQUEST =
			new ExchangeOrderRequest(1L, "홍길동", "010-1234-5678", "06236", "서울시 강남구", "101동");

	private ExchangeOrder mockOrder(Long id, ExchangeStatus status, Long userId, Long cardId,
			Long productId, Integer usedCardCount) {
		ExchangeOrder order = mock(ExchangeOrder.class);
		User user = mock(User.class);
		Card card = mock(Card.class);
		ExchangeProduct product = mock(ExchangeProduct.class);
		lenient().when(user.getId()).thenReturn(userId);
		lenient().when(card.getId()).thenReturn(cardId);
		lenient().when(product.getId()).thenReturn(productId);
		lenient().when(order.getId()).thenReturn(id);
		lenient().when(order.getUser()).thenReturn(user);
		lenient().when(order.getCard()).thenReturn(card);
		lenient().when(order.getExchangeProduct()).thenReturn(product);
		lenient().when(order.getUsedCardCount()).thenReturn(usedCardCount);
		lenient().when(order.getCardName()).thenReturn("수박 카드");
		lenient().when(order.getExchangeProductName()).thenReturn("텀블러");
		lenient().when(order.getStatus()).thenReturn(status);
		lenient().when(order.getReceiverName()).thenReturn("홍길동");
		lenient().when(order.getReceiverPhone()).thenReturn("010-1234-5678");
		lenient().when(order.getAddress()).thenReturn("서울시 강남구");
		lenient().when(order.getAddressDetail()).thenReturn("101동");
		lenient().when(order.getRequestedAt()).thenReturn(LocalDateTime.of(2026, 7, 28, 10, 0));
		return order;
	}

	// ---- requestExchange ----

	@Test
	void requestExchangeCreatesOrderAndDecrementsCardAndStock() {
		Card card = mock(Card.class);
		ExchangeProduct product = mock(ExchangeProduct.class);
		given(card.getId()).willReturn(1L);
		given(card.getRequiredCountForExchange()).willReturn(3);
		given(card.getExchangeProduct()).willReturn(product);
		given(product.getId()).willReturn(10L);
		given(cardRepository.findById(1L)).willReturn(Optional.of(card));
		given(userCardRepository.decrementCountIfEnough(7L, 1L, 3)).willReturn(1);
		given(exchangeProductRepository.findByIdAndStatus(10L, ActiveStatus.ON_SALE))
				.willReturn(Optional.of(product));
		given(exchangeProductRepository.decrementStockIfAvailable(10L)).willReturn(1);

		User userRef = mock(User.class);
		given(userRepository.getReferenceById(7L)).willReturn(userRef);

		ExchangeOrder saved = mockOrder(100L, ExchangeStatus.REQUESTED, 7L, 1L, 10L, 3);
		given(exchangeOrderRepository.saveAndFlush(any(ExchangeOrder.class))).willReturn(saved);

		ExchangeOrderResponse response = exchangeService.requestExchange(7L, REQUEST);

		assertThat(response.id()).isEqualTo(100L);
		assertThat(response.usedCardCount()).isEqualTo(3);
		assertThat(response.status()).isEqualTo(ExchangeStatus.REQUESTED);
		verify(userCardRepository).decrementCountIfEnough(7L, 1L, 3);
		verify(exchangeProductRepository).decrementStockIfAvailable(10L);
	}

	@Test
	void requestExchangeRejectsWhenUserIdIsNull() {
		assertThatThrownBy(() -> exchangeService.requestExchange(null, REQUEST))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
		verify(cardRepository, never()).findById(anyLong());
	}

	@Test
	void requestExchangeFailsWhenCardNotFound() {
		given(cardRepository.findById(1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> exchangeService.requestExchange(7L, REQUEST))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CARD_NOT_FOUND));
		verify(userCardRepository, never()).decrementCountIfEnough(anyLong(), anyLong(), any());
	}

	@Test
	void requestExchangeFailsWhenCardCountInsufficient() {
		Card card = mock(Card.class);
		given(card.getId()).willReturn(1L);
		given(card.getRequiredCountForExchange()).willReturn(3);
		given(cardRepository.findById(1L)).willReturn(Optional.of(card));
		given(userCardRepository.decrementCountIfEnough(7L, 1L, 3)).willReturn(0);

		assertThatThrownBy(() -> exchangeService.requestExchange(7L, REQUEST))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CARD_NOT_OWNED));
		verify(exchangeProductRepository, never()).findByIdAndStatus(anyLong(), any());
	}

	@Test
	void requestExchangeFailsWhenExchangeProductNotOnSale() {
		Card card = mock(Card.class);
		ExchangeProduct product = mock(ExchangeProduct.class);
		given(card.getId()).willReturn(1L);
		given(card.getRequiredCountForExchange()).willReturn(3);
		given(card.getExchangeProduct()).willReturn(product);
		given(product.getId()).willReturn(10L);
		given(cardRepository.findById(1L)).willReturn(Optional.of(card));
		given(userCardRepository.decrementCountIfEnough(7L, 1L, 3)).willReturn(1);
		given(exchangeProductRepository.findByIdAndStatus(10L, ActiveStatus.ON_SALE))
				.willReturn(Optional.empty());

		assertThatThrownBy(() -> exchangeService.requestExchange(7L, REQUEST))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_PRODUCT_NOT_FOUND));
		verify(exchangeProductRepository, never()).decrementStockIfAvailable(anyLong());
		verify(exchangeOrderRepository, never()).saveAndFlush(any());
	}

	@Test
	void requestExchangeFailsWhenStockUnavailable() {
		Card card = mock(Card.class);
		ExchangeProduct product = mock(ExchangeProduct.class);
		given(card.getId()).willReturn(1L);
		given(card.getRequiredCountForExchange()).willReturn(3);
		given(card.getExchangeProduct()).willReturn(product);
		given(product.getId()).willReturn(10L);
		given(cardRepository.findById(1L)).willReturn(Optional.of(card));
		given(userCardRepository.decrementCountIfEnough(7L, 1L, 3)).willReturn(1);
		given(exchangeProductRepository.findByIdAndStatus(10L, ActiveStatus.ON_SALE))
				.willReturn(Optional.of(product));
		given(exchangeProductRepository.decrementStockIfAvailable(10L)).willReturn(0);

		assertThatThrownBy(() -> exchangeService.requestExchange(7L, REQUEST))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_PRODUCT_OUT_OF_STOCK));
		verify(exchangeOrderRepository, never()).saveAndFlush(any());
	}

	// ---- getMyExchanges / getMyExchange ----

	@Test
	void getMyExchangesMapsRepositoryPage() {
		Pageable pageable = PageRequest.of(0, 10);
		ExchangeOrder order = mockOrder(1L, ExchangeStatus.REQUESTED, 7L, 1L, 10L, 3);
		given(exchangeOrderRepository.findAllByUserId(7L, pageable))
				.willReturn(new PageImpl<>(java.util.List.of(order)));

		Page<ExchangeOrderResponse> result = exchangeService.getMyExchanges(7L, pageable);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).id()).isEqualTo(1L);
	}

	@Test
	void getMyExchangeReturnsOwnedOrder() {
		ExchangeOrder order = mockOrder(1L, ExchangeStatus.REQUESTED, 7L, 1L, 10L, 3);
		given(exchangeOrderRepository.findByIdAndUserId(1L, 7L)).willReturn(Optional.of(order));

		ExchangeOrderResponse response = exchangeService.getMyExchange(7L, 1L);

		assertThat(response.id()).isEqualTo(1L);
	}

	@Test
	void getMyExchangeFailsWhenNotOwned() {
		given(exchangeOrderRepository.findByIdAndUserId(1L, 7L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> exchangeService.getMyExchange(7L, 1L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_NOT_FOUND));
	}

	// ---- cancelExchange ----

	@Test
	void cancelExchangeDelegatesToRefundServiceOnSuccess() {
		given(exchangeOrderRepository.existsByIdAndUserId(50L, 7L)).willReturn(true);

		exchangeService.cancelExchange(7L, 50L, "단순 변심");

		verify(exchangeRefundService).cancelAndRefund(50L, CancelledBy.USER, "단순 변심");
	}

	@Test
	void cancelExchangeFailsWhenNotOwned() {
		given(exchangeOrderRepository.existsByIdAndUserId(50L, 7L)).willReturn(false);

		assertThatThrownBy(() -> exchangeService.cancelExchange(7L, 50L, "단순 변심"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_NOT_FOUND));
		verify(exchangeRefundService, never()).cancelAndRefund(any(), any(), any());
	}

	@Test
	void cancelExchangePropagatesInvalidStateFromRefundService() {
		given(exchangeOrderRepository.existsByIdAndUserId(50L, 7L)).willReturn(true);
		given(exchangeRefundService.cancelAndRefund(50L, CancelledBy.USER, "단순 변심"))
				.willThrow(new BusinessException(ErrorCode.EXCHANGE_INVALID_STATE));

		assertThatThrownBy(() -> exchangeService.cancelExchange(7L, 50L, "단순 변심"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_INVALID_STATE));
	}

}
