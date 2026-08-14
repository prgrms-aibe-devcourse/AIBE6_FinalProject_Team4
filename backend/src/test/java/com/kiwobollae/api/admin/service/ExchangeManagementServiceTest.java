package com.kiwobollae.api.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.dto.response.ExchangeOrderResponse;
import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.ExchangeOrder;
import com.kiwobollae.api.commerce.entity.ExchangeProduct;
import com.kiwobollae.api.commerce.entity.enums.CancelledBy;
import com.kiwobollae.api.commerce.entity.enums.ExchangeStatus;
import com.kiwobollae.api.commerce.repository.ExchangeOrderRepository;
import com.kiwobollae.api.commerce.service.ExchangeRefundService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
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
class ExchangeManagementServiceTest {

	@Mock private ExchangeOrderRepository exchangeOrderRepository;
	@Mock private ExchangeRefundService exchangeRefundService;
	@InjectMocks private ExchangeManagementService exchangeManagementService;

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

	@Test
	void getExchangesForAdminMapsRepositoryPage() {
		Pageable pageable = PageRequest.of(0, 10);
		ExchangeOrder order = mockOrder(1L, ExchangeStatus.REQUESTED, 7L, 1L, 10L, 3);
		given(exchangeOrderRepository.search(ExchangeStatus.REQUESTED, pageable))
				.willReturn(new PageImpl<>(List.of(order)));

		Page<ExchangeOrderResponse> result =
				exchangeManagementService.getExchangesForAdmin(ExchangeStatus.REQUESTED, pageable);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).id()).isEqualTo(1L);
	}

	@Test
	void prepareExchangeTransitionsAndReturnsRefreshedResponse() {
		given(exchangeOrderRepository.updateStatusIfMatches(50L, ExchangeStatus.PREPARING, ExchangeStatus.REQUESTED))
				.willReturn(1);
		ExchangeOrder refreshed = mockOrder(50L, ExchangeStatus.PREPARING, 7L, 1L, 10L, 3);
		given(exchangeOrderRepository.findById(50L)).willReturn(Optional.of(refreshed));

		ExchangeOrderResponse response = exchangeManagementService.prepareExchange(50L);

		assertThat(response.status()).isEqualTo(ExchangeStatus.PREPARING);
	}

	@Test
	void prepareExchangeFailsWithNotFoundWhenOrderDoesNotExist() {
		given(exchangeOrderRepository.updateStatusIfMatches(50L, ExchangeStatus.PREPARING, ExchangeStatus.REQUESTED))
				.willReturn(0);
		given(exchangeOrderRepository.existsById(50L)).willReturn(false);

		assertThatThrownBy(() -> exchangeManagementService.prepareExchange(50L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_NOT_FOUND));
	}

	@Test
	void prepareExchangeFailsWithInvalidStateWhenStatusMismatched() {
		given(exchangeOrderRepository.updateStatusIfMatches(50L, ExchangeStatus.PREPARING, ExchangeStatus.REQUESTED))
				.willReturn(0);
		given(exchangeOrderRepository.existsById(50L)).willReturn(true);

		assertThatThrownBy(() -> exchangeManagementService.prepareExchange(50L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_INVALID_STATE));
	}

	@Test
	void shipExchangeTransitionsPreparingToShipping() {
		given(exchangeOrderRepository.updateStatusIfMatches(50L, ExchangeStatus.SHIPPING, ExchangeStatus.PREPARING))
				.willReturn(1);
		ExchangeOrder refreshed = mockOrder(50L, ExchangeStatus.SHIPPING, 7L, 1L, 10L, 3);
		given(exchangeOrderRepository.findById(50L)).willReturn(Optional.of(refreshed));

		ExchangeOrderResponse response = exchangeManagementService.shipExchange(50L);

		assertThat(response.status()).isEqualTo(ExchangeStatus.SHIPPING);
	}

	@Test
	void deliverExchangeTransitionsShippingToDelivered() {
		given(exchangeOrderRepository.deliverIfMatches(eq(50L), any(LocalDateTime.class), eq(ExchangeStatus.SHIPPING)))
				.willReturn(1);
		ExchangeOrder refreshed = mockOrder(50L, ExchangeStatus.DELIVERED, 7L, 1L, 10L, 3);
		given(exchangeOrderRepository.findById(50L)).willReturn(Optional.of(refreshed));

		ExchangeOrderResponse response = exchangeManagementService.deliverExchange(50L);

		assertThat(response.status()).isEqualTo(ExchangeStatus.DELIVERED);
	}

	@Test
	void deliverExchangeFailsWhenNotShipping() {
		given(exchangeOrderRepository.deliverIfMatches(eq(50L), any(LocalDateTime.class), eq(ExchangeStatus.SHIPPING)))
				.willReturn(0);
		given(exchangeOrderRepository.existsById(50L)).willReturn(true);

		assertThatThrownBy(() -> exchangeManagementService.deliverExchange(50L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_INVALID_STATE));
	}

	@Test
	void adminCancelExchangeDelegatesToRefundServiceOnSuccess() {
		ExchangeOrder cancelled = mockOrder(50L, ExchangeStatus.CANCELLED, 7L, 1L, 10L, 3);
		given(exchangeRefundService.cancelAndRefund(50L, CancelledBy.ADMIN, "품절")).willReturn(cancelled);

		ExchangeOrderResponse response = exchangeManagementService.adminCancelExchange(50L, "품절");

		assertThat(response.status()).isEqualTo(ExchangeStatus.CANCELLED);
	}

	@Test
	void adminCancelExchangePropagatesNotFoundFromRefundService() {
		given(exchangeRefundService.cancelAndRefund(50L, CancelledBy.ADMIN, "품절"))
				.willThrow(new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND));

		assertThatThrownBy(() -> exchangeManagementService.adminCancelExchange(50L, "품절"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_NOT_FOUND));
	}
}
