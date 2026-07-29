package com.kiwobollae.api.admin.service;

import com.kiwobollae.api.commerce.dto.response.ExchangeOrderResponse;
import com.kiwobollae.api.commerce.entity.ExchangeOrder;
import com.kiwobollae.api.commerce.entity.enums.CancelledBy;
import com.kiwobollae.api.commerce.entity.enums.ExchangeStatus;
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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeManagementService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final ExchangeOrderRepository exchangeOrderRepository;
	private final ExchangeProductRepository exchangeProductRepository;
	private final UserCardRepository userCardRepository;

	@Transactional(readOnly = true)
	public Page<ExchangeOrderResponse> getExchangesForAdmin(ExchangeStatus status, Pageable pageable) {
		return exchangeOrderRepository.search(status, pageable).map(ExchangeOrderResponse::from);
	}

	@Transactional
	public ExchangeOrderResponse prepareExchange(Long id) {
		return transitionStatus(id, ExchangeStatus.PREPARING, ExchangeStatus.REQUESTED);
	}

	@Transactional
	public ExchangeOrderResponse shipExchange(Long id) {
		return transitionStatus(id, ExchangeStatus.SHIPPING, ExchangeStatus.PREPARING);
	}

	@Transactional
	public ExchangeOrderResponse deliverExchange(Long id) {
		int updated = exchangeOrderRepository.deliverIfMatches(
				id,
				LocalDateTime.now(KST),
				ExchangeStatus.SHIPPING
		);
		if (updated == 0) {
			throwNotFoundOrInvalidState(id);
		}
		return ExchangeOrderResponse.from(findExchangeForAdmin(id));
	}

	@Transactional
	public ExchangeOrderResponse adminCancelExchange(Long id, String reason) {
		int cancelled = exchangeOrderRepository.cancelIfMatches(
				id,
				CancelledBy.ADMIN,
				reason,
				LocalDateTime.now(KST),
				ExchangeStatus.REQUESTED
		);
		if (cancelled == 0) {
			throwNotFoundOrInvalidState(id);
		}

		ExchangeOrder exchangeOrder = findExchangeForAdmin(id);
		ExchangeOrderResponse response = ExchangeOrderResponse.from(exchangeOrder);
		refund(exchangeOrder);
		return response;
	}

	private ExchangeOrderResponse transitionStatus(Long id, ExchangeStatus newStatus, ExchangeStatus expectedStatus) {
		int updated = exchangeOrderRepository.updateStatusIfMatches(id, newStatus, expectedStatus);
		if (updated == 0) {
			throwNotFoundOrInvalidState(id);
		}
		return ExchangeOrderResponse.from(findExchangeForAdmin(id));
	}

	private ExchangeOrder findExchangeForAdmin(Long id) {
		return exchangeOrderRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND));
	}

	private void throwNotFoundOrInvalidState(Long id) {
		if (!exchangeOrderRepository.existsById(id)) {
			throw new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND);
		}
		throw new BusinessException(ErrorCode.EXCHANGE_INVALID_STATE);
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
