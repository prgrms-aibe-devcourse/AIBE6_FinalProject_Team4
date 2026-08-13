package com.kiwobollae.api.admin.service;

import com.kiwobollae.api.commerce.dto.response.ExchangeOrderResponse;
import com.kiwobollae.api.commerce.entity.ExchangeOrder;
import com.kiwobollae.api.commerce.entity.enums.CancelledBy;
import com.kiwobollae.api.commerce.entity.enums.ExchangeStatus;
import com.kiwobollae.api.commerce.repository.ExchangeOrderRepository;
import com.kiwobollae.api.commerce.service.ExchangeRefundService;
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
	private final ExchangeRefundService exchangeRefundService;

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
		ExchangeOrder exchangeOrder = exchangeRefundService.cancelAndRefund(id, CancelledBy.ADMIN, reason);
		return ExchangeOrderResponse.from(exchangeOrder);
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
}
