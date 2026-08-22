package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.payment.dto.request.PaymentRefundRequest;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.provider.PaymentProviderBusyException;
import com.kiwobollae.api.payment.provider.PaymentRefundResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentRefundService {

	private final PaymentProvider paymentProvider;
	private final PaymentRefundTransactionService paymentRefundTransactionService;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public PaymentRefundResponse refund(
			Long userId,
			String idempotencyKey,
			Long paymentId,
			PaymentRefundRequest request
	) {
		validateRequest(userId, paymentId, idempotencyKey);
		String reason = request.reason().trim();
		String requestHash = sha256(normalizedRequest(paymentId, reason));
		PaymentRefundPreparation preparation = paymentRefundTransactionService.prepare(
				userId,
				idempotencyKey,
				requestHash,
				paymentId,
				reason
		);
		if (preparation.replay()) {
			return preparation.replayResponse();
		}

		PaymentRefundResult providerResult;
		try {
			providerResult = paymentProvider.refund(preparation.command());
		} catch (PaymentProviderBusyException exception) {
			paymentRefundTransactionService.failDefinitively(preparation);
			throw exception;
		}
		if (providerResult == null) {
			throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_INVALID_RESPONSE);
		}
		if (!providerResult.successful()) {
			paymentRefundTransactionService.failDefinitively(preparation);
			throw new BusinessException(
					ErrorCode.PAYMENT_DECLINED,
					providerResult.message() == null || providerResult.message().isBlank()
							? "결제 대행사가 환불을 거절했습니다."
							: providerResult.message()
			);
		}
		if (providerResult.refundKey() == null || providerResult.refundKey().isBlank()
				|| providerResult.refundKey().length() > 200) {
			throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_INVALID_RESPONSE);
		}

		return paymentRefundTransactionService.complete(preparation, providerResult);
	}

	private void validateRequest(Long userId, Long paymentId, String idempotencyKey) {
		if (userId == null || userId < 1 || paymentId == null || paymentId < 1
				|| idempotencyKey == null || idempotencyKey.isBlank()
				|| idempotencyKey.length() > 64) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
	}

	private String normalizedRequest(Long paymentId, String reason) {
		return "paymentId=" + paymentId + "&reason=" + reason;
	}

	private String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(value.getBytes(StandardCharsets.UTF_8))
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
	}
}
