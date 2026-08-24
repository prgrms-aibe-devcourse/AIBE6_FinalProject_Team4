package com.kiwobollae.api.commerce.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교환 취소(사용자/관리자 공통) — 상태를 원자적으로 CANCELLED로 전이시키고, 사용된 카드 수량과
 * 교환 상품 재고를 복구한다. 상태 전이와 환불을 한 메서드가 통째로 책임지기 때문에, 호출부가
 * "취소 반영 이후에 다시 조회해서 넘겨야 한다" 같은 순서를 신경 쓸 필요가 없고, 같은 주문에
 * 대해 두 번 불러도 두 번째 호출은 {@code cancelIfMatches}가 0건을 반환해 자연히 막힌다.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRefundService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final ExchangeOrderRepository exchangeOrderRepository;
	private final UserCardRepository userCardRepository;
	private final ExchangeProductRepository exchangeProductRepository;

	@Transactional
	public ExchangeOrder cancelAndRefund(Long id, CancelledBy cancelledBy, String reason) {
		// 일반 주문 취소가 배송 준비중(PREPARING)까지는 허용하고 배송중(SHIPPING)부터 막는 것과
		// 동일한 경계를 쓴다.
		int cancelled = exchangeOrderRepository.cancelIfMatches(
				id,
				cancelledBy,
				reason,
				LocalDateTime.now(KST),
				ExchangeStatus.PREPARING
		);
		if (cancelled == 0) {
			if (!exchangeOrderRepository.existsById(id)) {
				throw new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND);
			}
			throw new BusinessException(ErrorCode.EXCHANGE_INVALID_STATE);
		}

		ExchangeOrder exchangeOrder = exchangeOrderRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND));

		Long userId = exchangeOrder.getUser().getId();
		Long cardId = exchangeOrder.getCard().getId();
		Long exchangeProductId = exchangeOrder.getExchangeProduct().getId();
		Integer usedCardCount = exchangeOrder.getUsedCardCount();

		userCardRepository.incrementCount(userId, cardId, usedCardCount);
		exchangeProductRepository.incrementStock(exchangeProductId);

		return exchangeOrder;
	}
}
