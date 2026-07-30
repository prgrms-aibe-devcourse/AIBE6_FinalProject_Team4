package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.dto.request.CardPurchaseRequest;
import com.kiwobollae.api.commerce.dto.response.CardPurchaseResponse;
import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.CardPurchaseLog;
import com.kiwobollae.api.commerce.entity.UserCard;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.repository.CardPurchaseLogRepository;
import com.kiwobollae.api.commerce.repository.CardRepository;
import com.kiwobollae.api.commerce.repository.UserCardRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.point.dto.response.PointDeductionResult;
import com.kiwobollae.api.point.service.WalletService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CardPurchaseService {

	private static final String API_TYPE = "CARD_PURCHASE";

	private final CardRepository cardRepository;
	private final UserCardRepository userCardRepository;
	private final CardPurchaseLogRepository cardPurchaseLogRepository;
	private final UserRepository userRepository;
	private final WalletService walletService;
	private final IdempotencyService idempotencyService;
	private final ObjectMapper objectMapper;

	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public CardPurchaseResponse purchase(
			Long userId,
			String idempotencyKey,
			CardPurchaseRequest request
	) {
		validate(userId, idempotencyKey);
		IdempotencyExecution execution = idempotencyService.start(
				userId,
				API_TYPE,
				idempotencyKey,
				hash(request)
		);
		if (execution.replay()) {
			return deserialize(execution.key().getResponseSnapshot());
		}

		Card card = cardRepository.findByIdAndStatus(request.cardId(), ActiveStatus.ON_SALE)
				.orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
		long totalPoint;
		try {
			totalPoint = Math.multiplyExact(card.getPointPrice(), request.quantity().longValue());
		} catch (ArithmeticException exception) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}

		CardPurchaseLog purchaseLog = cardPurchaseLogRepository.saveAndFlush(
				CardPurchaseLog.builder()
						.user(userRepository.getReferenceById(userId))
						.card(card)
						.cardName(card.getName())
						.unitPoint(card.getPointPrice())
						.quantity(request.quantity())
						.usedPoint(totalPoint)
						.usedFreePoint(0L)
						.usedPaidPoint(0L)
						.status("COMPLETED")
						.createdAt(LocalDateTime.now(ZoneOffset.UTC))
						.build()
		);

		PointDeductionResult pointUsage = walletService.deductForCardPurchase(
				userId,
				totalPoint,
				purchaseLog.getId()
		);
		purchaseLog.applyPointUsage(pointUsage);

		userCardRepository.incrementCount(userId, card.getId(), request.quantity());
		int ownedCount = userCardRepository.findByUser_IdAndCard_Id(userId, card.getId())
				.map(UserCard::getCount)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR));

		CardPurchaseResponse response = CardPurchaseResponse.from(
				purchaseLog,
				pointUsage,
				ownedCount
		);
		String responseSnapshot = serialize(response);
		idempotencyService.succeed(
				execution.key(),
				200,
				responseSnapshot,
				"CARD_PURCHASE",
				purchaseLog.getId()
		);
		return response;
	}

	private void validate(Long userId, String idempotencyKey) {
		if (userId == null) {
			throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
		}
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
	}

	private String hash(CardPurchaseRequest request) {
		try {
			String value = request.cardId() + ":" + request.quantity();
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(value.getBytes(StandardCharsets.UTF_8))
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
	}

	private String serialize(CardPurchaseResponse response) {
		try {
			return objectMapper.writeValueAsString(response);
		} catch (Exception exception) {
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
	}

	private CardPurchaseResponse deserialize(String snapshot) {
		try {
			return objectMapper.readValue(snapshot, CardPurchaseResponse.class);
		} catch (Exception exception) {
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
	}
}
