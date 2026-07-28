package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.commerce.dto.response.CardResponse;
import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.repository.CardRepository;
import com.kiwobollae.api.commerce.repository.UserCardRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardService {

	private final CardRepository cardRepository;
	private final UserCardRepository userCardRepository;

	public List<CardResponse> getCards(Long userId) {
		List<Card> cards = cardRepository.findAllByStatusOrderByCreatedAtDesc(ActiveStatus.ON_SALE);
		Map<Long, Integer> ownedCounts = getOwnedCounts(userId, cards);

		return cards.stream()
				.map(card -> CardResponse.from(
						card,
						userId == null ? null : ownedCounts.getOrDefault(card.getId(), 0)
				))
				.toList();
	}

	public CardResponse getCard(Long cardId, Long userId) {
		Card card = cardRepository.findByIdAndStatus(cardId, ActiveStatus.ON_SALE)
				.orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

		Integer ownedCount = userId == null
				? null
				: userCardRepository.findByUser_IdAndCard_Id(userId, cardId)
						.map(userCard -> userCard.getCount())
						.orElse(0);

		return CardResponse.from(card, ownedCount);
	}

	public List<CardResponse> getMyCards(Long userId) {
		if (userId == null) {
			throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
		}
		return userCardRepository.findAllByUser_IdAndCountGreaterThanOrderByIdDesc(userId, 0).stream()
				.map(userCard -> CardResponse.from(userCard.getCard(), userCard.getCount()))
				.toList();
	}

	private Map<Long, Integer> getOwnedCounts(Long userId, List<Card> cards) {
		if (userId == null || cards.isEmpty()) {
			return Map.of();
		}

		List<Long> cardIds = cards.stream()
				.map(Card::getId)
				.toList();

		return userCardRepository.findAllByUser_IdAndCard_IdIn(userId, cardIds)
				.stream()
				.collect(Collectors.toMap(
						userCard -> userCard.getCard().getId(),
						userCard -> userCard.getCount(),
						Integer::max
				));
	}
}
