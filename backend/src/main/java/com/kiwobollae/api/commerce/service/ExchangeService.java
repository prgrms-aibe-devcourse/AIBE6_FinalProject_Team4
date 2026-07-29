package com.kiwobollae.api.commerce.service;

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
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final ExchangeProductRepository exchangeProductRepository;
	private final ExchangeOrderRepository exchangeOrderRepository;
	private final CardRepository cardRepository;
	private final UserCardRepository userCardRepository;
	private final UserRepository userRepository;

	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public ExchangeOrderResponse requestExchange(Long userId, ExchangeOrderRequest request) {
		if (userId == null) {
			throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
		}

		Card card = cardRepository.findById(request.cardId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

		Integer requiredCount = card.getRequiredCountForExchange();
		int cardDecremented = userCardRepository.decrementCountIfEnough(userId, card.getId(), requiredCount);
		if (cardDecremented == 0) {
			throw new BusinessException(ErrorCode.CARD_NOT_OWNED);
		}

		ExchangeProduct exchangeProduct = exchangeProductRepository
				.findByIdAndStatus(card.getExchangeProduct().getId(), ActiveStatus.ON_SALE)
				.orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_PRODUCT_NOT_FOUND));

		int stockDecremented = exchangeProductRepository.decrementStockIfAvailable(exchangeProduct.getId());
		if (stockDecremented == 0) {
			throw new BusinessException(ErrorCode.EXCHANGE_PRODUCT_OUT_OF_STOCK);
		}

		ExchangeOrder exchangeOrder = exchangeOrderRepository.saveAndFlush(
				ExchangeOrder.create(
						userRepository.getReferenceById(userId),
						card,
						exchangeProduct,
						requiredCount,
						request.receiverName(),
						request.receiverPhone(),
						request.address(),
						request.addressDetail(),
						LocalDateTime.now(KST)
				)
		);

		return ExchangeOrderResponse.from(exchangeOrder);
	}

	@Transactional(readOnly = true)
	public Page<ExchangeOrderResponse> getMyExchanges(Long userId, Pageable pageable) {
		return exchangeOrderRepository.findAllByUserId(userId, pageable).map(ExchangeOrderResponse::from);
	}

	@Transactional(readOnly = true)
	public ExchangeOrderResponse getMyExchange(Long userId, Long id) {
		return exchangeOrderRepository.findByIdAndUserId(id, userId)
				.map(ExchangeOrderResponse::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND));
	}

	@Transactional
	public void cancelExchange(Long userId, Long id, String reason) {
		ExchangeOrder exchangeOrder = exchangeOrderRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND));

		int cancelled = exchangeOrderRepository.cancelIfMatches(
				id,
				CancelledBy.USER,
				reason,
				LocalDateTime.now(KST),
				ExchangeStatus.REQUESTED
		);
		if (cancelled == 0) {
			throw new BusinessException(ErrorCode.EXCHANGE_INVALID_STATE);
		}

		refund(exchangeOrder);
	}

	private void refund(ExchangeOrder exchangeOrder) {
		Long userId = exchangeOrder.getUser().getId();
		Long cardId = exchangeOrder.getCard().getId();
		Long exchangeProductId = exchangeOrder.getExchangeProduct().getId();
		Integer usedCardCount = exchangeOrder.getUsedCardCount();

		userCardRepository.incrementCount(userId, cardId, usedCardCount);
		exchangeProductRepository.incrementStock(exchangeProductId);
	}
}
