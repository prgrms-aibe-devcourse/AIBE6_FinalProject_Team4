package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.UserCard;

public record UserCardResponse(
		Long id,
		Long userId,
		Long cardId,
		String cardName,
		Integer count
) {
	public static UserCardResponse from(UserCard userCard) {
		return new UserCardResponse(
				userCard.getId(),
				userCard.getUser().getId(),
				userCard.getCard().getId(),
				userCard.getCard().getName(),
				userCard.getCount()
		);
	}
}
