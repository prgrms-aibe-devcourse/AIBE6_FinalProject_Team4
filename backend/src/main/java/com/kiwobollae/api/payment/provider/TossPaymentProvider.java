package com.kiwobollae.api.payment.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TossPaymentProvider implements PaymentProvider {

	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
	private static final String APPROVED_STATUS = "DONE";
	private static final String CANCELED_STATUS = "CANCELED";
	private static final String CANCEL_DONE_STATUS = "DONE";

	private final RestClient restClient;

	@Autowired
	public TossPaymentProvider(
			@Value("${payment.toss.base-url:https://api.tosspayments.com}") String baseUrl,
			@Value("${payment.toss.secret-key:}") String secretKey
	) {
		this(RestClient.builder(), baseUrl, secretKey);
	}

	TossPaymentProvider(
			RestClient.Builder restClientBuilder,
			String baseUrl,
			String secretKey
	) {
		validateTestConfiguration(baseUrl, secretKey);
		String basicCredential = Base64.getEncoder().encodeToString(
				(secretKey + ":").getBytes(StandardCharsets.UTF_8)
		);
		this.restClient = restClientBuilder
				.baseUrl(baseUrl)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicCredential)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	@Override
	public PaymentProviderType getType() {
		return PaymentProviderType.TOSS;
	}

	@Override
	public PaymentConfirmResult confirm(PaymentConfirmCommand command) {
		try {
			TossPaymentResponse response = restClient.post()
					.uri("/v1/payments/confirm")
					.header(
							IDEMPOTENCY_HEADER,
							createIdempotencyKey("confirm", command.providerOrderId())
					)
					.body(new TossConfirmRequest(
							command.paymentKey(),
							command.providerOrderId(),
							command.amount()
					))
					.retrieve()
					.body(TossPaymentResponse.class);

			validateApprovedPayment(response, command);
			return PaymentConfirmResult.success();
		} catch (RestClientResponseException exception) {
			PaymentConfirmResult reconciled = reconcileConfirm(command);
			if (reconciled != null) {
				return reconciled;
			}
			if (isDefinitiveClientFailure(exception)) {
				return PaymentConfirmResult.failure("Toss 결제가 승인되지 않았습니다.");
			}
			throw providerUnavailable();
		} catch (RestClientException exception) {
			PaymentConfirmResult reconciled = reconcileConfirm(command);
			if (reconciled != null) {
				return reconciled;
			}
			throw providerUnavailable();
		}
	}

	@Override
	public PaymentRefundResult refund(PaymentRefundCommand command) {
		try {
			TossPaymentResponse response = restClient.post()
					.uri("/v1/payments/{paymentKey}/cancel", command.paymentKey())
					.header(
							IDEMPOTENCY_HEADER,
							createIdempotencyKey("cancel", command.paymentKey())
					)
					.body(new TossCancelRequest(command.reason()))
					.retrieve()
					.body(TossPaymentResponse.class);

			return toRefundResult(response, command);
		} catch (RestClientResponseException exception) {
			PaymentRefundResult reconciled = reconcileRefund(command);
			if (reconciled != null) {
				return reconciled;
			}
			if (isDefinitiveClientFailure(exception)) {
				return PaymentRefundResult.failure("Toss 결제 환불이 거절되었습니다.");
			}
			throw providerUnavailable();
		} catch (RestClientException exception) {
			PaymentRefundResult reconciled = reconcileRefund(command);
			if (reconciled != null) {
				return reconciled;
			}
			throw providerUnavailable();
		}
	}

	private PaymentConfirmResult reconcileConfirm(PaymentConfirmCommand command) {
		TossPaymentResponse response = findPayment(command.paymentKey());
		if (response == null) {
			return null;
		}
		if (isApprovedPayment(response, command)) {
			return PaymentConfirmResult.success();
		}
		return null;
	}

	private PaymentRefundResult reconcileRefund(PaymentRefundCommand command) {
		TossPaymentResponse response = findPayment(command.paymentKey());
		if (response == null || !isFullRefund(response, command)) {
			return null;
		}
		return PaymentRefundResult.success(findRefundKey(response, command));
	}

	private TossPaymentResponse findPayment(String paymentKey) {
		try {
			return restClient.get()
					.uri("/v1/payments/{paymentKey}", paymentKey)
					.retrieve()
					.body(TossPaymentResponse.class);
		} catch (RestClientException exception) {
			return null;
		}
	}

	private void validateApprovedPayment(
			TossPaymentResponse response,
			PaymentConfirmCommand command
	) {
		if (!isApprovedPayment(response, command)) {
			throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_INVALID_RESPONSE);
		}
	}

	private boolean isApprovedPayment(
			TossPaymentResponse response,
			PaymentConfirmCommand command
	) {
		return response != null
				&& Objects.equals(response.paymentKey(), command.paymentKey())
				&& Objects.equals(response.orderId(), command.providerOrderId())
				&& Objects.equals(response.totalAmount(), command.amount())
				&& APPROVED_STATUS.equals(response.status());
	}

	private PaymentRefundResult toRefundResult(
			TossPaymentResponse response,
			PaymentRefundCommand command
	) {
		if (!isFullRefund(response, command)) {
			throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_INVALID_RESPONSE);
		}
		return PaymentRefundResult.success(findRefundKey(response, command));
	}

	private boolean isFullRefund(
			TossPaymentResponse response,
			PaymentRefundCommand command
	) {
		return response != null
				&& Objects.equals(response.paymentKey(), command.paymentKey())
				&& Objects.equals(response.orderId(), command.providerOrderId())
				&& Objects.equals(response.totalAmount(), command.cashAmount())
				&& Objects.equals(response.balanceAmount(), 0L)
				&& CANCELED_STATUS.equals(response.status())
				&& findRefundKey(response, command) != null;
	}

	private String findRefundKey(
			TossPaymentResponse response,
			PaymentRefundCommand command
	) {
		if (response == null || response.cancels() == null) {
			return null;
		}
		return response.cancels().stream()
				.filter(cancel -> CANCEL_DONE_STATUS.equals(cancel.cancelStatus()))
				.filter(cancel -> Objects.equals(cancel.cancelAmount(), command.cashAmount()))
				.map(TossCancelResponse::transactionKey)
				.filter(transactionKey -> transactionKey != null && !transactionKey.isBlank())
				.findFirst()
				.orElse(null);
	}

	private String createIdempotencyKey(String operation, String resourceKey) {
		return UUID.nameUUIDFromBytes(
				(operation + ":" + resourceKey).getBytes(StandardCharsets.UTF_8)
		).toString();
	}

	private boolean isDefinitiveClientFailure(RestClientResponseException exception) {
		if (!exception.getStatusCode().is4xxClientError()) {
			return false;
		}
		return switch (exception.getStatusCode().value()) {
			case 401, 403, 408, 409, 429 -> false;
			default -> true;
		};
	}

	private void validateTestConfiguration(String baseUrl, String secretKey) {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalStateException("Toss Payments API 주소가 설정되지 않았습니다.");
		}
		if (secretKey == null || !secretKey.startsWith("test_")) {
			throw new IllegalStateException("Toss Payments 테스트 시크릿 키가 필요합니다.");
		}
	}

	private BusinessException providerUnavailable() {
		return new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE);
	}

	private record TossConfirmRequest(String paymentKey, String orderId, Long amount) {
	}

	private record TossCancelRequest(String cancelReason) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TossPaymentResponse(
			String paymentKey,
			String orderId,
			String status,
			Long totalAmount,
			Long balanceAmount,
			List<TossCancelResponse> cancels
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TossCancelResponse(
			Long cancelAmount,
			String transactionKey,
			String cancelStatus
	) {
	}
}
