package com.kiwobollae.api.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossPaymentProviderTest {

	private static final String BASE_URL = "https://api.tosspayments.test";
	private static final String SECRET_KEY = "test_sk_test_secret";

	private MockRestServiceServer server;
	private TossPaymentProvider provider;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		provider = new TossPaymentProvider(builder, BASE_URL, SECRET_KEY);
	}

	@Test
	void confirmsPaymentWithTossTestApi() {
		String expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
				(SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8)
		);
		String expectedIdempotencyKey = UUID.nameUUIDFromBytes(
				"confirm:order-1".getBytes(StandardCharsets.UTF_8)
		).toString();

		server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", expectedAuthorization))
				.andExpect(header("Idempotency-Key", expectedIdempotencyKey))
				.andExpect(content().json("""
						{"paymentKey":"payment-key-1","orderId":"order-1","amount":5000}
						"""))
				.andRespond(withSuccess("""
						{
						  "paymentKey": "payment-key-1",
						  "orderId": "order-1",
						  "status": "DONE",
						  "totalAmount": 5000,
						  "balanceAmount": 5000
						}
						""", MediaType.APPLICATION_JSON));

		PaymentConfirmResult result = provider.confirm(
				new PaymentConfirmCommand("order-1", "payment-key-1", 5_000L)
		);

		assertThat(provider.getType()).isEqualTo(PaymentProviderType.TOSS);
		assertThat(result.successful()).isTrue();
		server.verify();
	}

	@Test
	void rejectsMismatchedApprovalResponse() {
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
				.andRespond(withSuccess("""
						{
						  "paymentKey": "payment-key-1",
						  "orderId": "order-1",
						  "status": "DONE",
						  "totalAmount": 1000
						}
						""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> provider.confirm(
				new PaymentConfirmCommand("order-1", "payment-key-1", 5_000L)
		))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.PAYMENT_PROVIDER_INVALID_RESPONSE));
		server.verify();
	}

	@Test
	void returnsFailureWhenTossRejectsApproval() {
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
				.andRespond(withResourceNotFound());
		server.expect(requestTo(BASE_URL + "/v1/payments/payment-key-1"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withResourceNotFound());

		PaymentConfirmResult result = provider.confirm(
				new PaymentConfirmCommand("order-1", "payment-key-1", 5_000L)
		);

		assertThat(result.successful()).isFalse();
		server.verify();
	}

	@Test
	void keepsPaymentRecoverableWhileTossIdempotentRequestIsProcessing() {
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
				.andRespond(withStatus(HttpStatus.CONFLICT));
		server.expect(requestTo(BASE_URL + "/v1/payments/payment-key-1"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withResourceNotFound());

		assertThatThrownBy(() -> provider.confirm(
				new PaymentConfirmCommand("order-1", "payment-key-1", 5_000L)
		))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE));
		server.verify();
	}

	@Test
	void fullyRefundsPaymentWithoutSendingPartialCancelAmount() {
		String expectedIdempotencyKey = UUID.nameUUIDFromBytes(
				"cancel:payment-key-1".getBytes(StandardCharsets.UTF_8)
		).toString();

		server.expect(requestTo(BASE_URL + "/v1/payments/payment-key-1/cancel"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Idempotency-Key", expectedIdempotencyKey))
				.andExpect(content().json("""
						{"cancelReason":"사용자 요청"}
						"""))
				.andRespond(withSuccess("""
						{
						  "paymentKey": "payment-key-1",
						  "orderId": "order-1",
						  "status": "CANCELED",
						  "totalAmount": 5000,
						  "balanceAmount": 0,
						  "cancels": [
						    {
						      "cancelAmount": 5000,
						      "transactionKey": "cancel-transaction-key",
						      "cancelStatus": "DONE"
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		PaymentRefundResult result = provider.refund(
				new PaymentRefundCommand(
						"order-1",
						"payment-key-1",
						5_000L,
						"사용자 요청"
				)
		);

		assertThat(result.successful()).isTrue();
		assertThat(result.refundKey()).isEqualTo("cancel-transaction-key");
		server.verify();
	}

	@Test
	void refusesNonTestSecretKey() {
		assertThatThrownBy(() -> new TossPaymentProvider(
				RestClient.builder(),
				BASE_URL,
				"live_sk_secret"
		))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Toss Payments 테스트 시크릿 키가 필요합니다.");
	}

	@Test
	void loadsTossProviderWithoutRestClientBuilderBean() {
		new ApplicationContextRunner()
				.withInitializer(context -> context.getBeanFactory().setConversionService(
						ApplicationConversionService.getSharedInstance()
				))
				.withUserConfiguration(
						TossPaymentProvider.class,
						TossPaymentBulkhead.class,
						TossPaymentCircuitBreaker.class
				)
				.withPropertyValues(
						"payment.toss.base-url=" + BASE_URL,
						"payment.toss.secret-key=" + SECRET_KEY,
						"payment.toss.connect-timeout=2s",
						"payment.toss.read-timeout=5s",
						"payment.toss.reconciliation-read-timeout=1s",
						"payment.toss.bulkhead.max-concurrent-calls=3",
						"payment.toss.bulkhead.acquire-timeout=50ms",
						"payment.toss.circuit-breaker.failure-threshold=3",
						"payment.toss.circuit-breaker.open-duration=10s"
				)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(TossPaymentProvider.class);
				});
	}

	@Test
	void rejectsNonPositiveTimeouts() {
		assertThatThrownBy(() -> new TossPaymentProvider(
				BASE_URL,
				SECRET_KEY,
				Duration.ZERO,
				Duration.ofSeconds(30),
				Duration.ofSeconds(5),
				new TossPaymentBulkhead(10, Duration.ZERO),
				new TossPaymentCircuitBreaker(5, Duration.ofSeconds(30), System::nanoTime)
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Toss Payments 연결 타임아웃은 0보다 커야 합니다.");

		assertThatThrownBy(() -> new TossPaymentProvider(
				BASE_URL,
				SECRET_KEY,
				Duration.ofSeconds(3),
				Duration.ofSeconds(-1),
				Duration.ofSeconds(5),
				new TossPaymentBulkhead(10, Duration.ZERO),
				new TossPaymentCircuitBreaker(5, Duration.ofSeconds(30), System::nanoTime)
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Toss Payments 응답 타임아웃은 0보다 커야 합니다.");
	}
}
