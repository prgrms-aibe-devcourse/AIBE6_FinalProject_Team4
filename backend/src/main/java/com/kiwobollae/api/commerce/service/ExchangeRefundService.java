package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.commerce.entity.ExchangeOrder;
import com.kiwobollae.api.commerce.repository.ExchangeProductRepository;
import com.kiwobollae.api.commerce.repository.UserCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 교환 취소(사용자/관리자 공통)에 쓰이는 환불 로직 — 사용된 카드 수량과 교환 상품 재고를 원복한다.
 * 항상 취소 처리 호출부의 트랜잭션 안에서 실행되므로 별도 {@code @Transactional}은 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRefundService {

	private final UserCardRepository userCardRepository;
	private final ExchangeProductRepository exchangeProductRepository;

	public void refund(ExchangeOrder exchangeOrder) {
		Long userId = exchangeOrder.getUser().getId();
		Long cardId = exchangeOrder.getCard().getId();
		Long exchangeProductId = exchangeOrder.getExchangeProduct().getId();
		Integer usedCardCount = exchangeOrder.getUsedCardCount();

		userCardRepository.incrementCount(userId, cardId, usedCardCount);
		exchangeProductRepository.incrementStock(exchangeProductId);
	}
}
